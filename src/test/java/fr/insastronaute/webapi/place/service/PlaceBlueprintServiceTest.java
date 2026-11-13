package fr.insastronaute.webapi.place.service;

import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.ForbiddenException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.friend.repository.FriendshipRepository;
import fr.insastronaute.webapi.place.domain.PlaceBlueprint;
import fr.insastronaute.webapi.place.domain.PlaceBlueprintShare;
import fr.insastronaute.webapi.place.dto.PlaceBlueprintSaveDto;
import fr.insastronaute.webapi.place.repository.PlaceBlueprintRepository;
import fr.insastronaute.webapi.place.repository.PlaceBlueprintShareRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaceBlueprintServiceTest {

    private final PlaceBlueprintRepository blueprintRepository = mock(PlaceBlueprintRepository.class);
    private final PlaceBlueprintShareRepository shareRepository = mock(PlaceBlueprintShareRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final FriendshipRepository friendshipRepository = mock(FriendshipRepository.class);

    private PlaceBlueprintService service;
    private AppUser owner;

    @BeforeEach
    void setUp() {
        service = new PlaceBlueprintService(blueprintRepository, shareRepository, userRepository, friendshipRepository);
        owner = user(UUID.randomUUID());
        when(userRepository.findByKeycloakSub("owner-sub")).thenReturn(Optional.of(owner));
    }

    /** Deux cases dessinées, deux vides : « 0 » est le vide, pas une couleur. */
    private static PlaceBlueprintSaveDto square() {
        return new PlaceBlueprintSaveDto("Cœur", 2, 2, List.of("#FF0000"), "1001");
    }

    @Test
    void aBlueprintKeepsItsDrawingAndCountsWhatIsLeftToPlace() {
        when(blueprintRepository.countByOwnerId(owner.getId())).thenReturn(0L);
        when(blueprintRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var created = service.create("owner-sub", square());

        assertThat(created.cells()).isEqualTo("1001");
        assertThat(created.palette()).containsExactly("#FF0000");
        // Ce chiffre est ce que la page annonce : « il reste 2 pixels à poser ».
        assertThat(created.filledCells()).isEqualTo(2);
        assertThat(created.mine()).isTrue();
    }

    /**
     * Un plan mal formé n'a pas de moitié acceptable : rendu tel quel à
     * l'application, il donnerait des cases hors palette — donc des couleurs
     * inventées à poser sur une fresque commune.
     */
    @Test
    void aBlueprintThatDoesNotDescribeItsCellsIsRefused() {
        assertThatThrownBy(() -> service.create("owner-sub",
                new PlaceBlueprintSaveDto("Bancal", 2, 2, List.of("#FF0000"), "100")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Le plan ne décrit pas exactement ses cases.");

        verify(blueprintRepository, never()).save(any());
    }

    @Test
    void aBlueprintPointingAtAColorItDoesNotDeclareIsRefused() {
        assertThatThrownBy(() -> service.create("owner-sub",
                new PlaceBlueprintSaveDto("Bancal", 2, 2, List.of("#FF0000"), "1201")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Le plan vise une couleur qu'il ne déclare pas.");
    }

    @Test
    void aBlueprintCannotGrowBeyondWhatTheCanvasCouldHold() {
        assertThatThrownBy(() -> service.create("owner-sub",
                new PlaceBlueprintSaveDto("Immense", 200, 1, List.of(), "0".repeat(200))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Un plan ne peut pas dépasser 128 cases de côté.");
    }

    /** Un plan reçu se regarde et se pose ; celui qui l'a partagé ne s'attend pas à ce qu'il change. */
    @Test
    void onlyTheAuthorCanRewriteABlueprint() {
        UUID blueprintId = UUID.randomUUID();
        AppUser other = user(UUID.randomUUID());
        when(userRepository.findByKeycloakSub("other-sub")).thenReturn(Optional.of(other));
        when(blueprintRepository.findById(blueprintId)).thenReturn(Optional.of(blueprint(blueprintId, owner)));

        assertThatThrownBy(() -> service.update("other-sub", blueprintId, square()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Seul l'auteur d'un plan peut le modifier.");
    }

    /** Un plan porte le travail d'un groupe et le nom de son auteur : il ne s'envoie pas à un inconnu. */
    @Test
    void aBlueprintOnlyGoesToFriends() {
        UUID blueprintId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        when(blueprintRepository.findById(blueprintId)).thenReturn(Optional.of(blueprint(blueprintId, owner)));
        when(friendshipRepository.findAcceptedFriendIds(owner.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> service.share("owner-sub", blueprintId, List.of(strangerId)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Un plan ne se partage qu'avec des amis.");

        verify(shareRepository, never()).save(any());
    }

    /** Le geste se refait souvent, après avoir ajouté quelqu'un au groupe. */
    @Test
    void sharingTwiceWithTheSameFriendChangesNothing() {
        UUID blueprintId = UUID.randomUUID();
        AppUser friend = user(UUID.randomUUID());
        when(blueprintRepository.findById(blueprintId)).thenReturn(Optional.of(blueprint(blueprintId, owner)));
        when(friendshipRepository.findAcceptedFriendIds(owner.getId())).thenReturn(List.of(friend.getId()));
        when(userRepository.findAllById(any())).thenReturn(List.of(friend));
        when(shareRepository.existsByBlueprintIdAndUserId(blueprintId, friend.getId())).thenReturn(true);

        service.share("owner-sub", blueprintId, List.of(friend.getId()));

        verify(shareRepository, never()).save(any());
    }

    /**
     * Le même geste côté joueur — « je ne veux plus le voir » —, deux effets :
     * l'auteur efface le plan pour tout le monde, celui qui l'a reçu ne retire
     * que son propre accès.
     */
    @Test
    void aRecipientDroppingABlueprintDoesNotDeleteItForTheAuthor() {
        UUID blueprintId = UUID.randomUUID();
        AppUser friend = user(UUID.randomUUID());
        when(userRepository.findByKeycloakSub("friend-sub")).thenReturn(Optional.of(friend));
        when(blueprintRepository.findById(blueprintId)).thenReturn(Optional.of(blueprint(blueprintId, owner)));
        when(shareRepository.existsByBlueprintIdAndUserId(blueprintId, friend.getId())).thenReturn(true);

        service.delete("friend-sub", blueprintId);

        verify(shareRepository).deleteByBlueprintIdAndUserId(blueprintId, friend.getId());
        verify(blueprintRepository, never()).delete(any());
    }

    @Test
    void theAuthorDeletingABlueprintTakesItsSharesWithIt() {
        UUID blueprintId = UUID.randomUUID();
        PlaceBlueprint blueprint = blueprint(blueprintId, owner);
        when(blueprintRepository.findById(blueprintId)).thenReturn(Optional.of(blueprint));
        when(shareRepository.findByBlueprintId(blueprintId)).thenReturn(List.of(new PlaceBlueprintShare()));

        service.delete("owner-sub", blueprintId);

        verify(shareRepository).deleteAll(any());
        verify(blueprintRepository).delete(blueprint);
    }

    /** Un plan qu'on n'a ni fait ni reçu n'existe pas pour ce joueur. */
    @Test
    void aStrangerCannotDropABlueprintTheyNeverReceived() {
        UUID blueprintId = UUID.randomUUID();
        AppUser stranger = user(UUID.randomUUID());
        when(userRepository.findByKeycloakSub("stranger-sub")).thenReturn(Optional.of(stranger));
        when(blueprintRepository.findById(blueprintId)).thenReturn(Optional.of(blueprint(blueprintId, owner)));
        when(shareRepository.existsByBlueprintIdAndUserId(blueprintId, stranger.getId())).thenReturn(false);

        assertThatThrownBy(() -> service.delete("stranger-sub", blueprintId))
                .isInstanceOf(NotFoundException.class);
    }

    /** Chaque plan repart entier à chaque ouverture de la page : leur nombre est borné. */
    @Test
    void aPlayerCannotHoardBlueprints() {
        when(blueprintRepository.countByOwnerId(owner.getId()))
                .thenReturn((long) PlaceBlueprintService.MAX_BLUEPRINTS_PER_PLAYER);

        assertThatThrownBy(() -> service.create("owner-sub", square()))
                .isInstanceOf(BadRequestException.class);

        verify(blueprintRepository, never()).save(any());
    }

    @Test
    void theListShowsWhatIsMineFirstThenWhatWasSharedWithMe() {
        PlaceBlueprint mine = blueprint(UUID.randomUUID(), owner);
        mine.setName("Le mien");
        AppUser friend = user(UUID.randomUUID());
        friend.setDisplayName("Chloé");
        PlaceBlueprint received = blueprint(UUID.randomUUID(), friend);
        received.setName("Le sien");

        when(blueprintRepository.findByOwnerIdOrderByUpdatedAtDesc(owner.getId())).thenReturn(List.of(mine));
        when(blueprintRepository.findSharedWith(owner.getId())).thenReturn(List.of(received));

        var visible = service.listVisible("owner-sub");

        assertThat(visible).hasSize(2);
        assertThat(visible.get(0).mine()).isTrue();
        assertThat(visible.get(1).mine()).isFalse();
        // « Partagé par Chloé » est ce qui distingue un plan reçu d'un plan qu'on a fait.
        assertThat(visible.get(1).ownerName()).isEqualTo("Chloé");
        verify(blueprintRepository, times(1)).findSharedWith(owner.getId());
    }

    /**
     * Une fresque de groupe ne tient que si tout le monde pose au même endroit :
     * le plan reçu doit arriver à la place où son auteur l'a mis.
     */
    @Test
    void theOwnersPositionTravelsWithTheSharedBlueprint() {
        UUID blueprintId = UUID.randomUUID();
        PlaceBlueprint blueprint = blueprint(blueprintId, owner);
        when(blueprintRepository.findById(blueprintId)).thenReturn(Optional.of(blueprint));
        when(blueprintRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var anchored = service.setAnchor("owner-sub", blueprintId, 12, 34);
        assertThat(anchored.anchorX()).isEqualTo(12);
        assertThat(anchored.anchorY()).isEqualTo(34);

        AppUser friend = user(UUID.randomUUID());
        when(userRepository.findByKeycloakSub("friend-sub")).thenReturn(Optional.of(friend));
        when(blueprintRepository.findSharedWith(friend.getId())).thenReturn(List.of(blueprint));

        var received = service.listVisible("friend-sub").getFirst();
        assertThat(received.mine()).isFalse();
        assertThat(received.anchorX()).isEqualTo(12);
        assertThat(received.anchorY()).isEqualTo(34);
    }

    /** Un ami qui déplace le plan chez lui ne le déplace que pour lui. */
    @Test
    void onlyTheAuthorSetsTheSharedPosition() {
        UUID blueprintId = UUID.randomUUID();
        AppUser friend = user(UUID.randomUUID());
        when(userRepository.findByKeycloakSub("friend-sub")).thenReturn(Optional.of(friend));
        when(blueprintRepository.findById(blueprintId)).thenReturn(Optional.of(blueprint(blueprintId, owner)));

        assertThatThrownBy(() -> service.setAnchor("friend-sub", blueprintId, 1, 1))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Seul l'auteur d'un plan peut choisir où il se pose.");

        verify(blueprintRepository, never()).save(any());
    }

    /** Pas de position à partager tant que l'auteur n'a pas posé le plan. */
    @Test
    void aBlueprintNeverPlacedHasNoPosition() {
        when(blueprintRepository.findByOwnerIdOrderByUpdatedAtDesc(owner.getId()))
                .thenReturn(List.of(blueprint(UUID.randomUUID(), owner)));

        var listed = service.listVisible("owner-sub").getFirst();
        assertThat(listed.anchorX()).isNull();
        assertThat(listed.anchorY()).isNull();
    }

    /** Modifier le dessin ne doit pas renvoyer le plan au coin du canevas. */
    @Test
    void editingTheDrawingKeepsItsPosition() {
        UUID blueprintId = UUID.randomUUID();
        PlaceBlueprint blueprint = blueprint(blueprintId, owner);
        blueprint.setAnchor(40, 50);
        when(blueprintRepository.findById(blueprintId)).thenReturn(Optional.of(blueprint));
        when(blueprintRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var updated = service.update("owner-sub", blueprintId, square());

        assertThat(updated.anchorX()).isEqualTo(40);
        assertThat(updated.anchorY()).isEqualTo(50);
    }

    private static AppUser user(UUID id) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setDisplayName("Joueur");
        return user;
    }

    private static PlaceBlueprint blueprint(UUID id, AppUser owner) {
        PlaceBlueprint blueprint = new PlaceBlueprint();
        blueprint.setOwner(owner);
        blueprint.setName("Plan");
        blueprint.setWidth(2);
        blueprint.setHeight(2);
        blueprint.setPalette("#FF0000");
        blueprint.setCells("1001");
        try {
            var field = PlaceBlueprint.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(blueprint, id);
        } catch (ReflectiveOperationException unreachable) {
            throw new IllegalStateException(unreachable);
        }
        return blueprint;
    }
}
