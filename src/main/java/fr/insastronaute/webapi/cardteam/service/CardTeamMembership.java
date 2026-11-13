package fr.insastronaute.webapi.cardteam.service;

import fr.insastronaute.webapi.cardteam.domain.CardTeam;
import fr.insastronaute.webapi.cardteam.domain.CardTeamMember;
import fr.insastronaute.webapi.cardteam.repository.CardTeamInvitationRepository;
import fr.insastronaute.webapi.cardteam.repository.CardTeamJoinRequestRepository;
import fr.insastronaute.webapi.cardteam.repository.CardTeamMemberRepository;
import fr.insastronaute.webapi.cardteam.repository.CardTeamRepository;
import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.common.web.CodedConflictException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.friend.domain.Friendship;
import fr.insastronaute.webapi.friend.domain.FriendshipStatus;
import fr.insastronaute.webapi.friend.repository.FriendshipRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Les regles d'appartenance a une equipe de cartes, en un seul endroit.
 *
 * <p>Le parcours joueur et le parcours administrateur les partagent : un
 * administrateur range les equipes, il ne contourne pas leurs regles. Dupliquer la
 * capacite maximale et la creation d'amities dans un second service, c'est garantir
 * qu'elles divergeront a la premiere evolution.</p>
 */
@Component
public class CardTeamMembership {

    private final CardTeamRepository teamRepository;
    private final CardTeamMemberRepository memberRepository;
    private final CardTeamJoinRequestRepository joinRequestRepository;
    private final CardTeamInvitationRepository invitationRepository;
    private final FriendshipRepository friendshipRepository;
    private final CardTeamPopulationPolicy populationPolicy;

    public CardTeamMembership(
            CardTeamRepository teamRepository,
            CardTeamMemberRepository memberRepository,
            CardTeamJoinRequestRepository joinRequestRepository,
            CardTeamInvitationRepository invitationRepository,
            FriendshipRepository friendshipRepository,
            CardTeamPopulationPolicy populationPolicy
    ) {
        this.teamRepository = teamRepository;
        this.memberRepository = memberRepository;
        this.joinRequestRepository = joinRequestRepository;
        this.invitationRepository = invitationRepository;
        this.friendshipRepository = friendshipRepository;
        this.populationPolicy = populationPolicy;
    }

    public CardTeam lockTeam(UUID teamId) {
        return teamRepository.findByIdForUpdate(teamId)
                .orElseThrow(() -> new NotFoundException("Equipe de cartes introuvable."));
    }

    public void requireCapacity(CardTeam team) {
        requireCapacityFor(team, 1);
    }

    public void requireCapacityFor(CardTeam team, int arrivals) {
        long resulting = memberRepository.countByTeamId(team.getId()) + arrivals;

        if (resulting <= CardTeam.MAX_MEMBERS) {
            return;
        }

        if (arrivals == 1) {
            throw new CodedConflictException("CARD_TEAM_FULL", "Cette equipe est complete.");
        }

        throw new CodedConflictException(
                "CARD_TEAM_MERGE_TOO_LARGE",
                "La fusion donnerait " + resulting + " membres pour un maximum de " + CardTeam.MAX_MEMBERS + "."
        );
    }

    public void attachMember(CardTeam team, AppUser user) {
        List<CardTeamMember> existingMembers = memberRepository.findByTeamIdOrderByJoinedAt(team.getId());
        populationPolicy.requireHomogeneous(java.util.stream.Stream.concat(
                existingMembers.stream().map(CardTeamMember::getUser),
                java.util.stream.Stream.of(user)
        ).toList());
        CardTeamMember membership = new CardTeamMember();
        membership.setTeam(team);
        membership.setUser(user);
        memberRepository.save(membership);
        existingMembers.forEach(member -> ensureAcceptedFriendship(user, member.getUser()));
    }

    public void requireCompatible(CardTeam team, AppUser user) {
        List<AppUser> users = new java.util.ArrayList<>(memberRepository.findByTeamIdOrderByJoinedAt(team.getId())
                .stream().map(CardTeamMember::getUser).toList());
        users.add(user);
        populationPolicy.requireHomogeneous(users);
    }

    public void requireCompatible(CardTeam team, List<AppUser> arrivals) {
        List<AppUser> users = new java.util.ArrayList<>(memberRepository.findByTeamIdOrderByJoinedAt(team.getId())
                .stream().map(CardTeamMember::getUser).toList());
        users.addAll(arrivals);
        populationPolicy.requireHomogeneous(users);
    }

    public void ensureAcceptedFriendship(AppUser joiningUser, AppUser existingMember) {
        Friendship friendship = friendshipRepository.findBetween(joiningUser.getId(), existingMember.getId())
                .orElseGet(() -> {
                    Friendship created = new Friendship();
                    created.setRequester(joiningUser);
                    created.setAddressee(existingMember);
                    return created;
                });
        if (friendship.getStatus() != FriendshipStatus.ACCEPTED) {
            friendship.setStatus(FriendshipStatus.ACCEPTED);
            friendship.setRespondedAt(TimeTravelContext.instantNow());
            friendshipRepository.save(friendship);
        } else if (friendship.getId() == null && friendship.getCreatedAt() == null) {
            friendshipRepository.save(friendship);
        }
    }

    /**
     * Une equipe sans membre disparait.
     *
     * <p>Ce n'est pas du menage cosmetique : l'index unique sur {@code LOWER(name)}
     * fait qu'une equipe vide garde son nom en otage, donc personne ne pourrait plus
     * recreer une equipe portant ce nom alors que plus aucun joueur n'y est.</p>
     *
     * <p>Les demandes et invitations encore en attente pour cette equipe sont
     * supprimees explicitement, pour la meme raison que AdminCardTeamService.delete
     * ne s'en remet pas au {@code ON DELETE CASCADE} du schema : la cascade existe en
     * production, mais le schema H2 des tests est genere depuis les entites JPA, ou
     * la cle etrangere n'en porte pas. Sans ce menage explicite, une equipe videe par
     * un depart, un retrait ou un deplacement alors qu'une demande d'adhesion lui est
     * encore adressee ferait echouer deleteById sur une contrainte de cle etrangere
     * en test tout en fonctionnant en production - exactement le piege que ce
     * refactor doit eviter partout ailleurs dans ce package.</p>
     */
    public void deleteIfEmpty(UUID teamId) {
        if (memberRepository.countByTeamId(teamId) == 0) {
            joinRequestRepository.deleteByTeamId(teamId);
            invitationRepository.deleteByTeamId(teamId);
            teamRepository.deleteById(teamId);
        }
    }
}
