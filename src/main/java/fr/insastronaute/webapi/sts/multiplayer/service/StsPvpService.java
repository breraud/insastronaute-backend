package fr.insastronaute.webapi.sts.multiplayer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.card.dto.CardDeckCardDto;
import fr.insastronaute.webapi.card.dto.CardDeckCategoryDto;
import fr.insastronaute.webapi.card.dto.CardDeckDto;
import fr.insastronaute.webapi.card.service.CardService;
import fr.insastronaute.webapi.card.service.CollectionTestModeService;
import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.common.json.JsonPayloads;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.friend.domain.FriendshipStatus;
import fr.insastronaute.webapi.friend.repository.FriendshipRepository;
import fr.insastronaute.webapi.sts.combat.api.dto.CombatSnapshotDto;
import fr.insastronaute.webapi.sts.combat.command.CombatCommand;
import fr.insastronaute.webapi.sts.combat.command.EndTurnCommand;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpBattleSettlement;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpCombatBroadcaster;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpCombatService;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpCombatView;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpBattle;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpBattleStatus;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpChallenge;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpChallengeStatus;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpDeck;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpMode;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpRaidEncounters;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpNotification;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpNotificationType;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpProfile;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpQueueEntry;
import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpBattleActionRequest;
import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpBattleDto;
import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpChallengeDto;
import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpChallengeRequest;
import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpChallengeResponse;
import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpCollectionDto;
import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpDeckDto;
import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpDeckSaveRequest;
import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpNotificationDto;
import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpParticipantDto;
import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpProfileDto;
import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpProfileUpdateRequest;
import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpQueueStatusResponse;
import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpQuickMatchRequest;
import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpQuickMatchResponse;
import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpMatchmakingStateResponse;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpBattleRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpChallengeRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpDeckRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpNotificationRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpProfileRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpQueueEntryRepository;
import fr.insastronaute.webapi.sts.service.StsGameDataService;
import fr.insastronaute.webapi.sts.domain.StsRun;
import fr.insastronaute.webapi.sts.domain.StsRunStatus;
import fr.insastronaute.webapi.sts.repository.StsRunRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@Service
public class StsPvpService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StsPvpService.class);

    private final SecureRandom unlockRandom = new SecureRandom();
    /** Tire la rencontre de chaque raid. */
    private final SecureRandom raidEncounterRandom = new SecureRandom();

    private static final int DEFAULT_ELO = 1000;
    // Fallbacks only: a data version without gameplay/pvp_deck_rules.json keeps composing decks
    // with these, see minDeckSize()/maxDeckSize().
    private static final int DEFAULT_MIN_DECK_SIZE = 10;
    private static final int DEFAULT_MAX_DECK_SIZE = 30;
    private static final long CHALLENGE_TTL_MINUTES = 10L;
    private static final long MATCHMAKING_ENTRY_TTL_SECONDS = 30L;
    private static final long MATCH_RESULT_RECOVERY_SECONDS = 120L;
    private static final long INACTIVE_TIMEOUT_SECONDS = 45L;
    /** The version of the combat protocol a snapshot is expressed in, the one PvE also speaks. */
    private static final int COMBAT_PROTOCOL_VERSION = 1;
    /** What tells a client reading a snapshot which of the two combats it is looking at. */
    private static final String PVP_MODE = "PVP";

    private final StsPvpProfileRepository profileRepository;
    /**
     * Le boss de raid par defaut.
     *
     * <p>Ecrit aussi comme valeur de champ, et pas seulement dans l'expression {@code @Value} :
     * une instance construite directement — les tests unitaires le sont tous — ne passe par
     * aucune injection, et un identifiant de boss nul ne se verrait qu'au premier raid.
     */
    private static final String DEFAULT_RAID_BOSS_ENEMY_ID = "Ironclad";

    /**
     * L'ennemi que les raids opposent aux joueurs.
     *
     * <p>Configurable plutot qu'ecrit en dur : le boss est un ennemi des donnees de jeu comme un
     * autre, et le choisir depuis la configuration evite d'avoir a redeployer pour en changer.
     */
    @org.springframework.beans.factory.annotation.Value("${sts.pvp.raid.boss-enemy-id:Ironclad}")
    private String raidBossEnemyId = DEFAULT_RAID_BOSS_ENEMY_ID;

    private final StsPvpDeckRepository deckRepository;
    private final StsPvpQueueEntryRepository queueRepository;
    private final StsPvpChallengeRepository challengeRepository;
    private final StsPvpBattleRepository battleRepository;
    private final StsPvpNotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final DepartmentRepository departmentRepository;
    private final CardService cardService;
    private final StsGameDataService gameDataService;
    private final StsRunRepository stsRunRepository;
    private final ObjectMapper objectMapper;
    private final StsPvpCombatService combatService;
    private final StsPvpCombatBroadcaster broadcaster;
    private final CollectionTestModeService collectionTestMode;

    public StsPvpService(
            StsPvpProfileRepository profileRepository,
            StsPvpDeckRepository deckRepository,
            StsPvpQueueEntryRepository queueRepository,
            StsPvpChallengeRepository challengeRepository,
            StsPvpBattleRepository battleRepository,
            StsPvpNotificationRepository notificationRepository,
            UserRepository userRepository,
            FriendshipRepository friendshipRepository,
            DepartmentRepository departmentRepository,
            CardService cardService,
            StsGameDataService gameDataService,
            StsRunRepository stsRunRepository,
            ObjectMapper objectMapper,
            StsPvpCombatService combatService,
            StsPvpCombatBroadcaster broadcaster,
            CollectionTestModeService collectionTestMode
    ) {
        this.profileRepository = profileRepository;
        this.deckRepository = deckRepository;
        this.queueRepository = queueRepository;
        this.challengeRepository = challengeRepository;
        this.battleRepository = battleRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.friendshipRepository = friendshipRepository;
        this.departmentRepository = departmentRepository;
        this.cardService = cardService;
        this.gameDataService = gameDataService;
        this.stsRunRepository = stsRunRepository;
        this.objectMapper = objectMapper;
        this.combatService = combatService;
        this.collectionTestMode = collectionTestMode;
        this.broadcaster = broadcaster;
    }

    @Transactional
    public StsPvpProfileDto getProfile(AppUser currentUser) {
        StsPvpProfile profile = ensureProfile(currentUser);
        return toProfileDto(profile);
    }

    @Transactional
    public StsPvpProfileDto updateProfile(AppUser currentUser, StsPvpProfileUpdateRequest request) {
        StsPvpProfile profile = ensureProfileForUpdate(currentUser);

        if (request != null && request.selectedCharacter() != null && !request.selectedCharacter().isBlank()) {
            validateCharacter(request.selectedCharacter());
            profile.setSelectedCharacter(request.selectedCharacter().trim());
        }

        if (request != null && request.friendMatch() != null) {
            profile.setPreferredFriendly(request.friendMatch());
        }

        return toProfileDto(profileRepository.save(profile));
    }

    @Transactional(readOnly = true)
    public StsPvpCollectionDto getCollection(AppUser currentUser) {
        StsPvpProfile profile = ensureProfile(currentUser);
        Set<String> ownedCardIds = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        ownedCardIds.addAll(deckBuildableCardIds(currentUser.getKeycloakSub()));
        // Base-pool and multiplayer-exclusive cards have no collection card to own: this is their
        // only record of having been earned through a run's end.
        ownedCardIds.addAll(unlockedStsCardKeys(profile));
        Map<String, Integer> characterLevels = characterLevels(profile);
        Set<String> unlockedMultiplayerCardIds = unlockedMultiplayerCardIds(profile.getSelectedCharacter(), characterLevels);

        return new StsPvpCollectionDto(
                ownedCardIds.stream().sorted().toList(),
                unlockedMultiplayerCardIds.stream().sorted().toList(),
                characterLevels,
                minDeckSize(),
                maxDeckSize(),
                // Sans ce drapeau, un joueur verrait sa collection entière sans
                // comprendre pourquoi, et la croirait acquise.
                allCardsUnlocked()
        );
    }

    /**
     * Grants the PvP card unlocks a finished run has earned.
     *
     * <p>There used to be an endpoint for this, and a client decided when to call it. That put the
     * concession at a moment of the client's choosing rather than at the moment that earned it, and
     * a run whose status granted nothing yet still burned its one-shot flag on the way through. It
     * is now driven by {@code StsRunService} from the transition itself — retreat, abandon or
     * defeat — and there is no client-facing way to ask for it.
     *
     * <p>Runs inside the caller's transaction, on the run entity the caller already holds locked.
     *
     * @return the names of the cards newly unlocked, in the order granted; empty when nothing was
     *         earned or the run had already claimed its unlocks
     */
    public List<String> grantRunEndUnlocks(StsRun run) {
        // The flag is the idempotency guard, and it is only ever set once cards actually change
        // hands: a run that earned nothing must stay able to earn later.
        if (run == null || run.getOwner() == null || run.isPvpUnlockClaimed()) {
            return List.of();
        }

        // A retreat and a game over both hand over the whole deck rather than a draw from it: a run
        // played until it ended, however it ended, has earned every card it carried. Drawing a
        // single card on a defeat made collecting them all a matter of luck over many deaths.
        // Only abandoning mid-run still draws one consolation card.
        boolean wholeDeck = run.getStatus() == StsRunStatus.Retired
                || run.getStatus() == StsRunStatus.Completed
                || run.getStatus() == StsRunStatus.Failed;
        int allowedUnlockCount = wholeDeck ? Integer.MAX_VALUE : runEndUnlockCount(run);
        if (allowedUnlockCount <= 0) {
            return List.of();
        }

        // Locked under this same call: a run's own deck cards and its multiplayer-exclusive bonus
        // both check and write this set, and two run ends racing each other must not both draw the
        // exclusive their character has a single copy of to give.
        StsPvpProfile profile = ensureProfileForUpdate(run.getOwner());
        Set<String> alreadyUnlocked = new HashSet<>(ownedVirtualCardIds(run.getOwner().getKeycloakSub()));
        alreadyUnlocked.addAll(unlockedStsCardKeys(profile));

        // Only cards actually in this run's deck, matching its selected character, are eligible —
        // nothing the client says takes part in the decision. A collection-linked card only joins
        // them when its category is configured as never legitimately obtainable; otherwise it must
        // still be earned the normal way.
        Map<String, String> collectionCategoriesByCardName =
                collectionCardCategoriesByName();
        List<String> candidates = new ArrayList<>(eligibleRunUnlockCandidates(
                run, alreadyUnlocked, collectionCategoriesByCardName, runEndUnlockableCategories()));

        List<String> cardNamesToGrant = new ArrayList<>();
        if (!candidates.isEmpty()) {
            if (wholeDeck) {
                // Nothing to draw: every eligible card leaves with the player.
                cardNamesToGrant.addAll(candidates);
            } else {
                // Drawn, not taken in deck order. Deck order means nothing to the player, so
                // handing out its first few read as an arbitrary pick. A draw cannot be
                // reconstructed from the run afterwards, so what it gave is written down.
                Collections.shuffle(candidates, unlockRandom);
                cardNamesToGrant.addAll(candidates.stream().limit(allowedUnlockCount).toList());
            }
        }

        // A bonus on top of the deck draw, not instead of it: multiplayer-exclusive cards cannot
        // sit in a PvE deck, so they can never come out of eligibleRunUnlockCandidates, and this is
        // their only door in now that character level no longer opens one.
        String bonusExclusive = randomEligibleMultiplayerExclusiveCard(run, alreadyUnlocked);
        if (bonusExclusive != null) {
            cardNamesToGrant.add(bonusExclusive);
        }

        if (cardNamesToGrant.isEmpty()) {
            return List.of();
        }

        run.setPvpUnlockClaimed(true);
        // Keeps the physical/virtual collection in sync for the cards that are actually collection
        // cards; a name with no match there is silently ignored, which is fine for the rest.
        cardService.grantVirtualCardsByName(run.getOwner().getId(), cardNamesToGrant);
        recordUnlockedStsCardKeys(profile, cardNamesToGrant);
        LOGGER.info("PvP run-end unlocks granted: run={} status={} act={} drawn={}/{} cards={}",
                run.getId(), run.getStatus(), run.getAct(),
                cardNamesToGrant.size(), candidates.size(), cardNamesToGrant);
        return cardNamesToGrant;
    }

    // Abandoning mid-run only ever grants one consolation card, and only past the first act. A
    // retreat and a game over both hand over the whole deck, decided in grantRunEndUnlocks, so
    // this formula is never consulted for them.
    private int runEndUnlockCount(StsRun run) {
        int act = Math.max(0, run.getAct());

        if (run.getStatus() == StsRunStatus.Abandoned) {
            // « Past the first act » veut dire un acte franchi, pas un numéro d'acte : l'enemy
            // pool peut faire démarrer les runs plus loin, et s'arrêter là où l'on a commencé ne
            // vaut rien de plus qu'abandonner au premier acte.
            return act > gameDataService.startingAct(run.getDataVersion()) ? 1 : 0;
        }

        return 0;
    }

    /**
     * The identifier a run-end unlock is recorded and checked under.
     *
     * <p>A collection-linked card is named by that link, because that is also the name
     * {@code cardService.grantVirtualCardsByName} matches against; a card with no such link falls
     * back to its own catalog name, which is the only name it has.
     */
    private String cardUnlockKey(JsonNode card) {
        String collectionCardId = textOrNull(card, "collectionCardId");
        // Un chemin d'illustration ne nomme aucune carte de collection : l'enregistrer comme cle
        // de deverrouillage inscrirait « /iticbi1/cards/sans.jpg » dans le profil du joueur, que
        // grantVirtualCardsByName ne rapprocherait de rien et qu'aucun ecran ne saurait afficher.
        if (collectionCardId != null && !isArtworkPath(collectionCardId)) {
            return collectionCardId;
        }
        return textOrNull(card, "cardName");
    }

    /**
     * Si une carte favorisee ainsi peut revenir a une run menee par ce personnage.
     *
     * <p>Une carte neutre n'est favorisee par personne et se joue dans le deck PvP de n'importe
     * lequel : l'ecran de deck ne retient que « Aucun » et le personnage courant. L'egalite
     * stricte l'ecartait pourtant du tirage de fin de run, et comme ce tirage est la seule porte
     * d'entree d'une carte sans lien de collection, ces cartes-la n'etaient obtenables par aucun
     * moyen -- silencieusement, puisque rien ne distingue « pas tire » de « impossible a tirer ».
     * Les cartes secretes, toutes neutres, tombaient exactement dedans.
     *
     * <p>Une carte favorisee par un <em>autre</em> personnage reste ecartee : c'est cela que la
     * regle voulait dire, et une carte neutre ne l'a jamais enfreint.
     */
    private boolean unlockableByCharacter(String favoredCharacter, String selectedCharacter) {
        String favored = favoredCharacter == null ? "" : favoredCharacter.trim();
        if (favored.isEmpty() || "Aucun".equalsIgnoreCase(favored) || "Neutral".equalsIgnoreCase(favored)) {
            return true;
        }
        return favored.equalsIgnoreCase(selectedCharacter);
    }

    /**
     * Si ce {@code collectionCardId} designe une illustration plutot qu'une carte de collection.
     *
     * <p>Les cartes secretes empruntent les images troll du theme iticbi1, qui sont des fichiers
     * statiques du site : elles n'ont ni entree de collection, ni categorie, ni proprietaire.
     * Leur donner le chemin de l'image comme {@code collectionCardId} evite un second champ que
     * toutes les couches devraient porter, du ScriptableObject jusqu'ici.
     *
     * <p>La forme suffit a les distinguer : un nom de carte de collection est un nom propre
     * (« Adam »), jamais un chemin. Doit rester en phase avec {@code STSCollectionCardApi
     * .IsImagePath} cote Unity, qui decide de la meme chose pour charger l'image.
     */
    static boolean isArtworkPath(String collectionCardId) {
        if (collectionCardId == null) {
            return false;
        }

        String trimmed = collectionCardId.trim();
        if (trimmed.isEmpty()) {
            return false;
        }

        String lower = trimmed.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("data:")) {
            return true;
        }
        if (trimmed.indexOf('/') >= 0 || trimmed.indexOf('\\') >= 0) {
            return true;
        }
        for (String extension : new String[] { ".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp" }) {
            if (lower.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    /** The run-end-unlocked card keys this profile has already earned, from any past run. */
    private Set<String> unlockedStsCardKeys(StsPvpProfile profile) {
        Set<String> keys = new HashSet<>();
        JsonNode unlocked = profile.getUnlockedCards();
        if (unlocked != null && unlocked.isArray()) {
            for (JsonNode key : unlocked) {
                if (key != null && key.isTextual() && !key.asText().isBlank()) {
                    keys.add(key.asText().trim());
                }
            }
        }
        return keys;
    }

    /** Adds {@code cardNames} to the profile's permanent unlock record. */
    private void recordUnlockedStsCardKeys(StsPvpProfile profile, Collection<String> cardNames) {
        Set<String> merged = unlockedStsCardKeys(profile);
        merged.addAll(cardNames);
        ArrayNode array = objectMapper.createArrayNode();
        merged.stream().sorted(String.CASE_INSENSITIVE_ORDER).forEach(array::add);
        profile.setUnlockedCards(array);
        profileRepository.save(profile);
    }

    /**
     * One random not-yet-unlocked multiplayer-exclusive card for this run's character, or
     * {@code null} when none is left to give.
     *
     * <p>These cards cannot sit in a PvE deck, so a run's own deck never produces one: this is a
     * flat bonus alongside it, not a draw from it. Character level is not consulted; it is retired
     * as a gate until this replaces it for good.
     */
    private String randomEligibleMultiplayerExclusiveCard(StsRun run, Set<String> alreadyUnlocked) {
        JsonNode cards = gameDataService.cards(run.getDataVersion()).path("cards");
        if (cards == null || !cards.isArray()) {
            return null;
        }

        String selectedCharacter = run.getSelectedCharacter();
        Set<String> seen = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        List<String> candidates = new ArrayList<>();

        for (JsonNode card : cards) {
            if (card == null || !card.path("multiplayerExclusive").asBoolean(false)) {
                continue;
            }
            if (!unlockableByCharacter(card.path("favoredCharacter").asText(""), selectedCharacter)) {
                continue;
            }

            String key = cardUnlockKey(card);
            if (key == null || alreadyUnlocked.contains(key) || !seen.add(key)) {
                continue;
            }
            candidates.add(key);
        }

        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(unlockRandom.nextInt(candidates.size()));
    }

    private List<String> eligibleRunUnlockCandidates(
            StsRun run,
            Set<String> alreadyOwned,
            Map<String, String> collectionCategoriesByCardName,
            Set<String> runEndUnlockableCategories
    ) {
        JsonNode deck = run.getRunInventory() == null ? null : run.getRunInventory().path("deck");
        if (deck == null || !deck.isArray() || deck.isEmpty()) {
            return List.of();
        }

        String selectedCharacter = run.getSelectedCharacter();
        Map<String, JsonNode> cardsById = stsCardsById(run.getDataVersion());
        Set<String> seen = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        List<String> candidates = new ArrayList<>();

        for (JsonNode entry : deck) {
            String cardId = textOrNull(entry, "cardId");
            JsonNode card = cardId == null ? null : cardsById.get(cardId);
            if (card == null || card.path("pveOnly").asBoolean(false)) {
                continue;
            }

            String favoredCharacter = card.path("favoredCharacter").asText("");
            if (!unlockableByCharacter(favoredCharacter, selectedCharacter)) {
                continue;
            }

            String candidateName = cardUnlockKey(card);
            if (candidateName == null || alreadyOwned.contains(candidateName) || !seen.add(candidateName)) {
                continue;
            }

            String collectionCardId = textOrNull(card, "collectionCardId");
            // Une illustration n'est pas un lien de collection : rien ne s'y gagne, donc rien n'y
            // est a gagner d'abord. Ces cartes se deverrouillent comme celles qui n'ont aucun
            // lien, exactement comme une carte dont la categorie est declaree jamais obtenable --
            // sans quoi les cartes secretes, toutes illustrees ainsi, ne seraient jouables en
            // duel par personne.
            if (collectionCardId != null && !isArtworkPath(collectionCardId)) {
                String category = collectionCategoriesByCardName.get(collectionCardId);
                boolean neverLegitimatelyObtainable = category != null && runEndUnlockableCategories.contains(category);
                if (!neverLegitimatelyObtainable) {
                    // Linked to a real collection card that is not configured as an exception: it
                    // has to be earned there, a run's end does not hand it out.
                    continue;
                }
            }

            candidates.add(candidateName);
        }

        return candidates;
    }

    /**
     * The category each collection card is filed under, by card name.
     *
     * <p>Unfiltered by ownership, quantity, <em>and visibility</em>. It used to be read through
     * the player's deck, which drops hidden categories and disabled cards — but a retired
     * collection is hidden precisely because it can no longer be obtained, which is exactly what
     * {@code runEndUnlockableCategories} exists to name. Its cards came back with no category,
     * so they were exempt from nothing, and a retreat skipped them without a word.
     */
    private Map<String, String> collectionCardCategoriesByName() {
        return cardService.categoryNamesByCardName();
    }

    /**
     * The collection categories configured, in {@code gameplay/pvp_deck_rules.json}, as never
     * legitimately obtainable — their linked cards unlock like a non-collection card, at a run's
     * end, instead of requiring real ownership.
     */
    private Set<String> runEndUnlockableCategories() {
        Set<String> categories = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        JsonNode rules = deckRulesNode();
        JsonNode configured = rules == null ? null : rules.path("runEndUnlockableCategories");
        if (configured != null && configured.isArray()) {
            for (JsonNode category : configured) {
                if (category != null && category.isTextual() && !category.asText().isBlank()) {
                    categories.add(category.asText().trim());
                }
            }
        }
        return categories;
    }


    @Transactional(readOnly = true)
    public List<StsPvpDeckDto> listDecks(AppUser currentUser) {
        return deckRepository.findByOwner_KeycloakSubOrderByUpdatedAtDesc(currentUser.getKeycloakSub())
                .stream()
                .map(this::toDeckDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public StsPvpDeckDto getDeck(AppUser currentUser, UUID deckId) {
        StsPvpDeck deck = deckRepository.findByIdAndOwner_KeycloakSub(deckId, currentUser.getKeycloakSub())
                .orElseThrow(() -> new NotFoundException("Deck PVP introuvable."));
        return toDeckDto(deck);
    }

    /**
     * Efface un deck du joueur courant.
     *
     * <p>La liste des decks n'avait aucune sortie : on pouvait en empiler autant qu'on
     * voulait et jamais en retirer un, si bien que le menu finissait par proposer un
     * choix illisible. Effacer est donc la seule opération manquante, et elle est écrite
     * ici plutôt que dans le contrôleur pour une raison : la propriété du deck se vérifie
     * en le cherchant par son id <em>et</em> par son propriétaire. Chercher par le seul id
     * puis comparer laisserait, entre les deux, un chemin où l'on efface le deck d'autrui.
     *
     * <p>Le deck actif s'efface comme les autres. Refuser reviendrait à ne jamais pouvoir
     * nettoyer un personnage dont tous les decks sont anciens, et le combat sait déjà se
     * passer d'un deck marqué actif : il retombe sur le plus récent. Ce que le joueur perd
     * en effaçant le dernier deck d'un personnage, la file de matchmaking le lui dit
     * ({@code requireUsableDeck}) avant d'engager qui que ce soit contre lui.
     */
    @Transactional
    public void deleteDeck(AppUser currentUser, UUID deckId) {
        StsPvpDeck deck = deckRepository.findByIdAndOwner_KeycloakSub(deckId, currentUser.getKeycloakSub())
                .orElseThrow(() -> new NotFoundException("Deck PVP introuvable."));
        deckRepository.delete(deck);
    }

    @Transactional
    public StsPvpDeckDto saveDeck(AppUser currentUser, StsPvpDeckSaveRequest request) {
        if (request == null) {
            throw new BadRequestException("Le deck est requis.");
        }

        List<String> cardIds = normalizeDeckCardIds(request.cardIds());
        if (cardIds.size() < minDeckSize()) {
            throw new BadRequestException("Deck trop petit.");
        }
        if (cardIds.size() > maxDeckSize()) {
            throw new BadRequestException("Deck trop grand.");
        }

        StsPvpProfile profile = ensureProfileForUpdate(currentUser);
        String selectedCharacter = request.selectedCharacter();
        if (selectedCharacter == null || selectedCharacter.isBlank()) {
            selectedCharacter = profile.getSelectedCharacter();
        }
        validateCharacter(selectedCharacter);

        validateDeckCards(currentUser, selectedCharacter, cardIds, profile);

        String name = (request.name() == null || request.name().isBlank()) ? "Deck" : request.name().trim();

        // Un nom déjà pris pour ce personnage remplace le deck qui le porte, au lieu d'en ajouter un
        // second au menu : voir StsPvpDeckNaming. Le plus récent est gardé et mis à jour — son
        // identifiant ne change pas, ce qui garde la sélection du menu — et les doublons plus
        // anciens, laissés par les versions d'avant cette règle, sont retirés.
        List<StsPvpDeck> sameNamed = StsPvpDeckNaming.sameNamed(
                deckRepository.findByOwner_KeycloakSubAndSelectedCharacterOrderByUpdatedAtDesc(
                        currentUser.getKeycloakSub(), selectedCharacter),
                name);
        StsPvpDeck deck = sameNamed.isEmpty() ? new StsPvpDeck() : sameNamed.get(0);
        // Remplacer le contenu d'un deck ne le désactive pas : une sauvegarde simple (isActive
        // faux) du deck actif le laisserait sinon sans deck pour combattre.
        boolean wasActive = sameNamed.stream().anyMatch(existing -> Boolean.TRUE.equals(existing.getActive()));
        boolean activate = Boolean.TRUE.equals(request.isActive());

        deck.setOwner(currentUser);
        deck.setName(name);
        deck.setSelectedCharacter(selectedCharacter);
        deck.setCardIds(objectMapper.valueToTree(cardIds));
        for (StsPvpDeck duplicate : sameNamed.subList(Math.min(1, sameNamed.size()), sameNamed.size())) {
            deckRepository.delete(duplicate);
        }

        if (activate) {
            // Avant d'activer, et non après : le deck remplacé fait partie de ceux qu'on désactive.
            deactivateCurrentDecks(currentUser.getKeycloakSub(), selectedCharacter);
            // Composer un deck pour un personnage, c'est déjà l'avoir choisi. Laisser le
            // profil vide reviendrait à faire jouer autre chose à qui vient de préparer
            // celui-ci — c'est exactement ce qui envoyait des joueurs au combat sans deck.
            if (profile.getSelectedCharacter() == null || profile.getSelectedCharacter().isBlank()) {
                profile.setSelectedCharacter(selectedCharacter);
                profileRepository.save(profile);
            }
        }
        deck.setActive(activate || wasActive);

        return toDeckDto(deckRepository.save(deck));
    }

    /**
     * Refuse l'entrée en file à qui ne pourra pas se battre, avant qu'un adversaire
     * y soit engagé.
     *
     * <p>Sans ce garde, l'absence de deck ne se voyait qu'à la création du combat,
     * une fois les deux joueurs appariés : celui qui avait tout ce qu'il fallait se
     * prenait l'échec de l'autre, et le message parlait d'un personnage que personne
     * n'avait choisi.
     *
     * <p>Car un profil neuf n'a pas de personnage, et le combat lui en attribuait un
     * par défaut. Ici on ne devine pas : sans personnage choisi, on le dit.
     */
    private void requireUsableDeck(AppUser currentUser, StsPvpProfile profile) {
        String character = profile.getSelectedCharacter();
        if (character == null || character.isBlank()) {
            throw new BadRequestException(
                    "Choisis ton personnage dans le menu multijoueur avant de chercher un combat.");
        }

        boolean hasDeck = deckRepository
                .findByOwner_KeycloakSubAndSelectedCharacterOrderByUpdatedAtDesc(
                        currentUser.getKeycloakSub(), character)
                .stream()
                .anyMatch(deck -> deck.getCardIds() != null && !deck.getCardIds().isEmpty());
        if (!hasDeck) {
            throw new BadRequestException(
                    "Compose un deck pour " + character + " avant de chercher un combat.");
        }
    }

    @Transactional
    public StsPvpQuickMatchResponse quickMatch(AppUser currentUser, StsPvpQuickMatchRequest request) {
        // A singleton row serializes claiming, cancellation and stale-entry cleanup on every
        // application instance. Without it, two callers can both select the same opponent.
        queueRepository.lockMatchmaking();
        queueRepository.deleteByUpdatedAtBefore(Instant.now().minusSeconds(MATCHMAKING_ENTRY_TTL_SECONDS));

        StsPvpProfile profile = ensureProfileForUpdate(currentUser);
        boolean friendly = request != null && Boolean.TRUE.equals(request.friendly());
        boolean fillWithAi = request != null && Boolean.TRUE.equals(request.fillWithAi());
        StsPvpMode mode = parseMode(request);
        requireUsableDeck(currentUser, profile);

        StsPvpQueueEntry myEntry = queueRepository.findByUser_KeycloakSub(currentUser.getKeycloakSub()).orElse(null);
        if (myEntry == null) {
            myEntry = new StsPvpQueueEntry();
            myEntry.setUser(currentUser);
        }
        myEntry.setFriendly(friendly);
        myEntry.setMode(mode);
        myEntry.setEloSnapshot(profile.getElo() == null ? DEFAULT_ELO : profile.getElo());
        myEntry.touch();
        queueRepository.save(myEntry);

        Pairing pairing = pair(currentUser, myEntry, fillWithAi);
        if (pairing.battleId() == null) {
            return new StsPvpQuickMatchResponse(
                    true, false, null,
                    "Recherche de joueurs en cours (" + pairing.queuedPlayers() + "/"
                            + mode.totalHumanSeats() + ").",
                    mode.name(), pairing.queuedPlayers(), mode.totalHumanSeats());
        }

        return new StsPvpQuickMatchResponse(
                false, true, pairing.battleId(), "Match trouve.",
                mode.name(), pairing.queuedPlayers(), mode.totalHumanSeats());
    }

    /**
     * Une tentative d'appariement : la bataille ouverte, ou le compte de joueurs réunis.
     *
     * @param battleId      la bataille ouverte, ou null quand il manque encore du monde
     * @param queuedPlayers combien de joueurs la file réunissait, le demandeur compris
     */
    private record Pairing(UUID battleId, int queuedPlayers) {
    }

    /**
     * Cherche à compléter la salle de {@code myEntry}, et ouvre la bataille si elle est pleine.
     *
     * <p>Appelé à l'inscription <em>et</em> à chaque battement de cœur. Ce n'était le cas qu'à
     * l'inscription, et cela suffisait tant qu'un mode ne réunissait que deux joueurs : le second
     * arrivé refermait toujours la salle. À trois places ou plus, une salle pouvait rester à 3/4
     * indéfiniment — les trois présents ne faisaient que battre, et personne ne recomptait — alors
     * qu'un quatrième était parti puis revenu entre-temps.
     *
     * <p>L'appelant a déjà pris le verrou d'appariement : deux salles ne peuvent pas réclamer le
     * même joueur.
     */
    private Pairing pair(AppUser currentUser, StsPvpQueueEntry myEntry, boolean fillWithAi) {
        StsPvpMode mode = myEntry.getMode();
        boolean friendly = Boolean.TRUE.equals(myEntry.getFriendly());
        final int myElo = myEntry.getEloSnapshot() == null ? DEFAULT_ELO : myEntry.getEloSnapshot();
        final UUID myId = currentUser.getId();
        // Les adversaires les plus proches au classement, autant qu'il en faut pour completer le
        // format. La file est lue par mode : chercher un raid ne peut pas tirer un duelliste.
        List<StsPvpQueueEntry> candidates = queueRepository
                .findByModeAndFriendlyOrderByQueuedAtAsc(mode, friendly)
                .stream()
                .filter(candidate -> !candidate.getUser().getId().equals(myId))
                .sorted(Comparator.comparingInt(candidate -> Math.abs(candidate.getEloSnapshot() - myElo)))
                .limit(mode.totalHumanSeats() - 1L)
                .toList();

        int queuedPlayers = candidates.size() + 1;
        boolean roomFull = queuedPlayers >= mode.totalHumanSeats();
        if (!roomFull && !fillWithAi) {
            return new Pairing(null, queuedPlayers);
        }

        // Les joueurs reunis, dans l'ordre des sieges : celui qui demande occupe le premier, ce
        // qui le place dans l'equipe 0 — la sienne, et celle de ses coequipiers en raid.
        List<AppUser> players = new ArrayList<>();
        players.add(currentUser);
        candidates.forEach(candidate -> players.add(candidate.getUser()));

        StsPvpBattle battle = createBattle(mode, players, friendly);

        queueRepository.delete(myEntry);
        candidates.forEach(queueRepository::delete);

        for (AppUser player : players) {
            createNotification(
                    player,
                    currentUser,
                    StsPvpNotificationType.QUICK_MATCH_FOUND,
                    "Match trouve",
                    "Un combat " + mode.label() + " a ete trouve.",
                    objectMapper.createObjectNode()
                            .put("battleId", battle.getId().toString())
                            .put("friendly", friendly)
                            .put("mode", mode.name())
            );
        }

        return new Pairing(battle.getId(), players.size());
    }

    /**
     * Le mode demande.
     *
     * <p>Un mode inconnu est refuse plutot que ramene au duel : le demandeur a nomme un format,
     * et le mettre silencieusement dans un autre est pire que lui dire non.
     */
    /**
     * Combien de joueurs cherchent en ce moment dans une file donnée.
     *
     * <p>En lecture seule et sans le verrou d'appariement : c'est un affichage, interrogé en
     * boucle par tous les menus ouverts, et le faire passer par le verrou ferait payer à
     * l'appariement le prix de gens qui ne font que regarder.</p>
     *
     * <p>La fenêtre de fraîcheur est celle qui décide de la validité d'une entrée ailleurs,
     * {@link #MATCHMAKING_ENTRY_TTL_SECONDS} : ce qui est annoncé est donc exactement ce avec
     * quoi l'appariement accepterait de travailler, et non ce qui traîne encore en base.</p>
     *
     * <p>Le demandeur se compte lui-même s'il est en file, comme dans le « (2/4) » que rend
     * déjà la recherche rapide. Deux comptes qui ne diraient pas la même chose du même moment
     * seraient pires qu'un seul, fût-il discutable.</p>
     */
    @Transactional(readOnly = true)
    public StsPvpQueueStatusResponse queueStatus(String modeName, boolean friendly) {
        StsPvpMode mode;
        try {
            mode = StsPvpMode.parse(modeName);
        } catch (IllegalArgumentException unknown) {
            throw new BadRequestException(unknown.getMessage());
        }

        long waiting = queueRepository.countByModeAndFriendlyAndUpdatedAtAfter(
                mode, friendly, Instant.now().minusSeconds(MATCHMAKING_ENTRY_TTL_SECONDS));

        return new StsPvpQueueStatusResponse(mode.name(), friendly, waiting, mode.totalHumanSeats());
    }

    private static StsPvpMode parseMode(StsPvpQuickMatchRequest request) {
        try {
            return StsPvpMode.parse(request == null ? null : request.mode());
        } catch (IllegalArgumentException unknown) {
            throw new BadRequestException(unknown.getMessage());
        }
    }

    @Transactional
    public StsPvpMatchmakingStateResponse cancelQuickMatch(AppUser currentUser) {
        queueRepository.lockMatchmaking();
        long deleted = queueRepository.deleteByUser_KeycloakSub(currentUser.getKeycloakSub());
        UUID battleId = deleted == 0 ? latestMatchedBattleId(currentUser).orElse(null) : null;
        return new StsPvpMatchmakingStateResponse(false, battleId == null, battleId);
    }

    @Transactional
    public StsPvpMatchmakingStateResponse heartbeatQuickMatch(AppUser currentUser) {
        queueRepository.lockMatchmaking();
        queueRepository.deleteByUpdatedAtBefore(Instant.now().minusSeconds(MATCHMAKING_ENTRY_TTL_SECONDS));
        Optional<StsPvpQueueEntry> existing = queueRepository.findByUser_KeycloakSub(currentUser.getKeycloakSub());
        if (existing.isPresent()) {
            StsPvpQueueEntry entry = existing.get();
            entry.touch();
            queueRepository.save(entry);
            // Le battement recompte la salle. Sans cela, une salle de plus de deux places pouvait
            // rester incomplète alors que tout le monde était arrivé : seule une inscription
            // appariait, et les présents ne faisaient plus que battre. Jamais de renfort IA ici —
            // le battement ne porte pas ce choix, et le combler d'office donnerait des robots à
            // qui n'en a pas demandé.
            UUID battleId = pair(currentUser, entry, false).battleId();
            return new StsPvpMatchmakingStateResponse(battleId == null, false, battleId);
        }
        return new StsPvpMatchmakingStateResponse(false, false, latestMatchedBattleId(currentUser).orElse(null));
    }

    private Optional<UUID> latestMatchedBattleId(AppUser currentUser) {
        return notificationRepository
                .findFirstByRecipientAndTypeAndReadAtIsNullOrderByCreatedAtDesc(
                        currentUser,
                        StsPvpNotificationType.QUICK_MATCH_FOUND)
                .filter(notification -> notification.getCreatedAt() != null
                        && notification.getCreatedAt().isAfter(Instant.now().minusSeconds(MATCH_RESULT_RECOVERY_SECONDS)))
                .map(StsPvpNotification::getPayload)
                .map(payload -> payload.path("battleId").asText(null))
                .filter(value -> value != null && !value.isBlank())
                .flatMap(value -> {
                    try {
                        return Optional.of(UUID.fromString(value));
                    } catch (IllegalArgumentException ignored) {
                        return Optional.empty();
                    }
                })
                .flatMap(battleRepository::findById)
                .filter(battle -> battle.getStatus() == StsPvpBattleStatus.PENDING
                        || battle.getStatus() == StsPvpBattleStatus.ACTIVE)
                .map(StsPvpBattle::getId);
    }

    @Transactional
    public StsPvpChallengeResponse sendChallenge(AppUser currentUser, StsPvpChallengeRequest request) {
        if (request == null || request.targetUserId() == null || request.targetUserId().isBlank()) {
            throw new BadRequestException("Le joueur ciblé est requis.");
        }

        UUID targetId;
        try {
            targetId = UUID.fromString(request.targetUserId().trim());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("ID joueur invalide.");
        }

        AppUser targetUser = userRepository.findById(targetId)
                .orElseThrow(() -> new NotFoundException("Joueur ciblé introuvable."));

        if (targetUser.getId().equals(currentUser.getId())) {
            throw new BadRequestException("Impossible de se défier soi-même.");
        }

        boolean acceptedFriend = friendshipRepository.findBetween(currentUser.getId(), targetUser.getId())
                .filter(friendship -> friendship.getStatus() == FriendshipStatus.ACCEPTED)
                .isPresent();
        if (!acceptedFriend) {
            throw new BadRequestException("Le joueur ciblé doit être un ami accepté.");
        }

        // Le défiant doit pouvoir se battre avant d'engager qui que ce soit : sans deck, l'échec ne
        // se verrait qu'à l'acceptation, et c'est le défié qui le prendrait en pleine figure.
        requireUsableDeck(currentUser, ensureProfileForUpdate(currentUser));

        // Un défi encore en cours vers le même ami est rendu tel quel plutôt que doublé : cliquer
        // deux fois sur « Défier », ou défier depuis le site puis depuis le jeu, ne doit pas
        // laisser dix défis identiques dans sa liste.
        Optional<StsPvpChallenge> alreadyPending = challengeRepository
                .findFirstByChallenger_IdAndChallenged_IdAndStatusOrderByCreatedAtDesc(
                        currentUser.getId(), targetUser.getId(), StsPvpChallengeStatus.PENDING)
                .filter(pending -> !expireIfStale(pending));
        if (alreadyPending.isPresent()) {
            StsPvpChallenge pending = alreadyPending.get();
            return new StsPvpChallengeResponse(
                    pending.getId(), targetUser.getId(), pending.getFriendly(),
                    pending.getStatus().name(), pending.getExpiresAt());
        }

        StsPvpChallenge challenge = new StsPvpChallenge();
        challenge.setChallenger(currentUser);
        challenge.setChallenged(targetUser);
        challenge.setFriendly(Boolean.TRUE.equals(request.friendly()));
        challenge.setStatus(StsPvpChallengeStatus.PENDING);
        challenge.setExpiresAt(Instant.now().plus(CHALLENGE_TTL_MINUTES, ChronoUnit.MINUTES));
        challenge = challengeRepository.save(challenge);

        createNotification(
                targetUser,
                currentUser,
                StsPvpNotificationType.CHALLENGE_RECEIVED,
                "Défi PVP reçu",
                displayName(currentUser) + " vous défie en PVP.",
                objectMapper.createObjectNode()
                        .put("challengeId", challenge.getId().toString())
                        .put("friendly", challenge.getFriendly())
                        .put("challengerId", currentUser.getId().toString())
        );

        return new StsPvpChallengeResponse(
                challenge.getId(),
                targetUser.getId(),
                challenge.getFriendly(),
                challenge.getStatus().name(),
                challenge.getExpiresAt()
        );
    }

    /**
     * Les défis en cours du joueur : ceux qu'il a reçus et ceux qu'il a lancés.
     *
     * <p>Un défi dépassé est marqué expiré au passage plutôt que par une tâche planifiée : il
     * n'existe que pour être lu ici ou accepté, et les deux chemins le vérifient.
     */
    @Transactional
    public List<StsPvpChallengeDto> listChallenges(AppUser currentUser) {
        List<StsPvpChallengeDto> challenges = new ArrayList<>();
        for (StsPvpChallenge incoming : challengeRepository
                .findByChallenged_IdAndStatusOrderByCreatedAtDesc(currentUser.getId(), StsPvpChallengeStatus.PENDING)) {
            if (!expireIfStale(incoming)) {
                challenges.add(toChallengeDto(incoming, currentUser));
            }
        }
        for (StsPvpChallenge outgoing : challengeRepository
                .findByChallenger_IdAndStatusOrderByCreatedAtDesc(currentUser.getId(), StsPvpChallengeStatus.PENDING)) {
            if (!expireIfStale(outgoing)) {
                challenges.add(toChallengeDto(outgoing, currentUser));
            }
        }
        return challenges;
    }

    /**
     * Un défi, quel que soit son état — c'est ce que le défiant interroge pendant qu'il attend.
     *
     * <p>Un défi accepté porte sa bataille : c'est ainsi que le défiant apprend qu'il doit entrer
     * en combat, sans dépendre d'une notification qu'il aurait pu déjà marquer lue.
     *
     * @throws NotFoundException pour un défi inconnu ou qui ne concerne pas ce joueur — les deux
     *         se répondent de la même façon, pour ne pas dire quels défis existent entre d'autres
     */
    @Transactional
    public StsPvpChallengeDto getChallenge(AppUser currentUser, UUID challengeId) {
        StsPvpChallenge challenge = challengeRepository.findById(challengeId)
                .filter(found -> involves(found, currentUser))
                .orElseThrow(() -> new NotFoundException("Défi PVP introuvable."));
        expireIfStale(challenge);
        return toChallengeDto(challenge, currentUser);
    }

    /**
     * Le défié accepte : la bataille s'ouvre pour les deux.
     *
     * <p>Le verrou de l'appariement est pris en premier, comme pour une recherche rapide : sans
     * lui, l'un des deux joueurs pourrait être apparié par la file au même instant et se
     * retrouver dans deux batailles. Les deux joueurs quittent d'ailleurs la file, s'ils y étaient.
     *
     * <p>Les decks des deux sont vérifiés avant d'ouvrir quoi que ce soit, et le message dit
     * lequel manque : « compose un deck » adressé au défié parce que son ami n'en a plus serait
     * une énigme.
     */
    @Transactional
    public StsPvpChallengeDto acceptChallenge(AppUser currentUser, UUID challengeId) {
        queueRepository.lockMatchmaking();
        StsPvpChallenge challenge = lockedChallenge(challengeId, currentUser);
        if (!challenge.getChallenged().getId().equals(currentUser.getId())) {
            throw new BadRequestException("Seul le joueur défié peut accepter ce défi.");
        }
        requirePending(challenge);

        AppUser challenger = challenge.getChallenger();
        requireUsableDeck(currentUser, ensureProfileForUpdate(currentUser));
        try {
            requireUsableDeck(challenger, ensureProfileForUpdate(challenger));
        } catch (BadRequestException challengerCannotPlay) {
            throw new BadRequestException(
                    displayName(challenger) + " n'a plus de deck prêt pour ce défi.");
        }

        queueRepository.deleteByUser_KeycloakSub(currentUser.getKeycloakSub());
        queueRepository.deleteByUser_KeycloakSub(challenger.getKeycloakSub());

        // Le défiant occupe le premier siège : il est l'équipe 0, comme celui qui lance une
        // recherche. Rien n'en dépend en duel, mais un ordre stable rend les batailles lisibles.
        StsPvpBattle battle = createBattle(
                StsPvpMode.ONE_V_ONE, List.of(challenger, currentUser), Boolean.TRUE.equals(challenge.getFriendly()));

        challenge.setStatus(StsPvpChallengeStatus.ACCEPTED);
        challenge.setBattleId(battle.getId());
        challenge.setRespondedAt(Instant.now());
        challengeRepository.save(challenge);

        createNotification(
                challenger,
                currentUser,
                StsPvpNotificationType.CHALLENGE_ACCEPTED,
                "Défi accepté",
                displayName(currentUser) + " a accepté votre défi : le combat commence.",
                objectMapper.createObjectNode()
                        .put("challengeId", challenge.getId().toString())
                        .put("battleId", battle.getId().toString())
                        .put("friendly", challenge.getFriendly()));

        return toChallengeDto(challenge, currentUser);
    }

    /** Le défié refuse. Le défiant en est prévenu. */
    @Transactional
    public StsPvpChallengeDto declineChallenge(AppUser currentUser, UUID challengeId) {
        StsPvpChallenge challenge = lockedChallenge(challengeId, currentUser);
        if (!challenge.getChallenged().getId().equals(currentUser.getId())) {
            throw new BadRequestException("Seul le joueur défié peut refuser ce défi.");
        }
        requirePending(challenge);

        challenge.setStatus(StsPvpChallengeStatus.DECLINED);
        challenge.setRespondedAt(Instant.now());
        challengeRepository.save(challenge);

        createNotification(
                challenge.getChallenger(),
                currentUser,
                StsPvpNotificationType.CHALLENGE_DECLINED,
                "Défi refusé",
                displayName(currentUser) + " a refusé votre défi.",
                objectMapper.createObjectNode().put("challengeId", challenge.getId().toString()));

        return toChallengeDto(challenge, currentUser);
    }

    /** Le défiant retire son défi, tant que personne n'y a répondu. */
    @Transactional
    public StsPvpChallengeDto cancelChallenge(AppUser currentUser, UUID challengeId) {
        StsPvpChallenge challenge = lockedChallenge(challengeId, currentUser);
        if (!challenge.getChallenger().getId().equals(currentUser.getId())) {
            throw new BadRequestException("Seul le joueur qui a lancé ce défi peut l'annuler.");
        }
        requirePending(challenge);

        challenge.setStatus(StsPvpChallengeStatus.CANCELED);
        challenge.setRespondedAt(Instant.now());
        challengeRepository.save(challenge);
        return toChallengeDto(challenge, currentUser);
    }

    private StsPvpChallenge lockedChallenge(UUID challengeId, AppUser currentUser) {
        return challengeRepository.findByIdForUpdate(challengeId)
                .filter(found -> involves(found, currentUser))
                .orElseThrow(() -> new NotFoundException("Défi PVP introuvable."));
    }

    /**
     * Refuse un geste sur un défi qui n'attend plus de réponse.
     *
     * <p>Un défi déjà accepté, refusé ou annulé est un conflit et non une erreur de requête : le
     * joueur a agi sur un écran qui n'était plus à jour, et il lui faut relire le défi.
     */
    private void requirePending(StsPvpChallenge challenge) {
        if (expireIfStale(challenge)) {
            throw new ConflictException("Ce défi a expiré.");
        }
        if (challenge.getStatus() != StsPvpChallengeStatus.PENDING) {
            throw new ConflictException("Ce défi n'attend plus de réponse.");
        }
    }

    /**
     * Marque expiré un défi en attente dont le délai est passé.
     *
     * @return vrai quand le défi est (désormais) expiré
     */
    private boolean expireIfStale(StsPvpChallenge challenge) {
        if (challenge.getStatus() == StsPvpChallengeStatus.EXPIRED) {
            return true;
        }
        if (challenge.getStatus() != StsPvpChallengeStatus.PENDING
                || challenge.getExpiresAt() == null
                || challenge.getExpiresAt().isAfter(Instant.now())) {
            return false;
        }
        challenge.setStatus(StsPvpChallengeStatus.EXPIRED);
        challengeRepository.save(challenge);
        return true;
    }

    private static boolean involves(StsPvpChallenge challenge, AppUser user) {
        return challenge.getChallenger().getId().equals(user.getId())
                || challenge.getChallenged().getId().equals(user.getId());
    }

    private StsPvpChallengeDto toChallengeDto(StsPvpChallenge challenge, AppUser viewer) {
        boolean incoming = challenge.getChallenged().getId().equals(viewer.getId());
        return new StsPvpChallengeDto(
                challenge.getId(),
                incoming ? "INCOMING" : "OUTGOING",
                challenge.getChallenger().getId(),
                displayName(challenge.getChallenger()),
                challenge.getChallenged().getId(),
                displayName(challenge.getChallenged()),
                Boolean.TRUE.equals(challenge.getFriendly()),
                challenge.getStatus().name(),
                challenge.getBattleId(),
                challenge.getExpiresAt(),
                challenge.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<StsPvpNotificationDto> listNotifications(AppUser currentUser) {
        return notificationRepository.findByRecipientOrderByCreatedAtDesc(currentUser)
                .stream()
                .limit(100)
                .map(this::toNotificationDto)
                .toList();
    }

    @Transactional
    public StsPvpNotificationDto acknowledgeNotification(AppUser currentUser, UUID notificationId) {
        StsPvpNotification notification = notificationRepository.findByIdAndRecipient(notificationId, currentUser)
                .orElseThrow(() -> new NotFoundException("Notification PVP introuvable."));

        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
        }

        return toNotificationDto(notificationRepository.save(notification));
    }

    @Transactional(readOnly = true)
    public StsPvpBattleDto getBattleState(AppUser currentUser, UUID battleId) {
        StsPvpBattle battle = battleRepository.findById(battleId)
                .orElseThrow(() -> new NotFoundException("Combat PVP introuvable."));

        assertParticipant(currentUser, battle);
        return toBattleDto(battle, currentUser);
    }

    /**
     * The authoritative combat of {@code battleId}, in the snapshot shape a client resynchronises
     * from, projected for {@code currentUser}.
     *
     * <p>A client that lost a frame reads the combat back rather than guessing, and it used to
     * read it back from the PvE endpoint, which answers a hard-coded {@code "PVE"} and a shape the
     * PvP client cannot parse — so it asked, failed and asked again. This is the same question
     * answered on the PvP side.
     *
     * <p>What travels is {@link StsPvpCombatView#forViewer}, the same projection the socket sends,
     * and not a second one written here: the stored state carries both hands and the seed every
     * future draw is computable from, and a snapshot is either that projection or a leak. The
     * caller is checked to be a player of this battle first, so a stranger is refused rather than
     * handed the other two's combat — and {@code forViewer} refuses a stranger too, which is the
     * second lock on the same door.
     *
     * @throws NotFoundException when no such battle exists
     * @throws BadRequestException when {@code currentUser} does not play in it
     * @throws ConflictException when its combat has not opened yet, which has nothing to snapshot
     */
    @Transactional(readOnly = true)
    public CombatSnapshotDto combatSnapshot(AppUser currentUser, UUID battleId) {
        StsPvpBattle battle = battleRepository.findById(battleId)
                .orElseThrow(() -> new NotFoundException("Combat PVP introuvable."));

        assertParticipant(currentUser, battle);

        StsPvpCombatView view = StsPvpCombatView.forViewer(
                combatService.currentState(battle),
                currentUser.getId().toString(),
                battle.getTurnDeadline(),
                TimeTravelContext.instantNow(),
                // C'est ici, et pas dans la socket, que l'historique stocké gagne sa place :
                // un client qui se resynchronise n'a rejoué aucun événement, donc il n'a pas
                // vu passer les cartes déjà jouées. Sans cette ligne, se reconnecter viderait
                // l'historique d'un joueur pendant que l'autre le garde plein.
                combatService.cardHistory(battle));
        return new CombatSnapshotDto(
                COMBAT_PROTOCOL_VERSION,
                battleId.toString(),
                Long.toString(view.revision()),
                PVP_MODE,
                view);
    }

    /**
     * Ends the battle {@code currentUser} is giving up on, in their opponent's favour.
     *
     * <p>Until this existed the only way out of a duel was to close the tab and let the disconnect
     * sweep notice two minutes later, which looks to the other player exactly like a crash. What
     * happens here is that same forfeit, said out loud and taking effect at once: the decision
     * itself is {@link StsPvpCombatService#concede}, the one place a conceded duel is settled,
     * and this method only carries it in from HTTP and announces the result.
     *
     * <p>The battle is loaded under the write lock a player's own command takes, because a
     * surrender and the move the opponent makes at that exact moment must resolve one after the
     * other rather than one over the other.
     *
     * <p>Nothing happens, and nothing breaks, when there was nothing left to give up: a duel
     * already settled, or one the engine already ended, is answered with its own state and left
     * alone. Announcing it a second time would tell a client the fight ended twice, and moving the
     * rating a second time would charge the loss twice.
     *
     * @throws NotFoundException when no such battle exists
     * @throws BadRequestException when {@code currentUser} does not play in it
     */
    @Transactional
    public StsPvpBattleDto surrender(AppUser currentUser, UUID battleId) {
        StsPvpBattle battle = battleRepository.findByIdWithLock(battleId)
                .orElseThrow(() -> new NotFoundException("Combat PVP introuvable."));

        assertParticipant(currentUser, battle);

        Optional<StsPvpBattleSettlement.Conceded> conceded =
                combatService.concede(battle, currentUser.getId());
        if (conceded.isEmpty()) {
            return toBattleDto(battle, currentUser);
        }

        battleRepository.save(battle);
        LOGGER.info("PvP battle {} surrendered by {}", battleId, currentUser.getId());
        // The end is announced before the state it ends on, as every other event is: the client
        // reads the outcome from the CombatEnded, and a state alone leaves it showing a fight
        // still under way. The opponent pressed nothing, so the frames answer no action.
        broadcaster.publishEvents(battle, conceded.get().events(), null);
        broadcaster.publish(battle, conceded.get().state(), null);
        return toBattleDto(battle, currentUser);
    }

    @Transactional
    public StsPvpBattleDto submitBattleAction(AppUser currentUser, UUID battleId, StsPvpBattleActionRequest request) {
        StsPvpBattle battle = battleRepository.findById(battleId)
                .orElseThrow(() -> new NotFoundException("Combat PVP introuvable."));

        assertParticipant(currentUser, battle);
        // Both entry points stamp the same heartbeat through the same method: an action that
        // reaches the engine is stamped again there, which is idempotent.
        combatService.recordHeartbeat(battle, currentUser.getId());

        boolean heartbeatOnly = request == null || Boolean.TRUE.equals(request.heartbeatOnly());
        if (!heartbeatOnly) {
            // The action is neither trusted nor stored any more: the engine resolves it, and what
            // the battle keeps is the state the engine produced.
            CombatState state = combatService.currentState(battle);
            combatService.applyCommand(battle, currentUser.getId(), commandFrom(state, currentUser, request));
        }

        return toBattleDto(battleRepository.save(battle), currentUser);
    }

    /**
     * The engine command a client action asks for.
     *
     * <p>The actor is never read from the payload: it is the authenticated player, so no client
     * can act on its opponent's behalf. {@code expectedRevision} is required rather than assumed,
     * because it is what makes a replayed or late action fail instead of resolving twice.
     *
     * <p>Ending a turn is a command of its own, so an action that carries {@code endTurn} ends the
     * turn and nothing else; a card is played by its own call.
     */
    private CombatCommand commandFrom(CombatState state, AppUser currentUser, StsPvpBattleActionRequest request) {
        JsonNode action = request.action() == null
                ? objectMapper.createObjectNode()
                : JsonPayloads.toNode(request.action());
        if (!action.hasNonNull("expectedRevision")) {
            throw new BadRequestException("La révision attendue du combat PVP est requise.");
        }
        long expectedRevision = action.path("expectedRevision").asLong(-1L);
        if (expectedRevision < 0) {
            throw new BadRequestException("La révision attendue du combat PVP est invalide.");
        }

        String actorId = currentUser.getId().toString();
        String actionType = request.actionType() == null ? "" : request.actionType().trim().toUpperCase();
        if (Boolean.TRUE.equals(request.endTurn()) || "END_TURN".equals(actionType)) {
            return new EndTurnCommand(state.combatId(), expectedRevision, actorId);
        }

        String cardInstanceId = action.path("cardInstanceId").asText("").trim();
        if (cardInstanceId.isBlank()) {
            throw new BadRequestException("La carte jouée est requise.");
        }
        return new PlayCardCommand(
                state.combatId(),
                expectedRevision,
                actorId,
                cardInstanceId,
                extractStringArray(action.path("targetIds")),
                extractStringArray(action.path("selectedCardInstanceIds")));
    }

    private StsPvpProfile ensureProfile(AppUser user) {
        return profileRepository.findByUser_KeycloakSub(user.getKeycloakSub())
                .orElseGet(() -> createDefaultProfile(user));
    }

    private StsPvpProfile ensureProfileForUpdate(AppUser user) {
        return profileRepository.findByUserKeycloakSubForUpdate(user.getKeycloakSub())
                .orElseGet(() -> createDefaultProfile(user));
    }

    private StsPvpProfile createDefaultProfile(AppUser user) {
        StsPvpProfile profile = new StsPvpProfile();
        profile.setUser(user);
        profile.setSelectedCharacter("EP");
        profile.setElo(DEFAULT_ELO);
        profile.setCharacterExp(objectMapper.createObjectNode());
        profile.setCharacterStats(objectMapper.createObjectNode());
        profile.setUnlockedCards(objectMapper.createArrayNode());
        profile.setPreferredFriendly(false);
        profile.setWinsRanked(0);
        profile.setLossesRanked(0);
        profile.setWinsFriendly(0);
        profile.setLossesFriendly(0);
        return profileRepository.save(profile);
    }

    private StsPvpProfileDto toProfileDto(StsPvpProfile profile) {
        Map<String, Integer> levels = characterLevels(profile);
        return new StsPvpProfileDto(
                profile.getId(),
                profile.getUser().getId(),
                profile.getSelectedCharacter(),
                profile.getElo() == null ? DEFAULT_ELO : profile.getElo(),
                valueOrZero(profile.getWinsRanked()),
                valueOrZero(profile.getLossesRanked()),
                valueOrZero(profile.getWinsFriendly()),
                valueOrZero(profile.getLossesFriendly()),
                Boolean.TRUE.equals(profile.getPreferredFriendly()),
                levels,
                profile.getUpdatedAt()
        );
    }

    private StsPvpDeckDto toDeckDto(StsPvpDeck deck) {
        return new StsPvpDeckDto(
                deck.getId(),
                deck.getName(),
                deck.getSelectedCharacter(),
                Boolean.TRUE.equals(deck.getActive()),
                extractStringArray(deck.getCardIds()),
                deck.getUpdatedAt()
        );
    }

    private StsPvpNotificationDto toNotificationDto(StsPvpNotification notification) {
        Map<String, Object> payload = notification.getPayload() == null
                ? Map.of()
                : objectMapper.convertValue(notification.getPayload(), Map.class);

        return new StsPvpNotificationDto(
                notification.getId(),
                notification.getType().name(),
                notification.getTitle(),
                notification.getBody(),
                notification.getActor() == null ? null : notification.getActor().getId(),
                notification.getReadAt() != null,
                notification.getCreatedAt(),
                payload
        );
    }

    /**
     * The battle as one player may see it.
     *
     * <p>The viewer is a parameter rather than an afterthought because the battle's stored state is
     * the authoritative one: it holds both players' hands and the seed the whole future draw order
     * can be replayed from. What goes out is therefore the projection built for the reader, never
     * the state itself.
     */
    private StsPvpBattleDto toBattleDto(StsPvpBattle battle, AppUser viewer) {
        List<List<StsPvpParticipantDto>> teams = parseTeams(battle.getTeams());
        List<UUID> inactive = inactiveParticipants(battle);

        return new StsPvpBattleDto(
                battle.getId(),
                battle.getStatus().name(),
                Boolean.TRUE.equals(battle.getFriendly()),
                battle.getMode().name(),
                teams,
                JsonPayloads.toMap(projectedState(battle, viewer)),
                JsonPayloads.toMap(battle.getRewards()),
                battle.getTurnDeadline(),
                inactive,
                Instant.now()
        );
    }

    /**
     * @return the combat of {@code battle} projected for {@code viewer}, or null while the battle
     *         has no combat yet, which a pending battle is entitled to
     */
    private JsonNode projectedState(StsPvpBattle battle, AppUser viewer) {
        return combatService.currentStateIfAny(battle)
                .<JsonNode>map(state -> objectMapper.valueToTree(StsPvpCombatView.forViewer(
                        state,
                        viewer.getId().toString(),
                        battle.getTurnDeadline(),
                        TimeTravelContext.instantNow(),
                        combatService.cardHistory(battle))))
                .orElse(null);
    }

    private List<List<StsPvpParticipantDto>> parseTeams(JsonNode teamsNode) {
        List<List<StsPvpParticipantDto>> teams = new ArrayList<>();
        if (teamsNode == null || !teamsNode.isArray()) {
            return teams;
        }

        for (JsonNode teamNode : teamsNode) {
            List<StsPvpParticipantDto> team = new ArrayList<>();
            if (teamNode != null && teamNode.isArray()) {
                for (JsonNode participantNode : teamNode) {
                    UUID userId = uuidValue(participantNode.path("userId").asText(null));
                    String bossEnemyId = participantNode.path("bossEnemyId").asText("");
                    String combatantId = participantNode.path("combatantId").asText("");
                    team.add(new StsPvpParticipantDto(
                            userId,
                            // Les batailles ouvertes avant les modes ne portent pas de
                            // combatantId : leur combattant est nomme par l'identifiant
                            // d'utilisateur, ce que ce repli reconstitue a l'identique.
                            combatantId.isBlank()
                                    ? (userId == null ? null : userId.toString())
                                    : combatantId,
                            participantNode.path("displayName").asText("Unknown"),
                            participantNode.path("department").asText(""),
                            participantNode.path("selectedCharacter").asText("EP"),
                            "AI".equalsIgnoreCase(participantNode.path("controller").asText(""))
                                    || !bossEnemyId.isBlank(),
                            bossEnemyId.isBlank() ? null : bossEnemyId
                    ));
                }
            }
            teams.add(team);
        }

        return teams;
    }

    private List<UUID> inactiveParticipants(StsPvpBattle battle) {
        List<UUID> inactive = new ArrayList<>();
        if (battle.getHeartbeat() == null || !battle.getHeartbeat().isObject()) {
            return inactive;
        }

        long now = Instant.now().toEpochMilli();
        ObjectNode heartbeat = (ObjectNode) battle.getHeartbeat();
        heartbeat.fields().forEachRemaining(entry -> {
            long seenAt = entry.getValue().asLong(0L);
            if (seenAt <= 0L || now - seenAt > INACTIVE_TIMEOUT_SECONDS * 1000L) {
                UUID userId = uuidValue(entry.getKey());
                if (userId != null) {
                    inactive.add(userId);
                }
            }
        });

        return inactive;
    }

    private void assertParticipant(AppUser currentUser, StsPvpBattle battle) {
        String userId = currentUser.getId().toString();
        JsonNode teams = battle.getTeams();
        if (teams == null || !teams.isArray()) {
            throw new BadRequestException("Combat PVP invalide.");
        }

        for (JsonNode team : teams) {
            if (team == null || !team.isArray()) {
                continue;
            }
            for (JsonNode participant : team) {
                if (participant != null && userId.equals(participant.path("userId").asText())) {
                    return;
                }
            }
        }

        throw new BadRequestException("Ce joueur ne participe pas à ce combat PVP.");
    }

    /**
     * Ouvre une bataille du format demande avec les joueurs reunis.
     *
     * <p>Les sieges restes vides sont tenus par des renforts que le serveur joue, et le camp d'un
     * raid par son boss. Le tableau {@code teams} porte donc, en plus des joueurs, des
     * participants sans compte : c'est {@code StsPvpCombatService.seats} qui les relit, et c'est
     * la seule place ou la difference se voit.
     */
    private StsPvpBattle createBattle(StsPvpMode mode, List<AppUser> players, boolean friendly) {
        if (players.size() > mode.totalHumanSeats()) {
            throw new BadRequestException("Trop de joueurs pour un combat " + mode.label() + ".");
        }

        StsPvpBattle battle = new StsPvpBattle();
        battle.setFriendly(friendly);
        battle.setMode(mode);
        battle.setStatus(StsPvpBattleStatus.PENDING);

        ArrayNode teams = objectMapper.createArrayNode();
        List<ArrayNode> teamNodes = new ArrayList<>();
        for (int teamIndex = 0; teamIndex < mode.teamCount(); teamIndex++) {
            ArrayNode team = objectMapper.createArrayNode();
            teamNodes.add(team);
            teams.add(team);
        }

        // Les joueurs d'abord, siege par siege : le siege 0 est celui du demandeur.
        List<UUID> deckOwnerByTeam = new ArrayList<>();
        for (int teamIndex = 0; teamIndex < mode.teamCount(); teamIndex++) {
            deckOwnerByTeam.add(null);
        }
        for (int seatIndex = 0; seatIndex < players.size(); seatIndex++) {
            AppUser player = players.get(seatIndex);
            int teamIndex = mode.teamOfHumanSeat(seatIndex);
            StsPvpProfile profile = ensureProfile(player);
            teamNodes.get(teamIndex).add(participantNode(player, profile.getSelectedCharacter()));
            if (deckOwnerByTeam.get(teamIndex) == null) {
                deckOwnerByTeam.set(teamIndex, player.getId());
            }
        }

        // Puis les renforts, sur ce qui reste. Un renfort emprunte le deck d'un joueur de sa
        // propre equipe quand il y en a un, et sinon celui du demandeur : il lui faut un deck
        // legal, et c'est le seul qui soit sur d'exister.
        for (int seatIndex = players.size(); seatIndex < mode.totalHumanSeats(); seatIndex++) {
            int teamIndex = mode.teamOfHumanSeat(seatIndex);
            UUID deckOwner = deckOwnerByTeam.get(teamIndex) != null
                    ? deckOwnerByTeam.get(teamIndex)
                    : players.get(0).getId();
            StsPvpProfile ownerProfile = ensureProfile(
                    players.stream()
                            .filter(player -> player.getId().equals(deckOwner))
                            .findFirst()
                            .orElse(players.get(0)));
            teamNodes.get(teamIndex).add(aiAllyNode(
                    "ai-ally-" + teamIndex + "-" + seatIndex,
                    "Renfort " + (seatIndex + 1),
                    ownerProfile.getSelectedCharacter(),
                    deckOwner));
        }

        if (mode.hasBoss()) {
            // Autant de boss que la rencontre tirée nomme d'ennemis, un combattant chacun : un
            // même ennemi nommé deux fois donne deux combattants distincts.
            List<RaidBoss> encounter = raidEncounter();
            for (int bossIndex = 0; bossIndex < encounter.size(); bossIndex++) {
                RaidBoss boss = encounter.get(bossIndex);
                teamNodes.get(mode.bossTeamIndex()).add(bossNode(
                        "raid-boss-" + bossIndex, boss.enemyId(), boss.displayName(), boss.hpMultiplier()));
            }
        }
        battle.setTeams(teams);

        // A placeholder only: the combat's own state replaces it as soon as the battle has an id.
        battle.setState(objectMapper.createObjectNode());

        ObjectNode heartbeat = objectMapper.createObjectNode();
        long now = TimeTravelContext.instantNow().toEpochMilli();
        players.forEach(player -> heartbeat.put(player.getId().toString(), now));
        battle.setHeartbeat(heartbeat);
        battle.setCardHistory(objectMapper.createArrayNode());
        battle.setRewards(objectMapper.createObjectNode());

        battle = battleRepository.save(battle);
        // The combat is identified by the battle's id, so it can only be built once saved.
        combatService.startCombat(battle);
        return battleRepository.save(battle);
    }

    private ObjectNode participantNode(AppUser user, String selectedCharacter) {
        ObjectNode participant = objectMapper.createObjectNode();
        participant.put("userId", user.getId().toString());
        participant.put("combatantId", user.getId().toString());
        participant.put("displayName", displayName(user));
        participant.put("department", departmentShortName(user.getDepartment()));
        participant.put("selectedCharacter",
                selectedCharacter == null || selectedCharacter.isBlank() ? "EP" : selectedCharacter);
        participant.put("controller", "HUMAN");
        return participant;
    }

    private ObjectNode aiAllyNode(
            String combatantId, String displayName, String selectedCharacter, UUID deckOwnerUserId) {
        ObjectNode participant = objectMapper.createObjectNode();
        participant.putNull("userId");
        participant.put("combatantId", combatantId);
        participant.put("displayName", displayName);
        participant.put("selectedCharacter",
                selectedCharacter == null || selectedCharacter.isBlank() ? "EP" : selectedCharacter);
        participant.put("controller", "AI");
        participant.put("deckOwnerUserId", deckOwnerUserId.toString());
        return participant;
    }

    /**
     * Un ennemi d'une rencontre de raid, le nom sous lequel les joueurs le voient, et l'échelle de
     * points de vie que sa rencontre lui applique.
     */
    private record RaidBoss(String enemyId, String displayName, double hpMultiplier) {
    }

    /**
     * La rencontre que ce raid oppose aux joueurs, tirée au hasard dans
     * {@code gameplay/pvp_raid_encounters.json}.
     *
     * <p>Seules les rencontres dont chaque ennemi existe dans les données de jeu sont tirables : un
     * ennemi inconnu ne se découvrirait qu'à l'ouverture du combat, une fois les joueurs sortis de la
     * file, et le raid échouerait alors pour tous. Sans rencontre jouable — fichier absent, vide, ou
     * n'en nommant que d'invalides — le raid garde son boss unique d'avant.
     */
    private List<RaidBoss> raidEncounter() {
        String version = gameDataService.latestVersion();
        Map<String, String> displayNames = new HashMap<>();
        for (JsonNode enemy : gameDataService.enemies(version)) {
            String displayName = enemy.path("displayName").asText("").trim();
            for (String key : List.of(enemy.path("id").asText("").trim(),
                    enemy.path("enemyName").asText("").trim())) {
                if (!key.isEmpty()) {
                    displayNames.putIfAbsent(key, displayName.isEmpty() ? key : displayName);
                }
            }
        }

        StsPvpRaidEncounters encounters =
                StsPvpRaidEncounters.from(gameDataService.pvpRaidEncounters(version));
        int unplayable = encounters.encounters().size()
                - encounters.playable(displayNames::containsKey).size();
        if (unplayable > 0) {
            LOGGER.warn("{} raid encounter(s) in gameplay/pvp_raid_encounters.json (data version {})"
                    + " name an enemy that does not exist and will never be drawn.", unplayable, version);
        }

        StsPvpRaidEncounters.Encounter encounter = encounters
                .pick(raidEncounterRandom, displayNames::containsKey)
                .orElseGet(() -> new StsPvpRaidEncounters.Encounter(List.of(raidBossEnemyId), 1d));
        return encounter.enemies().stream()
                .map(id -> new RaidBoss(id, displayNames.getOrDefault(id, id), encounter.hpMultiplier()))
                .toList();
    }

    private ObjectNode bossNode(String combatantId, String bossEnemyId, String displayName, double hpMultiplier) {
        ObjectNode participant = objectMapper.createObjectNode();
        participant.putNull("userId");
        participant.put("combatantId", combatantId);
        participant.put("displayName", displayName);
        participant.put("controller", "AI");
        participant.put("bossEnemyId", bossEnemyId);
        participant.put("hpMultiplier", hpMultiplier);
        return participant;
    }

    private void createNotification(
            AppUser recipient,
            AppUser actor,
            StsPvpNotificationType type,
            String title,
            String body,
            JsonNode payload
    ) {
        StsPvpNotification notification = new StsPvpNotification();
        notification.setRecipient(recipient);
        notification.setActor(actor);
        notification.setType(type);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setPayload(payload == null ? objectMapper.createObjectNode() : payload);
        notificationRepository.save(notification);
    }

    /**
     * Toutes les cartes comptent-elles comme obtenues pour construire un deck ?
     *
     * Interrupteur de circonstance : il ne donne aucune carte à personne, la
     * collection reste ce qu'elle est. Il lève seulement la condition de
     * possession, le temps qu'il est levé.
     */
    @Transactional(readOnly = true)
    public boolean allCardsUnlocked() {
        return collectionTestMode.allCardsOwned();
    }

    /**
     * Ouvre ou referme l'accès à toutes les cartes pour la construction des decks.
     *
     * Aucune collection n'est modifiée : refermer suffit à revenir à l'état
     * d'avant, et les decks déjà enregistrés avec des cartes non possédées sont
     * alors refusés à la prochaine validation — ce qui est le comportement voulu,
     * un deck ne devant contenir que des cartes qu'on a.
     */
    /**
     * Les cartes utilisables pour construire un deck.
     *
     * Distinct de ce qu'on possède : l'interrupteur ouvre le catalogue à la
     * construction sans rien donner. Les confondre ferait croire à
     * {@code grantRunEndUnlocks} que tout est déjà acquis, et les récompenses de
     * fin de run cesseraient en silence tant que l'interrupteur est levé.
     */
    private Set<String> deckBuildableCardIds(String keycloakSub) {
        return collectionCardIds(cardService.getDeck(keycloakSub, CardCollectionType.VIRTUAL));
    }

    /**
     * Ce que le joueur possède vraiment, mode de test ignoré.
     *
     * L'attribution de fin de run s'en sert pour savoir quoi ne pas redonner :
     * lui servir la collection ouverte lui ferait croire que tout est acquis, et
     * elle ne donnerait plus rien tant que le mode est levé.
     */
    private Set<String> ownedVirtualCardIds(String keycloakSub) {
        return collectionCardIds(cardService.getOwnedDeck(keycloakSub, CardCollectionType.VIRTUAL));
    }

    private Set<String> collectionCardIds(CardDeckDto deckDto) {
        Set<String> ids = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        if (deckDto.categories() == null) {
            return ids;
        }

        for (CardDeckCategoryDto category : deckDto.categories()) {
            if (category.cards() == null) {
                continue;
            }
            for (CardDeckCardDto card : category.cards()) {
                if (card.quantity() <= 0) {
                    continue;
                }
                if (card.id() != null) {
                    ids.add(card.id().toString());
                }
                // STS cards reference the collection by card name, not by collection card UUID.
                if (card.name() != null && !card.name().isBlank()) {
                    ids.add(card.name().trim());
                }
            }
        }

        return ids;
    }

    private Set<String> unlockedMultiplayerCardIds(String selectedCharacter, Map<String, Integer> characterLevels) {
        JsonNode cardsRoot = gameDataService.cards(gameDataService.latestVersion());
        JsonNode cards = cardsRoot.path("cards");
        Set<String> unlocked = new HashSet<>();

        int level = characterLevels.getOrDefault(selectedCharacter, 0);

        if (cards == null || !cards.isArray()) {
            return unlocked;
        }

        for (JsonNode card : cards) {
            if (card == null || card.isNull()) {
                continue;
            }

            boolean multiplayerExclusive = card.path("multiplayerExclusive").asBoolean(false);
            if (!multiplayerExclusive) {
                continue;
            }

            int requiredLevel = card.path("characterLevel").asInt(0);
            if (level < requiredLevel) {
                continue;
            }

            String cardId = textOrNull(card, "id");
            if (cardId != null) {
                unlocked.add(cardId);
            }
        }

        return unlocked;
    }

    private void validateDeckCards(AppUser currentUser, String selectedCharacter, List<String> cardIds, StsPvpProfile profile) {
        Set<String> owned = new HashSet<>(deckBuildableCardIds(currentUser.getKeycloakSub()));
        owned.addAll(unlockedStsCardKeys(profile));

        Map<String, JsonNode> cardsById = stsCardsById();

        for (String cardId : cardIds) {
            JsonNode card = cardsById.get(cardId);
            if (card == null) {
                continue;
            }

            // Le pendant de multiplayerExclusive, dans l'autre sens : une carte de campagne n'a
            // pas sa place dans un deck de duel. Le client ne la montre déjà pas, mais un deck
            // arrive par une requête et non par un écran, et c'est ici que cela se tranche.
            if (card.path("pveOnly").asBoolean(false)) {
                throw new BadRequestException(
                        "Le deck contient une carte réservée à la campagne : " + cardId);
            }

            // Every non-campaign card is locked until earned through a run's end (retreat or
            // game over): the base pool and multiplayer-exclusive cards no longer get a free
            // pass, and character level is not consulted here.
            String unlockKey = cardUnlockKey(card);
            if (unlockKey == null || !owned.contains(unlockKey)) {
                throw new BadRequestException("Le deck contient une carte non débloquée : " + cardId);
            }
        }
    }

    /** The smallest a PvP deck may be, from {@code gameplay/pvp_deck_rules.json} when it sets one. */
    private int minDeckSize() {
        JsonNode rules = deckRulesNode();
        int configured = rules == null ? DEFAULT_MIN_DECK_SIZE : rules.path("minDeckSize").asInt(DEFAULT_MIN_DECK_SIZE);
        return configured > 0 ? configured : DEFAULT_MIN_DECK_SIZE;
    }

    /** The largest a PvP deck may be, from {@code gameplay/pvp_deck_rules.json} when it sets one. */
    private int maxDeckSize() {
        JsonNode rules = deckRulesNode();
        int configured = rules == null ? DEFAULT_MAX_DECK_SIZE : rules.path("maxDeckSize").asInt(DEFAULT_MAX_DECK_SIZE);
        return configured > 0 ? configured : DEFAULT_MAX_DECK_SIZE;
    }

    private JsonNode deckRulesNode() {
        return gameDataService.pvpDeckRules(gameDataService.latestVersion());
    }

    private Map<String, JsonNode> stsCardsById() {
        return stsCardsById(gameDataService.latestVersion());
    }

    private Map<String, JsonNode> stsCardsById(String dataVersion) {
        JsonNode cardsRoot = gameDataService.cards(dataVersion);
        JsonNode cards = cardsRoot.path("cards");
        Map<String, JsonNode> index = new LinkedHashMap<>();

        if (cards == null || !cards.isArray()) {
            return index;
        }

        for (JsonNode card : cards) {
            String id = textOrNull(card, "id");
            if (id != null) {
                index.put(id, card);
            }

            String name = textOrNull(card, "cardName");
            if (name != null && !index.containsKey(name)) {
                index.put(name, card);
            }
        }

        return index;
    }

    private List<String> normalizeDeckCardIds(List<String> cardIds) {
        if (cardIds == null || cardIds.isEmpty()) {
            throw new BadRequestException("Le deck doit contenir des cartes.");
        }

        List<String> normalized = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (String cardId : cardIds) {
            if (cardId == null || cardId.isBlank()) {
                continue;
            }

            String clean = cardId.trim();
            if (seen.add(clean)) {
                normalized.add(clean);
            }
        }

        return normalized;
    }

    private void deactivateCurrentDecks(String keycloakSub, String selectedCharacter) {
        List<StsPvpDeck> activeDecks = deckRepository
                .findByOwner_KeycloakSubAndSelectedCharacterOrderByUpdatedAtDesc(keycloakSub, selectedCharacter)
                .stream()
                .filter(deck -> Boolean.TRUE.equals(deck.getActive()))
                .toList();

        for (StsPvpDeck activeDeck : activeDecks) {
            activeDeck.setActive(false);
            deckRepository.save(activeDeck);
        }
    }

    private Map<String, Integer> characterLevels(StsPvpProfile profile) {
        Map<String, Integer> levels = new HashMap<>();

        if (profile.getCharacterExp() != null && profile.getCharacterExp().isObject()) {
            profile.getCharacterExp().fields().forEachRemaining(entry -> levels.put(entry.getKey(), entry.getValue().asInt(0)));
        }

        if (levels.isEmpty()) {
            JsonNode characters = gameDataService.characters(gameDataService.latestVersion());
            if (characters != null && characters.isArray()) {
                for (JsonNode character : characters) {
                    String characterId = character.path("characterId").asText(null);
                    if (characterId != null && !characterId.isBlank()) {
                        levels.putIfAbsent(characterId, 0);
                    }
                }
            }
        }

        return levels;
    }

    private List<String> extractStringArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item != null && item.isTextual()) {
                values.add(item.asText());
            }
        }
        return values;
    }

    private void validateCharacter(String characterId) {
        gameDataService.character(gameDataService.latestVersion(), characterId.trim());
    }

    private UUID uuidValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String textOrNull(JsonNode node, String key) {
        if (node == null || key == null) {
            return null;
        }

        String value = node.path(key).asText(null);
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private String displayName(AppUser user) {
        if (user == null) {
            return "Unknown";
        }

        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName();
        }

        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail();
        }

        return user.getId().toString();
    }

    private String departmentShortName(UUID departmentId) {
        if (departmentId == null) {
            return "";
        }

        Optional<Department> department = departmentRepository.findById(departmentId);
        return department.map(Department::getShortName).orElse("");
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}
