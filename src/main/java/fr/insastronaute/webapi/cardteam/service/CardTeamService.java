package fr.insastronaute.webapi.cardteam.service;

import fr.insastronaute.webapi.cardteam.domain.CardTeam;
import fr.insastronaute.webapi.cardteam.domain.CardTeamInvitation;
import fr.insastronaute.webapi.cardteam.domain.CardTeamJoinRequest;
import fr.insastronaute.webapi.cardteam.domain.CardTeamMember;
import fr.insastronaute.webapi.cardteam.domain.CardTeamRequestStatus;
import fr.insastronaute.webapi.cardteam.dto.*;
import fr.insastronaute.webapi.cardteam.repository.CardTeamInvitationRepository;
import fr.insastronaute.webapi.cardteam.repository.CardTeamJoinRequestRepository;
import fr.insastronaute.webapi.cardteam.repository.CardTeamMemberRepository;
import fr.insastronaute.webapi.cardteam.repository.CardTeamRepository;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.common.web.CodedConflictException;
import fr.insastronaute.webapi.common.web.ForbiddenException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.friend.domain.Friendship;
import fr.insastronaute.webapi.friend.domain.FriendshipStatus;
import fr.insastronaute.webapi.friend.repository.FriendshipRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;

@Service
public class CardTeamService {
    private final CardTeamRepository teamRepository;
    private final CardTeamMemberRepository memberRepository;
    private final CardTeamJoinRequestRepository joinRequestRepository;
    private final CardTeamInvitationRepository invitationRepository;
    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final CardTeamMembership membership;
    private final CardTeamPopulationPolicy populationPolicy;

    public CardTeamService(
            CardTeamRepository teamRepository,
            CardTeamMemberRepository memberRepository,
            CardTeamJoinRequestRepository joinRequestRepository,
            CardTeamInvitationRepository invitationRepository,
            FriendshipRepository friendshipRepository,
            UserRepository userRepository,
            CardTeamMembership membership,
            CardTeamPopulationPolicy populationPolicy
    ) {
        this.teamRepository = teamRepository;
        this.memberRepository = memberRepository;
        this.joinRequestRepository = joinRequestRepository;
        this.invitationRepository = invitationRepository;
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
        this.membership = membership;
        this.populationPolicy = populationPolicy;
    }

    @Transactional(readOnly = true)
    public CardTeamStateDto getState(AppUser user) {
        return memberRepository.findByUserId(user.getId())
                .map(member -> stateForMember(member.getTeam()))
                .orElseGet(() -> stateWithoutTeam(user));
    }

    /**
     * Inviter un ami, avec ou sans equipe derriere soi.
     *
     * <p>Sans equipe, l'invitation ne cree rien : elle porte seulement le nom que
     * l'equipe portera si l'autre accepte. Creer l'equipe des maintenant en ferait une
     * equipe d'une personne -- qui ne mutualise rien et reserve un nom que plus
     * personne ne peut prendre, y compris si l'invitation est refusee.</p>
     *
     * <p>Depuis une equipe, au contraire, le nom est refuse : elle est deja nommee, et
     * l'accepter ici serait un moyen detourne de la renommer.</p>
     */
    @Transactional
    public void invite(AppUser inviter, UUID invitedUserId, String requestedTeamName) {
        CardTeamMember inviterMembership = memberRepository.findByUserId(inviter.getId()).orElse(null);
        String proposedName = null;
        if (inviterMembership == null) {
            proposedName = normalizeName(requestedTeamName);
            if (teamRepository.existsByNameIgnoreCase(proposedName)) {
                throw new CodedConflictException("CARD_TEAM_NAME_TAKEN", "Ce nom d'equipe est deja utilise.");
            }
        } else if (requestedTeamName != null && !requestedTeamName.isBlank()) {
            throw new BadRequestException("Votre equipe est deja nommee.");
        }
        AppUser invited = userRepository.findById(invitedUserId)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
        Friendship friendship = friendshipRepository.findBetween(inviter.getId(), invitedUserId)
                .filter(relation -> relation.getStatus() == FriendshipStatus.ACCEPTED)
                .orElseThrow(() -> new CodedConflictException(
                        "CARD_TEAM_FRIEND_REQUIRED",
                        "Vous ne pouvez inviter que l'un de vos amis."
                ));
        if (friendship == null) throw new IllegalStateException("unreachable");
        requireAvailablePlayer(invited);
        if (inviterMembership == null) {
            populationPolicy.requireHomogeneous(List.of(inviter, invited));
        } else {
            membership.requireCompatible(inviterMembership.getTeam(), invited);
        }
        if (inviterMembership != null) membership.requireCapacity(inviterMembership.getTeam());
        if (invitationRepository.existsByInvitedIdAndStatus(invitedUserId, CardTeamRequestStatus.PENDING)
                || joinRequestRepository.existsByRequesterIdAndStatus(invitedUserId, CardTeamRequestStatus.PENDING)) {
            throw pendingConflict();
        }
        CardTeamInvitation invitation = new CardTeamInvitation();
        invitation.setTeam(inviterMembership == null ? null : inviterMembership.getTeam());
        invitation.setProposedTeamName(proposedName);
        invitation.setInviter(inviter);
        invitation.setInvited(invited);
        invitationRepository.save(invitation);
    }

