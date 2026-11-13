package fr.insastronaute.webapi.cardteam.service;

import fr.insastronaute.webapi.card.dto.CardDeckDto;
import fr.insastronaute.webapi.card.service.CardService;
import fr.insastronaute.webapi.cardteam.domain.CardTeam;
import fr.insastronaute.webapi.cardteam.domain.CardTeamMember;
import fr.insastronaute.webapi.cardteam.domain.CardTeamRequestStatus;
import fr.insastronaute.webapi.cardteam.dto.AdminCardTeamDto;
import fr.insastronaute.webapi.cardteam.repository.CardTeamInvitationRepository;
import fr.insastronaute.webapi.cardteam.repository.CardTeamJoinRequestRepository;
import fr.insastronaute.webapi.cardteam.repository.CardTeamMemberRepository;
import fr.insastronaute.webapi.cardteam.repository.CardTeamRepository;
import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.common.web.PageResponse;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
public class AdminCardTeamService {

    private final CardTeamRepository teamRepository;
    private final CardTeamMemberRepository memberRepository;
    private final CardTeamJoinRequestRepository joinRequestRepository;
    private final CardTeamInvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final CardTeamMembership membership;
    private final CardService cardService;

    public AdminCardTeamService(
            CardTeamRepository teamRepository,
            CardTeamMemberRepository memberRepository,
            CardTeamJoinRequestRepository joinRequestRepository,
            CardTeamInvitationRepository invitationRepository,
            UserRepository userRepository,
            CardTeamMembership membership,
            CardService cardService
    ) {
        this.teamRepository = teamRepository;
        this.memberRepository = memberRepository;
        this.joinRequestRepository = joinRequestRepository;
        this.invitationRepository = invitationRepository;
        this.userRepository = userRepository;
        this.membership = membership;
        this.cardService = cardService;
    }

    @Transactional(readOnly = true)
    public CardDeckDto getCollection(UUID teamId) {
        return cardService.getTeamDeck(teamId);
    }

    /**
     * Les membres de toute la page sont charges en une requete.
     *
     * <p>Passer par toDto equipe par equipe faisait une requete par ligne, soit
     * vingt-et-une pour une page de vingt. Le cout se voyait peu a l'echelle d'une
     * inte, mais la recherche est la page d'accueil de cet ecran : chaque frappe
     * dans le champ la relance.</p>
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminCardTeamDto> search(String query, Pageable pageable) {
        Page<CardTeam> teams = teamRepository.searchAdminTeams(likePattern(query), pageable);

        List<UUID> teamIds = teams.getContent().stream().map(CardTeam::getId).toList();
        Map<UUID, List<CardTeamMember>> membersByTeam = teamIds.isEmpty()
                ? Map.of()
                : memberRepository.findByTeamIdInOrderByJoinedAt(teamIds).stream()
                        .collect(Collectors.groupingBy(member -> member.getTeam().getId()));

        return PageResponse.from(teams.map(team ->
                AdminCardTeamDto.from(team, membersByTeam.getOrDefault(team.getId(), List.of()))));
    }

    @Transactional(readOnly = true)
    public AdminCardTeamDto findForUser(UUID userId) {
        return memberRepository.findByUserId(userId)
                .map(member -> toDto(member.getTeam()))
                .orElse(null);
    }

    @Transactional
    public void moveMember(UUID teamId, UUID userId) {
        CardTeam target = membership.lockTeam(teamId);
        AppUser player = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
        Optional<CardTeamMember> current = memberRepository.findByUserId(userId);

        // Deplacer un joueur vers son equipe actuelle est un succes sans effet, pas une
        // erreur : l'administrateur a obtenu l'etat qu'il demandait.
        if (current.isPresent() && current.get().getTeam().getId().equals(teamId)) {
            return;
        }

        membership.requireCompatible(target, player);
        membership.requireCapacity(target);

        UUID previousTeamId = current.map(member -> member.getTeam().getId()).orElse(null);
        current.ifPresent(member -> {
            memberRepository.delete(member);
            // Sans ce flush, Hibernate peut ordonner l'insertion avant la suppression et
            // violer ux_card_team_members_user, qui n'autorise qu'une adhesion par joueur.
            memberRepository.flush();
        });

        membership.attachMember(target, player);
        // L'arrivant peut porter une demande ou une invitation encore en attente
        // ailleurs : sans ceci, elle resurgirait le jour ou il redevient sans equipe,
        // et requestJoin la trouverait et refuserait tout net avec CARD_TEAM_PENDING_EXISTS.
        cancelPendingOperations(player.getId());

        if (previousTeamId != null) {
            membership.deleteIfEmpty(previousTeamId);
        }
    }

    @Transactional
    public void removeMember(UUID userId) {
        if (!removeMemberIfPresent(userId)) {
            throw new NotFoundException("Ce joueur n'appartient a aucune equipe.");
        }
    }

    /**
     * Retirer ce joueur de son equipe, sans se plaindre s'il n'en a pas.
     *
     * <p>Existe pour les appelants qui retirent au passage, sans savoir : la suppression
     * d'un joueur, par exemple. Attraper le {@link NotFoundException} de
     * {@link #removeMember} ne marcherait pas -- cette methode est transactionnelle, donc
     * Spring marque la transaction <em>rollback-only</em> avant de relancer l'exception.
     * L'appelant a beau l'avaler, son commit echoue ensuite en
     * {@code UnexpectedRollbackException}, loin de la cause.</p>
     *
     * @return vrai si une adhesion a ete retiree.
     */
    @Transactional
    public boolean removeMemberIfPresent(UUID userId) {
        CardTeamMember current = memberRepository.findByUserId(userId).orElse(null);
        if (current == null) {
            return false;
        }
        UUID teamId = current.getTeam().getId();
        membership.lockTeam(teamId);

        // Relecture apres le verrou. La premiere lecture sert a savoir quelle equipe
        // verrouiller, et se fait donc forcement avant lui : entre les deux, un autre
        // administrateur a pu deplacer ce joueur. On supprimerait alors une adhesion
        // qui n'appartient plus a l'equipe verrouillee, et deleteIfEmpty compterait
        // l'effectif de la mauvaise. Le cas est etroit, mais le refuser coute une
        // requete et evite d'avoir a expliquer un effectif faux.
        CardTeamMember confirmed = memberRepository.findByUserId(userId).orElse(null);
        if (confirmed == null) {
            return false;
        }
        if (!confirmed.getTeam().getId().equals(teamId)) {
            throw new ConflictException("Ce joueur vient de changer d'equipe. Recharge la page.");
        }

        memberRepository.delete(confirmed);
        memberRepository.flush();
        membership.deleteIfEmpty(teamId);
        return true;
    }

