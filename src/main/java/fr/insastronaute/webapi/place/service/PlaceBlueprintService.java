package fr.insastronaute.webapi.place.service;

import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.ForbiddenException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.friend.repository.FriendshipRepository;
import fr.insastronaute.webapi.place.domain.PlaceBlueprint;
import fr.insastronaute.webapi.place.domain.PlaceBlueprintShare;
import fr.insastronaute.webapi.place.dto.PlaceBlueprintDto;
import fr.insastronaute.webapi.place.dto.PlaceBlueprintSaveDto;
import fr.insastronaute.webapi.place.repository.PlaceBlueprintRepository;
import fr.insastronaute.webapi.place.repository.PlaceBlueprintShareRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Les plans de fresque : dessiner a cote, partager, puis poser.
 *
 * <p>Poser un pixel coute un delai, et une fresque a plusieurs mains se decide
 * avant de se poser. Ce travail se faisait ailleurs et se traduisait case par
 * case, de tete, au moment de poser.</p>
 */
@Service
public class PlaceBlueprintService {

    /**
     * Le nombre de plans qu'un joueur peut garder.
     *
     * <p>Assez pour un projet en plusieurs morceaux et ses brouillons ; pas assez
     * pour qu'un script remplisse la table. Chaque plan pese jusqu'a seize
     * kilo-octets et repart entier a chaque ouverture de la page.</p>
     */
    static final int MAX_BLUEPRINTS_PER_PLAYER = 30;

    private final PlaceBlueprintRepository blueprintRepository;
    private final PlaceBlueprintShareRepository shareRepository;
    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;

    public PlaceBlueprintService(
            PlaceBlueprintRepository blueprintRepository,
            PlaceBlueprintShareRepository shareRepository,
            UserRepository userRepository,
            FriendshipRepository friendshipRepository
    ) {
        this.blueprintRepository = blueprintRepository;
        this.shareRepository = shareRepository;
        this.userRepository = userRepository;
        this.friendshipRepository = friendshipRepository;
    }

    /** Les plans qu'on a faits, puis ceux qu'on a recus. */
    @Transactional(readOnly = true)
    public List<PlaceBlueprintDto> listVisible(String keycloakSub) {
        AppUser user = requireUser(keycloakSub);
        List<PlaceBlueprintDto> visible = new ArrayList<>(
                blueprintRepository.findByOwnerIdOrderByUpdatedAtDesc(user.getId()).stream()
                        .map(blueprint -> toDto(blueprint, true))
                        .toList());
        blueprintRepository.findSharedWith(user.getId()).stream()
                .map(blueprint -> toDto(blueprint, false))
                .forEach(visible::add);
        return visible;
    }

    @Transactional
    public PlaceBlueprintDto create(String keycloakSub, PlaceBlueprintSaveDto dto) {
        AppUser owner = requireUser(keycloakSub);
        if (blueprintRepository.countByOwnerId(owner.getId()) >= MAX_BLUEPRINTS_PER_PLAYER) {
            throw new BadRequestException(
                    "Tu as atteint la limite de " + MAX_BLUEPRINTS_PER_PLAYER + " plans. Supprimes-en un pour en créer un autre.");
        }

        PlaceBlueprint blueprint = new PlaceBlueprint();
        blueprint.setOwner(owner);
        apply(blueprint, dto);
        return toDto(blueprintRepository.save(blueprint), true);
    }

    @Transactional
    public PlaceBlueprintDto update(String keycloakSub, UUID blueprintId, PlaceBlueprintSaveDto dto) {
        AppUser user = requireUser(keycloakSub);
        PlaceBlueprint blueprint = requireBlueprint(blueprintId);
        // Un plan recu se regarde et se pose, il ne se recrit pas : celui qui l'a
        // partage ne s'attend pas a ce que son dessin change sous ses yeux.
        requireOwner(blueprint, user, "Seul l'auteur d'un plan peut le modifier.");

        apply(blueprint, dto);
        return toDto(blueprintRepository.save(blueprint), true);
    }

    /**
     * Supprimer un plan qu'on a fait, ou retirer de chez soi un plan recu.
     *
     * <p>Le meme geste pour le joueur — « je ne veux plus le voir » — et deux
     * effets differents : l'auteur efface le plan pour tout le monde, celui qui
     * l'a recu ne retire que son propre acces.</p>
     */
    @Transactional
    public void delete(String keycloakSub, UUID blueprintId) {
        AppUser user = requireUser(keycloakSub);
        PlaceBlueprint blueprint = requireBlueprint(blueprintId);

        if (blueprint.getOwner().getId().equals(user.getId())) {
            shareRepository.deleteAll(shareRepository.findByBlueprintId(blueprintId));
            blueprintRepository.delete(blueprint);
            return;
        }

        if (!shareRepository.existsByBlueprintIdAndUserId(blueprintId, user.getId())) {
            throw new NotFoundException("Plan introuvable.");
        }
        shareRepository.deleteByBlueprintIdAndUserId(blueprintId, user.getId());
    }