    @Transactional
    public void requestJoin(AppUser requester, UUID teamId) {
        requireAvailablePlayer(requester);
        CardTeam team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("Equipe de cartes introuvable."));
        membership.requireCompatible(team, requester);
        Set<UUID> friendIds = new HashSet<>(friendshipRepository.findAcceptedFriendIds(requester.getId()));
        boolean hasFriend = memberRepository.findByTeamIdOrderByJoinedAt(teamId).stream()
                .anyMatch(member -> friendIds.contains(member.getUser().getId()));
        if (!hasFriend) {
            throw new CodedConflictException(
                    "CARD_TEAM_FRIEND_REQUIRED",
                    "Cette equipe ne contient aucun de vos amis."
            );
        }
        membership.requireCapacity(team);
        if (joinRequestRepository.existsByRequesterIdAndStatus(requester.getId(), CardTeamRequestStatus.PENDING)
                || invitationRepository.existsByInvitedIdAndStatus(requester.getId(), CardTeamRequestStatus.PENDING)) {
            throw pendingConflict();
        }
        CardTeamJoinRequest request = new CardTeamJoinRequest();
        request.setTeam(team);
        request.setRequester(requester);
        joinRequestRepository.save(request);
    }

    @Transactional
    public void acceptJoinRequest(AppUser decidingMember, UUID requestId) {
        CardTeamMember membership = requireMembership(decidingMember);
        CardTeamJoinRequest request = joinRequestRepository
                .findByIdAndStatus(requestId, CardTeamRequestStatus.PENDING)
                .orElseThrow(() -> new NotFoundException("Demande d'adhesion introuvable."));
        if (!membership.getTeam().getId().equals(request.getTeam().getId())) {
            throw new ForbiddenException("Cette demande ne concerne pas votre equipe.");
        }
        addMember(request.getTeam(), request.getRequester());
        request.setStatus(CardTeamRequestStatus.ACCEPTED);
        request.setDecidedBy(decidingMember);
        request.setRespondedAt(now());
        joinRequestRepository.save(request);
        cancelOtherPendingOperations(request.getRequester().getId(), requestId, null);
    }

    @Transactional
    public void declineJoinRequest(AppUser decidingMember, UUID requestId) {
        CardTeamMember membership = requireMembership(decidingMember);
        CardTeamJoinRequest request = joinRequestRepository
                .findByIdAndStatus(requestId, CardTeamRequestStatus.PENDING)
                .orElseThrow(() -> new NotFoundException("Demande d'adhesion introuvable."));
        if (!membership.getTeam().getId().equals(request.getTeam().getId())) {
            throw new ForbiddenException("Cette demande ne concerne pas votre equipe.");
        }
        request.setStatus(CardTeamRequestStatus.DECLINED);
        request.setDecidedBy(decidingMember);
        request.setRespondedAt(now());
        joinRequestRepository.save(request);
    }

    @Transactional
    public void acceptInvitation(AppUser invited, UUID invitationId) {
        CardTeamInvitation invitation = invitationRepository
                .findByIdAndStatus(invitationId, CardTeamRequestStatus.PENDING)
                .orElseThrow(() -> new NotFoundException("Invitation d'equipe introuvable."));
        if (!invitation.getInvited().getId().equals(invited.getId())) {
            throw new ForbiddenException("Cette invitation ne vous est pas destinee.");
        }
        if (invitation.getTeam() == null) foundTeam(invitation, invited);
        else addMember(invitation.getTeam(), invited);
        invitation.setStatus(CardTeamRequestStatus.ACCEPTED);
        invitation.setRespondedAt(now());
        invitationRepository.save(invitation);
        cancelOtherPendingOperations(invited.getId(), null, invitationId);
    }

    @Transactional
    public void declineInvitation(AppUser invited, UUID invitationId) {
        CardTeamInvitation invitation = invitationRepository
                .findByIdAndStatus(invitationId, CardTeamRequestStatus.PENDING)
                .orElseThrow(() -> new NotFoundException("Invitation d'equipe introuvable."));
        if (!invitation.getInvited().getId().equals(invited.getId())) {
            throw new ForbiddenException("Cette invitation ne vous est pas destinee.");
        }
        invitation.setStatus(CardTeamRequestStatus.DECLINED);
        invitation.setRespondedAt(now());
        invitationRepository.save(invitation);
    }

    /**
     * Faire naitre l'equipe promise par l'invitation, avec ses deux membres d'un coup.
     *
     * <p>Le nom n'etait reserve nulle part -- rien n'existait -- donc il a pu etre pris
     * entre l'invitation et cette acceptation, et l'invitant a pu rejoindre une autre
     * equipe entre-temps. Les deux cas se decident ici, avant toute ecriture.</p>
     */
    private void foundTeam(CardTeamInvitation invitation, AppUser invited) {
        AppUser inviter = invitation.getInviter();
        requireAvailablePlayer(invited);
        populationPolicy.requireHomogeneous(List.of(inviter, invited));
        if (memberRepository.findByUserId(inviter.getId()).isPresent()) {
            throw new CodedConflictException(
                    "CARD_TEAM_INVITER_LEFT",
                    "La personne qui vous a invite a rejoint une autre equipe entre-temps."
            );
        }
        String name = invitation.getProposedTeamName();
        if (teamRepository.existsByNameIgnoreCase(name)) {
            throw new CodedConflictException("CARD_TEAM_NAME_TAKEN", "Ce nom d'equipe est deja utilise.");
        }
        CardTeam team = new CardTeam();
        team.setName(name);
        // L'invitant reste le fondateur : c'est lui qui a choisi le nom.
        team.setCreator(inviter);
        try {
            team = teamRepository.save(team);
            memberRepository.save(member(team, inviter));
            memberRepository.save(member(team, invited));
            memberRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            // Deux acceptations concurrentes visant le meme nom : l'index unique tranche.
            throw new CodedConflictException("CARD_TEAM_NAME_TAKEN", "Ce nom d'equipe est deja utilise.");
        }
        membership.ensureAcceptedFriendship(invited, inviter);
        invitation.setTeam(team);
        invitation.setProposedTeamName(null);
        // L'invitant n'a plus a chercher d'equipe : ses autres demandes n'ont plus d'objet.
        cancelOtherPendingOperations(inviter.getId(), null, null);
    }

    private void addMember(CardTeam requestedTeam, AppUser joiningUser) {
        CardTeam lockedTeam = membership.lockTeam(requestedTeam.getId());
        requireAvailablePlayer(joiningUser);
        membership.requireCapacity(lockedTeam);
        membership.attachMember(lockedTeam, joiningUser);
    }

    private void cancelOtherPendingOperations(UUID userId, UUID acceptedRequestId, UUID acceptedInvitationId) {
        joinRequestRepository.findPendingForRequester(userId).stream()
                .filter(request -> !request.getId().equals(acceptedRequestId))
                .forEach(request -> {
                    request.setStatus(CardTeamRequestStatus.CANCELLED);
                    request.setRespondedAt(now());
                    joinRequestRepository.save(request);
                });
        invitationRepository.findPendingForInvitedUser(userId).stream()
                .filter(invitation -> !invitation.getId().equals(acceptedInvitationId))
                .forEach(invitation -> {
                    invitation.setStatus(CardTeamRequestStatus.CANCELLED);
                    invitation.setRespondedAt(now());
                    invitationRepository.save(invitation);
                });
    }

    private CardTeamMember requireMembership(AppUser user) {
        return memberRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ForbiddenException("Vous n'appartenez a aucune equipe de cartes."));
    }

    private void requireAvailablePlayer(AppUser user) {
        if (memberRepository.findByUserId(user.getId()).isPresent()) {
            throw new CodedConflictException("CARD_TEAM_ALREADY_MEMBER", "Ce joueur appartient deja a une equipe.");
        }
    }

    private static CodedConflictException pendingConflict() {
        return new CodedConflictException("CARD_TEAM_PENDING_EXISTS", "Une demande ou invitation est deja en attente.");
    }

    private static Instant now() {
        return fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
    }

    private CardTeamStateDto stateForMember(CardTeam team) {
        List<CardTeamMember> members = memberRepository.findByTeamIdOrderByJoinedAt(team.getId());
        List<CardTeamJoinRequestDto> incoming = joinRequestRepository.findPendingForTeam(team.getId()).stream()
                .map(request -> toJoinRequestDto(request, Set.of()))
                .toList();
        return new CardTeamStateDto(toTeamDto(team, members), List.of(), List.of(), List.of(), incoming, List.of());
    }

    private CardTeamStateDto stateWithoutTeam(AppUser user) {
        List<UUID> friendIds = friendshipRepository.findAcceptedFriendIds(user.getId());
        Set<UUID> friendIdSet = new HashSet<>(friendIds);
        List<CardTeamSummaryDto> discoverable = friendIds.isEmpty() ? List.of() :
                memberRepository.findTeamsContainingUsers(friendIds).stream()
                        .map(team -> toSummary(team, friendIdSet))
                        .toList();
        List<CardTeamInvitationDto> invitations = invitationRepository.findPendingForInvitedUser(user.getId()).stream()
                .map(this::toInvitationDto)
                .toList();
        List<CardTeamJoinRequestDto> outgoing = joinRequestRepository.findPendingForRequester(user.getId()).stream()
                .map(request -> toJoinRequestDto(request, friendIdSet))
                .toList();
        List<CardTeamOutgoingInvitationDto> sent = invitationRepository.findPendingFromInviter(user.getId()).stream()
                .map(invitation -> new CardTeamOutgoingInvitationDto(
                        invitation.getId(),
                        invitation.getTeam() == null ? invitation.getProposedTeamName() : invitation.getTeam().getName(),
                        CardTeamUserDto.from(invitation.getInvited()),
                        invitation.getCreatedAt()
                ))
                .toList();
        return new CardTeamStateDto(null, discoverable, invitations, outgoing, List.of(), sent);
    }

    private CardTeamInvitationDto toInvitationDto(CardTeamInvitation invitation) {
        return new CardTeamInvitationDto(
                invitation.getId(),
                invitation.getTeam() == null ? null : toSummary(invitation.getTeam(), Set.of()),
                invitation.getProposedTeamName(),
                CardTeamUserDto.from(invitation.getInviter()),
                invitation.getCreatedAt()
        );
    }

    private CardTeamJoinRequestDto toJoinRequestDto(CardTeamJoinRequest request, Set<UUID> friendIds) {
        return new CardTeamJoinRequestDto(
                request.getId(),
                toSummary(request.getTeam(), friendIds),
                CardTeamUserDto.from(request.getRequester()),
                request.getCreatedAt()
        );
    }

    private CardTeamSummaryDto toSummary(CardTeam team, Set<UUID> friendIds) {
        List<CardTeamMember> members = memberRepository.findByTeamIdOrderByJoinedAt(team.getId());
        List<CardTeamUserDto> friends = members.stream()
                .map(CardTeamMember::getUser)
                .filter(member -> friendIds.contains(member.getId()))
                .map(CardTeamUserDto::from)
                .toList();
        return new CardTeamSummaryDto(team.getId(), team.getName(), members.size(), CardTeam.MAX_MEMBERS, friends);
    }

    private CardTeamDto toTeamDto(CardTeam team, List<CardTeamMember> members) {
        return new CardTeamDto(
                team.getId(),
                team.getName(),
                members.size(),
                CardTeam.MAX_MEMBERS,
                members.stream().map(CardTeamMember::getUser).map(CardTeamUserDto::from).toList(),
                team.getCreatedAt()
        );
    }

    private static CardTeamMember member(CardTeam team, AppUser user) {
        CardTeamMember member = new CardTeamMember();
        member.setTeam(team);
        member.setUser(user);
        return member;
    }

    private static String normalizeName(String requestedName) {
        if (requestedName == null || requestedName.isBlank()) {
            throw new BadRequestException("Le nom de l'equipe est requis.");
        }
        String name = requestedName.trim();
        if (name.length() > CardTeamNames.MAX_LENGTH) {
            throw new BadRequestException("Le nom de l'equipe doit contenir au plus "
                    + CardTeamNames.MAX_LENGTH + " caracteres.");
        }
        return name;
    }
}