    @Transactional
    public void merge(UUID targetTeamId, UUID sourceTeamId) {
        if (targetTeamId.equals(sourceTeamId)) {
            throw new BadRequestException("Une equipe ne peut pas fusionner avec elle-meme.");
        }

        // Verrouiller dans un ordre deterministe : deux administrateurs qui fusionnent
        // A vers B et B vers A en meme temps se bloqueraient mutuellement sinon. Les
        // entites verrouillees sont reutilisees telles quelles, sans relecture.
        CardTeam target;
        CardTeam source;
        if (targetTeamId.compareTo(sourceTeamId) < 0) {
            target = membership.lockTeam(targetTeamId);
            source = membership.lockTeam(sourceTeamId);
        } else {
            source = membership.lockTeam(sourceTeamId);
            target = membership.lockTeam(targetTeamId);
        }

        List<CardTeamMember> sourceMembers = memberRepository.findByTeamIdOrderByJoinedAt(sourceTeamId);
        membership.requireCompatible(target, sourceMembers.stream().map(CardTeamMember::getUser).toList());
        membership.requireCapacityFor(target, sourceMembers.size());

        List<AppUser> moving = sourceMembers.stream().map(CardTeamMember::getUser).toList();
        sourceMembers.forEach(memberRepository::delete);
        memberRepository.flush();
        moving.forEach(player -> {
            membership.attachMember(target, player);
            // Meme raison que dans moveMember : un membre absorbe peut porter une
            // demande ou une invitation encore en attente ailleurs.
            cancelPendingOperations(player.getId());
        });

        joinRequestRepository.deleteByTeamId(sourceTeamId);
        invitationRepository.deleteByTeamId(sourceTeamId);
        teamRepository.delete(source);
    }

    @Transactional
    public void delete(UUID teamId) {
        CardTeam team = membership.lockTeam(teamId);
        // Suppression explicite plutot que ON DELETE CASCADE : la cascade existe en
        // production, mais les tests tournent sur un schema genere depuis les entites
        // JPA, ou la cle etrangere n'en a pas. Un code qui marche en prod et casse en
        // test a cet endroit-la est un piege evitable en trois lignes.
        memberRepository.deleteByTeamId(teamId);
        joinRequestRepository.deleteByTeamId(teamId);
        invitationRepository.deleteByTeamId(teamId);
        teamRepository.delete(team);
    }

    /**
     * Le motif LIKE que la requete de recherche attend.
     *
     * <p>Construit ici plutot que concatene dans le JPQL : voir
     * CardTeamRepository.searchAdminTeams pour la raison. Les caracteres que
     * LIKE interprete sont neutralises, sans quoi un joueur qui tape
     * {@code _} verrait remonter toutes les equipes.</p>
     */
    private static String likePattern(String query) {
        if (query == null || query.isBlank()) {
            return "%";
        }

        String escaped = query.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");

        return "%" + escaped + "%";
    }

    private AdminCardTeamDto toDto(CardTeam team) {
        return AdminCardTeamDto.from(team, memberRepository.findByTeamIdOrderByJoinedAt(team.getId()));
    }

    /**
     * Annule les demandes et invitations que ce joueur avait en attente ailleurs.
     *
     * <p>Mirroir de CardTeamService.cancelOtherPendingOperations, appele apres chaque
     * attachMember admin. Sans cela, une arrivee par deplacement ou fusion laisse une
     * demande PENDING invisible tant que le joueur est en equipe (stateForMember ne la
     * montre pas) ; le jour ou il redevient sans equipe, elle resurgit et requestJoin
     * refuse tout net avec CARD_TEAM_PENDING_EXISTS, sans qu'aucun endpoint ne lui
     * permette de l'annuler lui-meme.</p>
     */
    private void cancelPendingOperations(UUID userId) {
        joinRequestRepository.findPendingForRequester(userId).forEach(request -> {
            request.setStatus(CardTeamRequestStatus.CANCELLED);
            request.setRespondedAt(TimeTravelContext.instantNow());
            joinRequestRepository.save(request);
        });
        invitationRepository.findPendingForInvitedUser(userId).forEach(invitation -> {
            invitation.setStatus(CardTeamRequestStatus.CANCELLED);
            invitation.setRespondedAt(TimeTravelContext.instantNow());
            invitationRepository.save(invitation);
        });
    }
}
