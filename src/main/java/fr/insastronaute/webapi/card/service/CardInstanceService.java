package fr.insastronaute.webapi.card.service;

import fr.insastronaute.webapi.card.service.CollectionRewardService;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTrigger;
import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import fr.insastronaute.webapi.card.domain.Card;
import fr.insastronaute.webapi.card.domain.CardCollection;
import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.card.domain.CardInstance;
import fr.insastronaute.webapi.card.domain.CardInstanceReward;
import fr.insastronaute.webapi.card.dto.AdminCardInstanceDto;
import fr.insastronaute.webapi.card.dto.CardInstanceCreateRequest;
import fr.insastronaute.webapi.card.dto.CardInstanceDto;
import fr.insastronaute.webapi.card.dto.CardInstanceUpdateRequest;
import fr.insastronaute.webapi.card.dto.OwnedCardInstanceDto;
import fr.insastronaute.webapi.card.dto.ReleasedCardInstanceDto;
import fr.insastronaute.webapi.card.print.domain.CardPrintRunSlot;
import fr.insastronaute.webapi.card.print.repository.CardPrintRunSlotRepository;
import fr.insastronaute.webapi.card.repository.CardCollectionRepository;
import fr.insastronaute.webapi.card.repository.CardInstanceRepository;
import fr.insastronaute.webapi.card.repository.CardInstanceRewardRepository;
import fr.insastronaute.webapi.card.goofy.GoofyCardService;
import fr.insastronaute.webapi.card.repository.CardRepository;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.common.adminaudit.service.AdminActionJournal;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CardInstanceService {

    /**
     * Une instance physique reellement deplacee par un geste d'administration.
     *
     * <p>Le nom de carte voyage avec l'identifiant : le registre est en ajout
     * seul, et une ligne qui ne cite qu'un identifiant devient muette des que
     * l'instance est repointee vers une autre definition de carte.</p>
     */
    public record MovedInstance(UUID instanceId, String cardName) {
    }

    private static final String PUBLIC_ID_ALPHABET = "ABCDEFGHIJKLMNPQRSTUVWXYZ123456789";
    private static final int PUBLIC_ID_RAW_LENGTH = 15;
    private static final int MAX_GENERATION_ATTEMPTS = 20;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CardInstanceRepository instanceRepository;
    private final CardRepository cardRepository;
    private final CardCollectionRepository collectionRepository;
    private final UserRepository userRepository;
    private final CardPrintRunSlotRepository printRunSlotRepository;
    private final AutoChallengeTriggerPublisher autoChallengeTriggerPublisher;
    private final CollectionRewardService collectionRewardService;
    private final AdminActionJournal journal;
    private final GoofyCardService goofyCardService;
    private final CardInstanceRewardRepository rewardRepository;

    public CardInstanceService(
            CardInstanceRepository instanceRepository,
            CardRepository cardRepository,
            CardCollectionRepository collectionRepository,
            UserRepository userRepository,
            CardPrintRunSlotRepository printRunSlotRepository,
            AutoChallengeTriggerPublisher autoChallengeTriggerPublisher,
            CollectionRewardService collectionRewardService,
            AdminActionJournal journal,
            GoofyCardService goofyCardService,
            CardInstanceRewardRepository rewardRepository
    ) {
        this.instanceRepository = instanceRepository;
        this.cardRepository = cardRepository;
        this.collectionRepository = collectionRepository;
        this.userRepository = userRepository;
        this.printRunSlotRepository = printRunSlotRepository;
        this.autoChallengeTriggerPublisher = autoChallengeTriggerPublisher;
        this.collectionRewardService = collectionRewardService;
        this.journal = journal;
        this.goofyCardService = goofyCardService;
        this.rewardRepository = rewardRepository;
    }

    @Transactional(readOnly = true)
    public Page<AdminCardInstanceDto> searchAdmin(
            String search,
            UUID cardId,
            String publicId,
            UUID ownerUserId,
            String type,
            Integer number,
            String assignmentStatus,
            Pageable pageable
    ) {
        String normalizedPublicId = publicId == null || publicId.isBlank() ? null : normalizePublicId(publicId);
        String normalizedType = type == null || type.isBlank() ? null : type.trim();
        Page<CardInstance> page = instanceRepository.searchAdmin(
                search,
                cardId,
                normalizedPublicId,
                ownerUserId,
                normalizedType,
                number,
                assignmentStatus,
                pageable
        );
        List<AdminCardInstanceDto> dtos = toAdminDtos(page.getContent());
        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public List<AdminCardInstanceDto> searchAdmin(UUID cardId, String publicId, UUID ownerUserId, String type, Integer number) {
        String normalizedPublicId = publicId == null || publicId.isBlank() ? null : normalizePublicId(publicId);
        String normalizedType = type == null || type.isBlank() ? null : type.trim();
        List<CardInstance> instances = instanceRepository.searchAdmin(
                cardId,
                normalizedPublicId,
                ownerUserId,
                normalizedType,
                number
        );
        return toAdminDtos(instances);
    }

    @Transactional(readOnly = true)
    public AdminCardInstanceDto getAdmin(UUID instanceId) {
        CardInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new NotFoundException("Instance de carte introuvable."));
        CardPrintRunSlot printSlot = printRunSlotRepository.findByCardInstanceId(instance.getId()).orElse(null);
        return toAdminDto(instance, printSlot);
    }

    @Transactional(readOnly = true)
    public List<CardInstanceDto> findByCard(UUID cardId) {
        if (!cardRepository.existsById(cardId)) {
            throw new NotFoundException("Carte introuvable.");
        }
        return instanceRepository.findByCardIdOrderByCreatedAtDesc(cardId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public CardInstanceDto create(UUID cardId, String actorKeycloakSub) {
        Card card = findInstantiableCard(cardId);
        CardInstance saved = instanceRepository.save(newInstance(card));
        recordCreation(actorKeycloakSub, List.of(saved));
        return toDto(saved);
    }

    @Transactional
    public List<CardInstanceDto> createBatch(UUID cardId, int quantity, String actorKeycloakSub) {
        if (quantity < 1 || quantity > 1000) {
            throw new BadRequestException("La quantité doit être comprise entre 1 et 1000.");
        }

        Card card = findInstantiableCard(cardId);
        List<CardInstance> instances = new ArrayList<>(quantity);
        for (int index = 0; index < quantity; index++) {
            instances.add(newInstance(card));
        }

        List<CardInstance> saved = instanceRepository.saveAll(instances);
        recordCreation(actorKeycloakSub, saved);
        return saved.stream().map(this::toDto).toList();
    }

    /**
     * Une ligne au registre par lot fabrique, jamais par exemplaire.
     *
     * <p>Mille lignes pour un clic ne se relisent pas. Celle-ci porte de quoi retrouver
     * ce qui a ete produit : la carte quand le lot n'en contient qu'une -- une serie
     * d'impression en melange plusieurs --, la quantite, et les codes publics du premier
     * et du dernier, qui situent la plage sans la recopier entiere.</p>
     *
     * <p>Sans cible : ces exemplaires n'appartiennent a personne au moment ou ils
     * naissent. La trace de leur entree dans une collection viendra plus tard.</p>
     */
    private void recordCreation(String actorKeycloakSub, List<CardInstance> created) {
        if (actorKeycloakSub == null || created.isEmpty()) {
            return;
        }

        List<String> distinctCardNames = created.stream()
                .map(CardInstance::getCard)
                .filter(java.util.Objects::nonNull)
                .map(Card::getName)
                .distinct()
                .toList();

        Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("quantity", created.size());
        if (distinctCardNames.size() == 1) {
            details.put("cardName", distinctCardNames.get(0));
        } else {
            // Une serie d'impression melange les cartes : les nommer toutes ferait une
            // ligne illisible, n'en nommer qu'une mentirait.
            details.put("cardCount", distinctCardNames.size());
        }
        details.put("firstPublicId", created.get(0).getPublicId());
        details.put("lastPublicId", created.get(created.size() - 1).getPublicId());

        journal.recordGlobal(actorKeycloakSub, AdminActionType.PHYSICAL_CARDS_CREATED, details);
    }

    @Transactional
    public AdminCardInstanceDto createAdmin(CardInstanceCreateRequest request, String actorKeycloakSub) {
        if (request == null) {
            throw new BadRequestException("La demande de création est requise.");
        }

        Card card = findInstantiableCard(request.cardId());
        CardInstance instance = new CardInstance();
        instance.setCard(card);
        if (request.publicId() != null) {
            String normalizedPublicId = normalizePublicId(request.publicId());
            if (goofyCardService.matchesCode(normalizedPublicId)) {
                throw new ConflictException("Cet identifiant est réservé pour la carte goofy.");
            }
            if (instanceRepository.existsByPublicId(normalizedPublicId)) {
                throw new ConflictException("Cet identifiant public de carte est déjà utilisé.");
            }
            instance.setPublicId(normalizedPublicId);
        } else {
            instance.setPublicId(generateUniquePublicId());
        }

        if (request.ownerUserId() != null) {
            AppUser owner = userRepository.findById(request.ownerUserId())
                    .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
            instance.setOwner(owner);
            instance.setAssignedAt(fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow());
        }

        CardInstance saved = instanceRepository.save(instance);

        if (request.ownerUserId() != null) {
            // Apres l'enregistrement : la recompense se note par exemplaire, et un
            // exemplaire neuf n'a pas d'identifiant avant d'etre enregistre.
            incrementPhysicalCollection(saved.getOwner(), card, saved);
            journal.record(actorKeycloakSub, request.ownerUserId(),
                    AdminActionType.PHYSICAL_CARD_ASSIGNED,
                    Map.of("publicId", saved.getPublicId(), "cardName", card.getName()));
        }

        return toAdminDto(saved, null);
    }

    @Transactional
    public List<CardInstance> createUnassignedInstances(List<Card> cards, String actorKeycloakSub) {
        if (cards == null || cards.isEmpty() || cards.size() > 1000) {
            throw new BadRequestException("La quantité doit être comprise entre 1 et 1000.");
        }

        List<CardInstance> instances = new ArrayList<>(cards.size());
        for (Card card : cards) {
            if (card == null || card.isDefaultCard()) {
                throw new BadRequestException("La carte par défaut ne peut pas être instanciée.");
            }
            instances.add(newInstance(card));
        }
        List<CardInstance> saved = instanceRepository.saveAll(instances);
        recordCreation(actorKeycloakSub, saved);
        return saved;
    }

    @Transactional
    public CardInstanceDto assign(UUID instanceId, UUID userId, String actorKeycloakSub) {
        CardInstance instance = findInstanceForUpdate(instanceId);
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));

        AppUser currentOwner = instance.getOwner();
        if (currentOwner != null && user.getId().equals(currentOwner.getId())) {
            return toDto(instance);
        }

        if (currentOwner != null) {
            decrementPhysicalCollection(currentOwner, instance.getCard());
        }
        instance.setOwner(user);
        instance.setAssignedAt(fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow());
        incrementPhysicalCollection(user, instance.getCard(), instance);
        CardInstance saved = instanceRepository.save(instance);

        // Un transfert se lit des deux cotes : celui qui perd la carte autant
        // que celui qui la recoit.
        String cardName = instance.getCard().getName();
        if (currentOwner != null) {
            journal.record(actorKeycloakSub, currentOwner.getId(),
                    AdminActionType.PHYSICAL_CARD_RELEASED,
                    Map.of("publicId", instance.getPublicId(), "cardName", cardName));
        }
        journal.record(actorKeycloakSub, userId,
                AdminActionType.PHYSICAL_CARD_ASSIGNED,
                Map.of("publicId", instance.getPublicId(), "cardName", cardName));

        return toDto(saved);
    }

    /**
     * Attribue les instances libres de la selection et renvoie celles qui ont
     * reellement change de main.
     *
     * <p>Une instance deja detenue par ce meme joueur est passee sans erreur :
     * elle n'apparait donc pas dans le retour, pour que l'appelant admin ne
     * journalise pas un geste qui n'a pas eu lieu.</p>
     */
    @Transactional
    public List<MovedInstance> assignAvailableToUser(UUID userId, List<UUID> instanceIds) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
        List<CardInstance> instances = findInstancesForUpdate(instanceIds);
        for (CardInstance instance : instances) {
            if (instance.getOwner() != null && !user.getId().equals(instance.getOwner().getId())) {
                throw new ConflictException("Une carte physique sélectionnée est déjà attribuée.");
            }
        }
        List<MovedInstance> assigned = new ArrayList<>();
        for (CardInstance instance : instances) {
            if (instance.getOwner() == null) {
                instance.setOwner(user);
                instance.setAssignedAt(fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow());
                incrementPhysicalCollection(user, instance.getCard(), instance);
                instanceRepository.save(instance);
                assigned.add(new MovedInstance(instance.getId(), instance.getCard().getName()));
            }
        }
        return List.copyOf(assigned);
    }

    /**
     * Retire les instances de la selection et renvoie celles qui ont ete
     * liberees.
     *
     * <p>Contrairement a assignAvailableToUser, rien n'est passe en silence :
     * une instance qui n'appartient pas au joueur fait echouer tout le geste.
     * Le retour vaut donc toujours la selection entiere ; il porte le nom de
     * carte dont l'appelant admin a besoin pour journaliser.</p>
     */
    @Transactional
    public List<MovedInstance> releaseOwnedByUser(UUID userId, List<UUID> instanceIds) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
        List<CardInstance> instances = findInstancesForUpdate(instanceIds);
        for (CardInstance instance : instances) {
            if (instance.getOwner() == null || !user.getId().equals(instance.getOwner().getId())) {
                throw new NotFoundException("Une carte physique sélectionnée n'appartient pas à cet utilisateur.");
            }
        }
        List<MovedInstance> released = new ArrayList<>();
        for (CardInstance instance : instances) {
            decrementPhysicalCollection(user, instance.getCard());
            instance.setOwner(null);
            instance.setAssignedAt(null);
            instanceRepository.save(instance);
            released.add(new MovedInstance(instance.getId(), instance.getCard().getName()));
        }
        return List.copyOf(released);
    }

    @Transactional
    public AdminCardInstanceDto updateAdmin(UUID instanceId, CardInstanceUpdateRequest request, String actorKeycloakSub) {
        if (request == null) {
            throw new BadRequestException("La demande de modification est requise.");
        }

        CardInstance instance = findInstanceForUpdate(instanceId);
        Card previousCard = instance.getCard();
        AppUser previousOwner = instance.getOwner();
        Card nextCard = previousCard;
        AppUser nextOwner = previousOwner;

        if (request.cardId() != null) {
            nextCard = findInstantiableCard(request.cardId());
            instance.setCard(nextCard);
        }
        // Un renommage double d'un transfert changerait le code sous les pieds
        // de l'ancien proprietaire : sa ligne doit nommer le code qu'il a tenu.
        String previousPublicId = instance.getPublicId();
        if (request.publicId() != null) {
            String normalizedPublicId = normalizePublicId(request.publicId());
            if (goofyCardService.matchesCode(normalizedPublicId)) {
                throw new ConflictException("Cet identifiant est réservé pour la carte goofy.");
            }
            if (!normalizedPublicId.equals(instance.getPublicId())
                    && instanceRepository.existsByPublicIdAndIdNot(normalizedPublicId, instance.getId())) {
                throw new ConflictException("Cet identifiant public de carte est déjà utilisé.");
            }
            instance.setPublicId(normalizedPublicId);
        }

        if (Boolean.TRUE.equals(request.clearOwner())) {
            nextOwner = null;
        } else if (request.ownerUserId() != null) {
            nextOwner = userRepository.findById(request.ownerUserId())
                    .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
        }

        boolean ownerChanged = !sameUser(previousOwner, nextOwner);
        boolean cardChanged = !java.util.Objects.equals(previousCard.getId(), nextCard.getId());
        if (ownerChanged || cardChanged) {
            if (previousOwner != null) {
                decrementPhysicalCollection(previousOwner, previousCard);
            }
            if (nextOwner != null) {
                incrementPhysicalCollection(nextOwner, nextCard, instance);
            }
            instance.setOwner(nextOwner);
            instance.setAssignedAt(nextOwner == null ? null : fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow());
        }

        CardInstance saved = instanceRepository.save(instance);

        if (ownerChanged && previousOwner != null) {
            journal.record(actorKeycloakSub, previousOwner.getId(),
                    AdminActionType.PHYSICAL_CARD_RELEASED,
                    Map.of("publicId", previousPublicId, "cardName", previousCard.getName()));
        }
        if (ownerChanged && nextOwner != null) {
            journal.record(actorKeycloakSub, nextOwner.getId(),
                    AdminActionType.PHYSICAL_CARD_ASSIGNED,
                    Map.of("publicId", saved.getPublicId(), "cardName", nextCard.getName()));
        }
        // Repointer l'instance sans la changer de main n'est ni une attribution
        // ni un retrait, mais le proprietaire perd bien une carte et en gagne
        // une autre - decrementPhysicalCollection puis increment juste au-dessus
        // le font. swapDetails porte les valeurs nominales des deux cartes, pas
        // le mouvement reel du score du proprietaire. Une instance sans
        // proprietaire ne touche personne : elle n'ecrit rien.
        if (cardChanged && !ownerChanged && nextOwner != null) {
            journal.record(actorKeycloakSub, nextOwner.getId(),
                    AdminActionType.PHYSICAL_CARD_SWAPPED,
                    swapDetails(saved.getPublicId(), previousCard, nextCard));
        }

        CardPrintRunSlot printSlot = printRunSlotRepository.findByCardInstanceId(saved.getId()).orElse(null);
        return toAdminDto(saved, printSlot);
    }

    @Transactional
    public CardInstanceDto release(UUID instanceId, String actorKeycloakSub) {
        CardInstance instance = findInstanceForUpdate(instanceId);
        if (instance.getOwner() == null) {
            return toDto(instance);
        }

        AppUser previousOwner = instance.getOwner();
        decrementPhysicalCollection(previousOwner, instance.getCard());
        instance.setOwner(null);
        instance.setAssignedAt(null);
        CardInstance saved = instanceRepository.save(instance);

        journal.record(actorKeycloakSub, previousOwner.getId(),
                AdminActionType.PHYSICAL_CARD_RELEASED,
                Map.of("publicId", saved.getPublicId(), "cardName", saved.getCard().getName()));

        return toDto(saved);
    }

    /**
     * Supprime une instance physique depuis l'ecran d'administration.
     *
     * <p>Detruire une instance detenue retire la carte a son proprietaire :
     * c'est le meme geste qu'un retrait, et il s'ecrit au registre avant que
     * la ligne ne disparaisse.</p>
     */
    @Transactional
    public void delete(UUID instanceId, String actorKeycloakSub) {
        CardInstance instance = findInstanceForUpdate(instanceId);
        if (instance.getOwner() != null) {
            decrementPhysicalCollection(instance.getOwner(), instance.getCard());
            journal.record(actorKeycloakSub, instance.getOwner().getId(),
                    AdminActionType.PHYSICAL_CARD_RELEASED,
                    Map.of("publicId", instance.getPublicId(), "cardName", instance.getCard().getName()));
        }
        instanceRepository.delete(instance);
    }

    /** Les exemplaires papier d'une carte que le joueur detient, pour qu'il choisisse lequel liberer. */
    @Transactional(readOnly = true)
    public List<OwnedCardInstanceDto> findMine(String keycloakSub, UUID cardId) {
        AppUser user = userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
        return instanceRepository.findByOwnerIdAndCardIdOrderByAssignedAtAsc(user.getId(), cardId).stream()
                .map(instance -> new OwnedCardInstanceDto(instance.getPublicId(), instance.getAssignedAt()))
                .toList();
    }

    /**
     * Le joueur rend a la circulation un exemplaire qu'il a en double.
     *
     * <p>L'exemplaire n'appartient plus a personne : le prochain qui scanne son code le
     * recupere, son ancien proprietaire compris tant que personne ne l'a fait. Jamais le
     * dernier : ce geste sert a donner un doublon, pas a vider sa collection.</p>
     *
     * <p>Un code inconnu, libre ou detenu par un autre recoit la meme reponse -- les
     * distinguer laisserait sonder les codes imprimes des autres. L'exemplaire est
     * verrouille avant le joueur, dans l'ordre ou le scan les prend : deux liberations
     * simultanees du meme joueur passent l'une apres l'autre, et la seconde voit la
     * premiere avant de compter ce qui lui reste.</p>
     */
    @Transactional
    public ReleasedCardInstanceDto releaseMine(String keycloakSub, String publicId) {
        Optional<CardInstance> found = publicId == null || publicId.isBlank()
                ? Optional.empty()
                : instanceRepository.findByPublicIdForUpdate(normalizePublicId(publicId));
        AppUser user = userRepository.findWithLockByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
        CardInstance instance = found
                .filter(candidate -> candidate.getOwner() != null && user.getId().equals(candidate.getOwner().getId()))
                .orElseThrow(() -> new NotFoundException("Cet exemplaire n'est pas dans ta collection."));
        Card card = instance.getCard();
        if (instanceRepository.countByOwnerIdAndCardId(user.getId(), card.getId()) < 2) {
            throw new BadRequestException("Tu ne peux libérer qu'un doublon : garde au moins un exemplaire de cette carte.");
        }

        int remainingQuantity = collectionRepository
                .findByUserIdAndCardIdAndCollectionType(user.getId(), card.getId(), CardCollectionType.PHYSICAL)
                .map(collection -> Math.max(0, collection.getQuantity() - 1))
                .orElse(0);
        decrementPhysicalCollection(user, card);
        instance.setOwner(null);
        instance.setAssignedAt(null);
        instanceRepository.save(instance);
        return new ReleasedCardInstanceDto(card.getId(), card.getName(), instance.getPublicId(), remainingQuantity);
    }

    @Transactional
    public CardInstanceDto claim(String keycloakSub, String publicId) {
        AppUser user = userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));

        // Le code goofy passe par le même champ qu'une carte imprimée : c'est un
        // code qu'on saisit, pas une carte qu'on scanne, et lui faire une entrée à
        // part obligerait à expliquer laquelle utiliser avant même de savoir qu'il
        // existe. On le reconnaît donc ici, avant la recherche d'instance — sinon
        // il finirait en « code invalide », faute de correspondre à une impression.
        Optional<Card> goofyCard = goofyCardService.cardFor(publicId);
        if (goofyCard.isPresent()) {
            return claimGoofyCard(user, goofyCard.get());
        }

        CardInstance instance = instanceRepository.findByPublicIdForUpdate(normalizePublicId(publicId))
                .orElseThrow(() -> new NotFoundException("Instance de carte introuvable."));

        AppUser currentOwner = instance.getOwner();
        if (currentOwner != null) {
            if (user.getId().equals(currentOwner.getId())) {
                return toDto(instance);
            }
            throw new ConflictException("Cette carte physique est déjà attribuée.");
        }

        instance.setOwner(user);
        instance.setAssignedAt(fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow());
        incrementPhysicalCollection(user, instance.getCard(), instance);
        return toDto(instanceRepository.save(instance));
    }

    /**
     * Donne la carte goofy, une fois par personne.
     *
     * <p>Elle prend la forme d'une instance physique comme une autre : elle compte
     * dans la collection, se prête au troc et se voit dans le classement. Une carte
     * qui n'existerait que dans une animation ne serait pas une carte.</p>
     */
    private CardInstanceDto claimGoofyCard(AppUser user, Card card) {
        goofyCardService.markRedeemed(user.getId());
        CardInstance instance = newInstance(card);
        instance.setOwner(user);
        instance.setAssignedAt(fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow());
        CardInstance saved = instanceRepository.save(instance);
        incrementPhysicalCollection(user, card, saved);

        CardInstanceDto dto = toDto(saved);
        return new CardInstanceDto(
                dto.id(), dto.cardId(), dto.cardName(), dto.publicId(),
                dto.ownerUserId(), dto.ownerDisplayName(), dto.ownerEmail(),
                dto.assignedAt(), dto.createdAt(), dto.updatedAt(),
                true
        );
    }

    private Card findInstantiableCard(UUID cardId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new NotFoundException("Carte introuvable."));
        if (card.isDefaultCard()) {
            throw new BadRequestException("La carte par défaut ne peut pas être instanciée.");
        }
        return card;
    }

    private CardInstance findInstanceForUpdate(UUID instanceId) {
        return instanceRepository.findByIdForUpdate(instanceId)
                .orElseThrow(() -> new NotFoundException("Instance de carte introuvable."));
    }

    private List<CardInstance> findInstancesForUpdate(List<UUID> instanceIds) {
        if (instanceIds == null || instanceIds.isEmpty() || instanceIds.size() > 100) {
            throw new BadRequestException("Sélectionnez entre 1 et 100 cartes physiques.");
        }
        if (instanceIds.stream().anyMatch(java.util.Objects::isNull)
                || instanceIds.stream().distinct().count() != instanceIds.size()) {
            throw new BadRequestException("La sélection de cartes physiques est invalide.");
        }
        return instanceIds.stream().map(this::findInstanceForUpdate).toList();
    }

    private CardInstance newInstance(Card card) {
        CardInstance instance = new CardInstance();
        instance.setCard(card);
        instance.setPublicId(generateUniquePublicId());
        return instance;
    }

    private String generateUniquePublicId() {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String publicId = randomPublicId();
            if (!instanceRepository.existsByPublicId(publicId) && !goofyCardService.matchesCode(publicId)) {
                return publicId;
            }
        }
        throw new ConflictException("Impossible de générer un identifiant public unique.");
    }

    private String randomPublicId() {
        StringBuilder raw = new StringBuilder(PUBLIC_ID_RAW_LENGTH);
        for (int index = 0; index < PUBLIC_ID_RAW_LENGTH; index++) {
            raw.append(PUBLIC_ID_ALPHABET.charAt(RANDOM.nextInt(PUBLIC_ID_ALPHABET.length())));
        }
        return formatRawPublicId(raw.toString());
    }

    private String normalizePublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            throw new BadRequestException("L'identifiant public de carte est requis.");
        }

        StringBuilder raw = new StringBuilder(PUBLIC_ID_RAW_LENGTH);
        for (int index = 0; index < publicId.length(); index++) {
            char character = Character.toUpperCase(publicId.charAt(index));
            if (character == '-' || Character.isWhitespace(character)) {
                continue;
            }
            raw.append(character);
        }

        if (raw.length() != PUBLIC_ID_RAW_LENGTH) {
            throw new BadRequestException("L'identifiant public de carte doit contenir 15 caractères.");
        }
        for (int index = 0; index < raw.length(); index++) {
            if (PUBLIC_ID_ALPHABET.indexOf(raw.charAt(index)) < 0) {
                throw new BadRequestException("L'identifiant public de carte contient un caractère invalide.");
            }
        }
        return formatRawPublicId(raw.toString());
    }

    private String formatRawPublicId(String raw) {
        return raw.substring(0, 5) + "-" + raw.substring(5, 10) + "-" + raw.substring(10, 15);
    }

    private void incrementPhysicalCollection(AppUser user, Card card, CardInstance instance) {
        CardCollection collection = collectionRepository
                .findByUserIdAndCardIdAndCollectionType(user.getId(), card.getId(), CardCollectionType.PHYSICAL)
                .orElse(null);
        int quantityBefore = collection == null ? 0 : collection.getQuantity();
        if (collection == null) {
            collection = new CardCollection();
            collection.setUser(user);
            collection.setCard(card);
            collection.setCollectionType(CardCollectionType.PHYSICAL);
            collection.setQuantity(1);
        } else {
            collection.setQuantity(collection.getQuantity() + 1);
        }
        collectionRepository.save(collection);
        if (firstRewardFor(instance, user, card)) {
            collectionRewardService.credit(user.getId(), card, CardCollectionType.PHYSICAL, quantityBefore, collection.getQuantity());
        }
        autoChallengeTriggerPublisher.publish(user.getId(), AutoChallengeTrigger.CARD_OBTAINED);
    }

    /**
     * Note, et dit, si cet exemplaire paie ce joueur pour la premiere fois.
     *
     * <p>Un doublon libere peut revenir a son ancien proprietaire, ou faire la navette
     * entre deux joueurs : chaque passage compte la carte, un seul la paie. Pas de
     * course a craindre entre deux verifications : tous les chemins qui donnent un
     * proprietaire a un exemplaire le tiennent deja verrouille, ou viennent de le
     * creer.</p>
     */
    private boolean firstRewardFor(CardInstance instance, AppUser user, Card card) {
        if (rewardRepository.existsByCardInstanceIdAndUserIdAndCardId(instance.getId(), user.getId(), card.getId())) {
            return false;
        }
        rewardRepository.save(new CardInstanceReward(instance.getId(), user.getId(), card.getId()));
        return true;
    }

    /**
     * Ce que valent, cote carte, les deux definitions d'une instance repointee.
     *
     * <p>Les points sont ceux de la premiere acquisition tels que la carte les
     * porte, sa categorie servant de valeur par defaut, et 0 quand la categorie
     * est une categorie de jeu - c'est ce que
     * {@link Card#getEffectiveFirstAcquisitionPoints()} rend dans les deux cas.
     * Le multiplicateur global des cartes physiques n'entre pas ici : c'est un
     * reglage de classement, pas une propriete du geste, et une ligne du
     * registre doit rester lisible telle qu'elle a ete ecrite.</p>
     *
     * <p>{@code fromCardPoints}, {@code toCardPoints} et {@code cardPointsDelta}
     * sont les valeurs nominales de premiere acquisition des deux definitions
     * de carte - pas le mouvement reel du score de collection du proprietaire,
     * qui depend aussi du multiplicateur et des exemplaires deja detenus de
     * chaque carte ; le signe de {@code cardPointsDelta} peut donc differer de
     * celui du mouvement reel.</p>
     *
     * <p>Le type peut manquer - toutes les cartes n'en ont pas - d'ou une carte
     * qui accepte des valeurs nulles plutot qu'un Map.of.</p>
     *
     * <p>{@code publicId} nomme le code que l'instance porte apres le geste,
     * contrairement a la ligne {@code PHYSICAL_CARD_RELEASED} d'un transfert qui
     * nomme deliberement celui que l'ancien proprietaire tenait : ici le
     * proprietaire garde l'objet, donc le code post-geste est celui qu'il
     * tient - sauf renommage simultane, ou ce code n'est alors pas celui qu'il
     * tenait avant ce geste.</p>
     */
    private Map<String, Object> swapDetails(String publicId, Card previousCard, Card nextCard) {
        int fromCardPoints = previousCard.getEffectiveFirstAcquisitionPoints();
        int toCardPoints = nextCard.getEffectiveFirstAcquisitionPoints();

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("publicId", publicId);
        details.put("fromCardId", previousCard.getId().toString());
        details.put("toCardId", nextCard.getId().toString());
        details.put("fromCardName", previousCard.getName());
        details.put("toCardName", nextCard.getName());
        details.put("fromType", previousCard.getType());
        details.put("toType", nextCard.getType());
        details.put("fromCardPoints", fromCardPoints);
        details.put("toCardPoints", toCardPoints);
        details.put("cardPointsDelta", toCardPoints - fromCardPoints);
        return details;
    }

    private void decrementPhysicalCollection(AppUser user, Card card) {
        CardCollection collection = collectionRepository
                .findByUserIdAndCardIdAndCollectionType(user.getId(), card.getId(), CardCollectionType.PHYSICAL)
                .orElse(null);
        if (collection == null) {
            return;
        }
        if (collection.getQuantity() <= 1) {
            collectionRepository.delete(collection);
            return;
        }
        collection.setQuantity(collection.getQuantity() - 1);
        collectionRepository.save(collection);
    }

    private CardInstanceDto toDto(CardInstance instance) {
        AppUser owner = instance.getOwner();
        return new CardInstanceDto(
                instance.getId(),
                instance.getCard().getId(),
                instance.getCard().getName(),
                instance.getPublicId(),
                owner == null ? null : owner.getId(),
                owner == null ? null : owner.getDisplayName(),
                owner == null ? null : owner.getEmail(),
                instance.getAssignedAt(),
                instance.getCreatedAt(),
                instance.getUpdatedAt(),
                false
        );
    }

    private List<AdminCardInstanceDto> toAdminDtos(List<CardInstance> instances) {
        if (instances.isEmpty()) {
            return List.of();
        }
        Map<UUID, CardPrintRunSlot> printSlotsByInstanceId = printRunSlotRepository
                .findByCardInstanceIdIn(instances.stream().map(CardInstance::getId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(slot -> slot.getCardInstance().getId(), Function.identity()));
        return instances.stream()
                .sorted(Comparator.comparing(CardInstance::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(instance -> toAdminDto(instance, printSlotsByInstanceId.get(instance.getId())))
                .toList();
    }

    private AdminCardInstanceDto toAdminDto(CardInstance instance, CardPrintRunSlot printSlot) {
        AppUser owner = instance.getOwner();
        UUID printRunId = printSlot == null ? null : printSlot.getPrintRun().getId();
        Integer sheetIndex = printSlot == null ? null : printSlot.getPageIndex();
        return new AdminCardInstanceDto(
                instance.getId(),
                instance.getCard().getId(),
                instance.getCard().getName(),
                instance.getCard().getCategory(),
                instance.getCard().getDisplayOrder(),
                instance.getPublicId(),
                owner == null ? null : owner.getId(),
                owner == null ? null : owner.getDisplayName(),
                owner == null ? null : owner.getEmail(),
                printRunId,
                sheetIndex,
                printRunId == null ? null : printPageId(printRunId, sheetIndex * 2 + 1),
                printRunId == null ? null : printPageId(printRunId, sheetIndex * 2 + 2),
                instance.getAssignedAt(),
                instance.getCreatedAt(),
                instance.getUpdatedAt()
        );
    }

    private boolean sameUser(AppUser first, AppUser second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.getId().equals(second.getId());
    }

    private String printPageId(UUID printRunId, int pageNumber) {
        return printRunId + "-" + pageNumber;
    }
}
