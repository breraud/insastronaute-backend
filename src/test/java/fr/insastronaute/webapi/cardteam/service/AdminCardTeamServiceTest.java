package fr.insastronaute.webapi.cardteam.service;

import fr.insastronaute.webapi.card.service.CardService;
import fr.insastronaute.webapi.cardteam.domain.CardTeam;
import fr.insastronaute.webapi.cardteam.domain.CardTeamJoinRequest;
import fr.insastronaute.webapi.cardteam.domain.CardTeamMember;
import fr.insastronaute.webapi.cardteam.domain.CardTeamRequestStatus;
import fr.insastronaute.webapi.cardteam.dto.AdminCardTeamDto;
import fr.insastronaute.webapi.cardteam.repository.CardTeamInvitationRepository;
import fr.insastronaute.webapi.cardteam.repository.CardTeamJoinRequestRepository;
import fr.insastronaute.webapi.cardteam.repository.CardTeamMemberRepository;
import fr.insastronaute.webapi.cardteam.repository.CardTeamRepository;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.CodedConflictException;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.common.web.PageResponse;
import fr.insastronaute.webapi.friend.domain.Friendship;
import fr.insastronaute.webapi.friend.repository.FriendshipRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminCardTeamServiceTest {

    @Mock CardTeamRepository teamRepository;
    @Mock CardTeamMemberRepository memberRepository;
    @Mock CardTeamJoinRequestRepository joinRequestRepository;
    @Mock CardTeamInvitationRepository invitationRepository;
    @Mock FriendshipRepository friendshipRepository;
    @Mock UserRepository userRepository;
    @Mock CardService cardService;
    @Mock CardTeamPopulationPolicy populationPolicy;

    private AdminCardTeamService service;

    private static AppUser user(String name) {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setDisplayName(name);
        return user;
    }

    private static CardTeam team(String name) {
        CardTeam team = new CardTeam();
        team.setId(UUID.randomUUID());
        team.setName(name);
        // creator_user_id est NOT NULL en base, avec une cle etrangere en ON DELETE
        // RESTRICT : une equipe sans createur n'existe pas. Le montage doit le dire,
        // sinon il autorise des scenarios que la base refuse.
        team.setCreator(user("Createur de " + name));
        return team;
    }

    private static CardTeamMember memberOf(CardTeam team, AppUser user) {
        CardTeamMember member = new CardTeamMember();
        member.setId(UUID.randomUUID());
        member.setTeam(team);
        member.setUser(user);
        return member;
    }

    @BeforeEach
    void setUp() {
        service = new AdminCardTeamService(
                teamRepository,
                memberRepository,
                joinRequestRepository,
                invitationRepository,
                userRepository,
                new CardTeamMembership(teamRepository, memberRepository, joinRequestRepository, invitationRepository, friendshipRepository, populationPolicy),
                cardService
        );
    }

    @Test
    void movingAPlayerIntoAFullTeamIsRefused() {
        CardTeam target = team("Pleine");
        AppUser player = user("Joueur");
        when(teamRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
        when(userRepository.findById(player.getId())).thenReturn(Optional.of(player));
        when(memberRepository.findByUserId(player.getId())).thenReturn(Optional.empty());
        when(memberRepository.countByTeamId(target.getId())).thenReturn((long) CardTeam.MAX_MEMBERS);

        assertThatThrownBy(() -> service.moveMember(target.getId(), player.getId()))
                .isInstanceOf(CodedConflictException.class)
                .satisfies(thrown -> assertThat(((CodedConflictException) thrown).getCode()).isEqualTo("CARD_TEAM_FULL"));

        verify(memberRepository, never()).save(any());
    }

    @Test
    void incompatibleMoveKeepsThePreviousMembership() {
        CardTeam source = team("Source");
        CardTeam target = team("Cible");
        AppUser staff = user("Staff");
        CardTeamMember current = memberOf(source, staff);
        when(teamRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
        when(userRepository.findById(staff.getId())).thenReturn(Optional.of(staff));
        when(memberRepository.findByUserId(staff.getId())).thenReturn(Optional.of(current));
        when(memberRepository.findByTeamIdOrderByJoinedAt(target.getId())).thenReturn(List.of(memberOf(target, user("Joueur"))));
        org.mockito.Mockito.doThrow(new CodedConflictException("CARD_TEAM_STAFF_PLAYER_MIX", "mix"))
                .when(populationPolicy).requireHomogeneous(any());

        assertThatThrownBy(() -> service.moveMember(target.getId(), staff.getId()))
                .isInstanceOf(CodedConflictException.class);

        verify(memberRepository, never()).delete(any());
        verify(memberRepository, never()).save(any());
    }

    @Test
    void movingTheLastMemberAwayDeletesTheEmptiedTeam() {
        CardTeam source = team("Source");
        CardTeam target = team("Cible");
        AppUser player = user("Joueur");
        CardTeamMember current = memberOf(source, player);
        when(teamRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
        when(userRepository.findById(player.getId())).thenReturn(Optional.of(player));
        when(memberRepository.findByUserId(player.getId())).thenReturn(Optional.of(current));
        when(memberRepository.countByTeamId(target.getId())).thenReturn(1L);
        when(memberRepository.countByTeamId(source.getId())).thenReturn(0L);
        when(memberRepository.findByTeamIdOrderByJoinedAt(target.getId())).thenReturn(List.of());

        service.moveMember(target.getId(), player.getId());

        verify(memberRepository).delete(current);
        verify(memberRepository).save(any(CardTeamMember.class));
        verify(teamRepository).deleteById(source.getId());
    }

    @Test
    void movingAPlayerCancelsTheirOtherPendingJoinRequest() {
        CardTeam target = team("Cible");
        AppUser player = user("Joueur");
        CardTeamJoinRequest pending = new CardTeamJoinRequest();
        pending.setId(UUID.randomUUID());
        pending.setTeam(team("Ailleurs"));
        pending.setRequester(player);
        pending.setStatus(CardTeamRequestStatus.PENDING);
        when(teamRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
        when(userRepository.findById(player.getId())).thenReturn(Optional.of(player));
        when(memberRepository.findByUserId(player.getId())).thenReturn(Optional.empty());
        when(memberRepository.countByTeamId(target.getId())).thenReturn(0L);
        when(memberRepository.findByTeamIdOrderByJoinedAt(target.getId())).thenReturn(List.of());
        when(joinRequestRepository.findPendingForRequester(player.getId())).thenReturn(List.of(pending));

        service.moveMember(target.getId(), player.getId());

        // Sans cela, cette demande resurgirait le jour ou ce joueur redevient sans
        // equipe et bloquerait requestJoin avec CARD_TEAM_PENDING_EXISTS.
        assertThat(pending.getStatus()).isEqualTo(CardTeamRequestStatus.CANCELLED);
        assertThat(pending.getRespondedAt()).isNotNull();
        verify(joinRequestRepository).save(pending);
    }

    @Test
    void movingAPlayerIntoTheirOwnTeamChangesNothing() {
        CardTeam target = team("Cible");
        AppUser player = user("Joueur");
        when(teamRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
        when(userRepository.findById(player.getId())).thenReturn(Optional.of(player));
        when(memberRepository.findByUserId(player.getId())).thenReturn(Optional.of(memberOf(target, player)));

        service.moveMember(target.getId(), player.getId());

        verify(memberRepository, never()).delete(any());
        verify(memberRepository, never()).save(any());
    }

    @Test
    void mergingTwoIntoTwoMovesEveryoneAndBefriendsThemAll() {
        CardTeam target = team("Cible");
        CardTeam source = team("Source");
        AppUser x = user("X");
        AppUser y = user("Y");
        AppUser a = user("A");
        AppUser b = user("B");
        when(teamRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
        when(teamRepository.findByIdForUpdate(source.getId())).thenReturn(Optional.of(source));
        when(memberRepository.findByTeamIdOrderByJoinedAt(source.getId()))
                .thenReturn(List.of(memberOf(source, a), memberOf(source, b)));
        when(memberRepository.countByTeamId(target.getId())).thenReturn(2L);
        // La cible se remplit au fil des rattachements : chaque arrivant doit devenir
        // ami de tous ceux qui sont deja la, y compris celui arrive juste avant lui.
        when(memberRepository.findByTeamIdOrderByJoinedAt(target.getId())).thenReturn(
                List.of(memberOf(target, x), memberOf(target, y)),
                List.of(memberOf(target, x), memberOf(target, y)),
                List.of(memberOf(target, x), memberOf(target, y), memberOf(target, a))
        );
        when(friendshipRepository.findBetween(any(), any())).thenReturn(Optional.empty());

        service.merge(target.getId(), source.getId());

        verify(memberRepository, times(2)).save(any(CardTeamMember.class));
        // 2 + 3 : c'est ce total qui prouve que la fusion recree bien le maillage complet
        // d'amities, et pas seulement les liens avec les membres d'origine. Deux equipes
        // de deux sont desormais la plus grande fusion qui tienne dans la limite.
        verify(friendshipRepository, times(5)).save(any(Friendship.class));
        verify(teamRepository).delete(source);
    }

    @Test
    void incompatibleMergeDoesNotRemoveSourceMembers() {
        CardTeam target = team("Joueurs");
        CardTeam source = team("Staff");
        AppUser staff = user("Staff");
        when(teamRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
        when(teamRepository.findByIdForUpdate(source.getId())).thenReturn(Optional.of(source));
        when(memberRepository.findByTeamIdOrderByJoinedAt(source.getId()))
                .thenReturn(List.of(memberOf(source, staff)));
        when(memberRepository.findByTeamIdOrderByJoinedAt(target.getId()))
                .thenReturn(List.of(memberOf(target, user("Joueur"))));
        org.mockito.Mockito.doThrow(new CodedConflictException("CARD_TEAM_STAFF_PLAYER_MIX", "mix"))
                .when(populationPolicy).requireHomogeneous(any());

        assertThatThrownBy(() -> service.merge(target.getId(), source.getId()))
                .isInstanceOf(CodedConflictException.class);

        verify(memberRepository, never()).delete(any());
        verify(teamRepository, never()).delete(any());
    }

    @Test
    void mergingCancelsPendingRequestsForEachMovedPlayer() {
        CardTeam target = team("Cible");
        CardTeam source = team("Source");
        AppUser a = user("A");
        AppUser b = user("B");
        CardTeamJoinRequest pendingForA = new CardTeamJoinRequest();
        pendingForA.setId(UUID.randomUUID());
        pendingForA.setTeam(team("Ailleurs"));
        pendingForA.setRequester(a);
        pendingForA.setStatus(CardTeamRequestStatus.PENDING);
        when(teamRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
        when(teamRepository.findByIdForUpdate(source.getId())).thenReturn(Optional.of(source));
        when(memberRepository.findByTeamIdOrderByJoinedAt(source.getId()))
                .thenReturn(List.of(memberOf(source, a), memberOf(source, b)));
        when(memberRepository.countByTeamId(target.getId())).thenReturn(0L);
        when(memberRepository.findByTeamIdOrderByJoinedAt(target.getId())).thenReturn(List.of());
        when(joinRequestRepository.findPendingForRequester(a.getId())).thenReturn(List.of(pendingForA));

        service.merge(target.getId(), source.getId());

        assertThat(pendingForA.getStatus()).isEqualTo(CardTeamRequestStatus.CANCELLED);
        verify(joinRequestRepository).save(pendingForA);
    }

    @Test
    void mergingTwoFullHalvesIsRefusedWithTheResultingSize() {
        CardTeam target = team("Cible");
        CardTeam source = team("Source");
        when(teamRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
        when(teamRepository.findByIdForUpdate(source.getId())).thenReturn(Optional.of(source));
        when(memberRepository.findByTeamIdOrderByJoinedAt(source.getId()))
                .thenReturn(List.of(memberOf(source, user("A")), memberOf(source, user("B")),
                                    memberOf(source, user("C")), memberOf(source, user("D"))));
        when(memberRepository.countByTeamId(target.getId())).thenReturn(4L);

        assertThatThrownBy(() -> service.merge(target.getId(), source.getId()))
                .isInstanceOf(CodedConflictException.class)
                .satisfies(thrown -> {
                    CodedConflictException conflict = (CodedConflictException) thrown;
                    assertThat(conflict.getCode()).isEqualTo("CARD_TEAM_MERGE_TOO_LARGE");
                    assertThat(conflict.getMessage()).contains("8");
                });

        verify(teamRepository, never()).delete(any());
    }

    @Test
    void mergingATeamWithItselfIsRejected() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> service.merge(id, id)).isInstanceOf(BadRequestException.class);

        verify(teamRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void deletingATeamClearsItsMembersRequestsAndInvitationsFirst() {
        CardTeam team = team("Condamnee");
        when(teamRepository.findByIdForUpdate(team.getId())).thenReturn(Optional.of(team));

        service.delete(team.getId());

        verify(memberRepository).deleteByTeamId(team.getId());
        verify(joinRequestRepository).deleteByTeamId(team.getId());
        verify(invitationRepository).deleteByTeamId(team.getId());
        verify(teamRepository).delete(team);
    }

    @Test
    void removingAPlayerFreesThemAndDeletesTheirTeamIfItEmpties() {
        CardTeam team = team("Solo");
        AppUser player = user("Joueur");
        CardTeamMember current = memberOf(team, player);
        when(memberRepository.findByUserId(player.getId())).thenReturn(Optional.of(current));
        when(teamRepository.findByIdForUpdate(team.getId())).thenReturn(Optional.of(team));
        when(memberRepository.countByTeamId(team.getId())).thenReturn(0L);

        service.removeMember(player.getId());

        verify(memberRepository).delete(current);
        verify(teamRepository).deleteById(team.getId());
    }

    /**
     * La premiere lecture sert a savoir quelle equipe verrouiller, elle precede donc
     * forcement le verrou. Si le joueur a change d'equipe entre les deux, supprimer
     * l'adhesion relue reviendrait a vider une equipe qu'on n'a pas verrouillee.
     */
    @Test
    void removingAPlayerWhoJustChangedTeamIsRefusedRatherThanAppliedToTheWrongTeam() {
        CardTeam locked = team("Verrouillee");
        CardTeam elsewhere = team("Ailleurs");
        AppUser player = user("Joueur");

        when(memberRepository.findByUserId(player.getId()))
                .thenReturn(Optional.of(memberOf(locked, player)))
                .thenReturn(Optional.of(memberOf(elsewhere, player)));
        when(teamRepository.findByIdForUpdate(locked.getId())).thenReturn(Optional.of(locked));

        assertThatThrownBy(() -> service.removeMember(player.getId()))
                .isInstanceOf(ConflictException.class);

        verify(memberRepository, never()).delete(any());
        verify(teamRepository, never()).deleteById(any());
    }

    @Test
    void movingAPlayerIntoATeamThatDoesNotExistIsRefusedBeforeReadingThePlayer() {
        UUID unknownTeam = UUID.randomUUID();
        UUID someUser = UUID.randomUUID();
        when(teamRepository.findByIdForUpdate(unknownTeam)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.moveMember(unknownTeam, someUser))
                .isInstanceOf(NotFoundException.class);

        // L'ordre compte : une cible inexistante ne doit pas aller jusqu'a interroger
        // l'utilisateur, sinon un identifiant d'equipe errone repond "utilisateur
        // introuvable" et envoie l'administrateur chercher le mauvais probleme.
        verifyNoInteractions(userRepository);
        verify(memberRepository, never()).save(any());
    }

    @Test
    void movingAPlayerWhoDoesNotExistIsRefused() {
        CardTeam target = team("Cible");
        UUID unknownUser = UUID.randomUUID();
        when(teamRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
        when(userRepository.findById(unknownUser)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.moveMember(target.getId(), unknownUser))
                .isInstanceOf(NotFoundException.class);

        verify(memberRepository, never()).save(any());
    }

    /**
     * Ce test compte les appels plutot que le resultat : c'est la seule facon de
     * faire echouer un retour au chargement equipe par equipe, qui rendrait
     * exactement la meme page.
     */
    @Test
    void searchLoadsTheMembersOfAWholePageInASingleQuery() {
        CardTeam first = team("Une");
        CardTeam second = team("Deux");

        when(teamRepository.searchAdminTeams(eq("%"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second)));
        when(memberRepository.findByTeamIdInOrderByJoinedAt(List.of(first.getId(), second.getId())))
                .thenReturn(List.of(memberOf(first, user("Alice")), memberOf(second, user("Bob"))));

        PageResponse<AdminCardTeamDto> page = service.search(null, Pageable.unpaged());

        assertThat(page.content()).hasSize(2);
        assertThat(page.content().get(0).members()).hasSize(1);
        assertThat(page.content().get(1).members()).hasSize(1);
        verify(memberRepository, never()).findByTeamIdOrderByJoinedAt(any());
    }

    @Test
    void searchOnAnEmptyPageDoesNotQueryMembersAtAll() {
        when(teamRepository.searchAdminTeams(eq("%"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        assertThat(service.search(null, Pageable.unpaged()).content()).isEmpty();

        verify(memberRepository, never()).findByTeamIdInOrderByJoinedAt(any());
    }
}