    /**
     * Partager un plan avec des amis.
     *
     * <p>Seulement des amis : un plan porte le travail d'un groupe et le nom de
     * son auteur, il n'a pas a s'envoyer a un identifiant trouve au hasard.</p>
     */
    @Transactional
    public PlaceBlueprintDto share(String keycloakSub, UUID blueprintId, List<UUID> userIds) {
        AppUser owner = requireUser(keycloakSub);
        PlaceBlueprint blueprint = requireBlueprint(blueprintId);
        requireOwner(blueprint, owner, "Seul l'auteur d'un plan peut le partager.");

        LinkedHashSet<UUID> targets = userIds.stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        targets.remove(owner.getId());
        if (targets.isEmpty()) {
            throw new BadRequestException("Choisis au moins un ami avec qui partager ce plan.");
        }

        Set<UUID> friends = Set.copyOf(friendshipRepository.findAcceptedFriendIds(owner.getId()));
        if (!friends.containsAll(targets)) {
            throw new BadRequestException("Un plan ne se partage qu'avec des amis.");
        }

        for (AppUser target : userRepository.findAllById(targets)) {
            // Repartager un plan deja partage ne doit rien casser : le geste se
            // refait souvent, apres avoir ajoute quelqu'un au groupe.
            if (shareRepository.existsByBlueprintIdAndUserId(blueprintId, target.getId())) {
                continue;
            }
            PlaceBlueprintShare share = new PlaceBlueprintShare();
            share.setBlueprint(blueprint);
            share.setUser(target);
            share.setSharedBy(owner);
            shareRepository.save(share);
        }
        return toDto(blueprint, true);
    }

    /**
     * Fixer ou le plan se pose sur la fresque.
     *
     * <p>La position suit le plan chez tous ceux avec qui il est partage, et la
     * suit en direct : si l'auteur deplace le plan, le groupe le retrouve au
     * nouvel endroit. C'est pourquoi seul l'auteur la fixe — un ami qui deplace
     * le plan chez lui ne le deplace que pour lui.</p>
     */
    @Transactional
    public PlaceBlueprintDto setAnchor(String keycloakSub, UUID blueprintId, int x, int y) {
        AppUser user = requireUser(keycloakSub);
        PlaceBlueprint blueprint = requireBlueprint(blueprintId);
        requireOwner(blueprint, user, "Seul l'auteur d'un plan peut choisir où il se pose.");

        blueprint.setAnchor(x, y);
        return toDto(blueprintRepository.save(blueprint), true);
    }

    private void apply(PlaceBlueprint blueprint, PlaceBlueprintSaveDto dto) {
        List<String> palette = dto.palette().stream()
                .map(color -> color == null ? null : color.trim().toUpperCase())
                .toList();
        BlueprintCells.validate(dto.width(), dto.height(), palette, dto.cells());

        blueprint.setName(dto.name().trim());
        blueprint.setWidth(dto.width());
        blueprint.setHeight(dto.height());
        blueprint.setPalette(BlueprintCells.joinPalette(palette));
        blueprint.setCells(dto.cells());
    }

    private PlaceBlueprintDto toDto(PlaceBlueprint blueprint, boolean mine) {
        return new PlaceBlueprintDto(
                blueprint.getId(),
                blueprint.getName(),
                blueprint.getWidth(),
                blueprint.getHeight(),
                BlueprintCells.splitPalette(blueprint.getPalette()),
                blueprint.getCells(),
                BlueprintCells.filledCells(blueprint.getCells()),
                mine,
                blueprint.getOwner().getDisplayName(),
                blueprint.getAnchorX(),
                blueprint.getAnchorY(),
                blueprint.getUpdatedAt()
        );
    }

    private static void requireOwner(PlaceBlueprint blueprint, AppUser user, String message) {
        if (!blueprint.getOwner().getId().equals(user.getId())) {
            throw new ForbiddenException(message);
        }
    }

    private AppUser requireUser(String keycloakSub) {
        return userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
    }

    private PlaceBlueprint requireBlueprint(UUID blueprintId) {
        return blueprintRepository.findById(blueprintId)
                .orElseThrow(() -> new NotFoundException("Plan introuvable."));
    }
}
