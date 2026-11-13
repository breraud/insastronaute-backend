package fr.insastronaute.webapi.cardteam.service;

import fr.insastronaute.webapi.cardteam.domain.CardTeam;
import fr.insastronaute.webapi.cardteam.domain.CardTeamInvitation;
import fr.insastronaute.webapi.cardteam.domain.CardTeamJoinRequest;
import fr.insastronaute.webapi.cardteam.domain.CardTeamMember;
import fr.insastronaute.webapi.cardteam.domain.CardTeamRequestStatus;
import fr.insastronaute.webapi.cardteam.repository.CardTeamInvitationRepository;
import fr.insastronaute.webapi.cardteam.repository.CardTeamJoinRequestRepository;
import fr.insastronaute.webapi.cardteam.repository.CardTeamMemberRepository;
import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.user.domain.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CardTeamPopulationReconciler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CardTeamPopulationReconciler.class);

    private final CardTeamMemberRepository memberRepository;
    private final CardTeamJoinRequestRepository joinRequestRepository;
    private final CardTeamInvitationRepository invitationRepository;
    private final CardTeamMembership membership;
    private final CardTeamPopulationPolicy policy;

    public CardTeamPopulationReconciler(
            CardTeamMemberRepository memberRepository,
            CardTeamJoinRequestRepository joinRequestRepository,
            CardTeamInvitationRepository invitationRepository,
            CardTeamMembership membership,
            CardTeamPopulationPolicy policy
    ) {
        this.memberRepository = memberRepository;
        this.joinRequestRepository = joinRequestRepository;
        this.invitationRepository = invitationRepository;
        this.membership = membership;
        this.policy = policy;
    }

    @Transactional
    public void reconcile() {
        Set<String> staff;
        try {
            staff = policy.knownStaff();
        } catch (CardTeamRoleDirectoryUnavailableException exception) {
            LOGGER.warn("Reconciliation des equipes reportee : annuaire des roles indisponible.");
            return;
        }

        Map<UUID, List<CardTeamMember>> byTeam = memberRepository.findAllWithTeam().stream()
                .collect(Collectors.groupingBy(member -> member.getTeam().getId()));
        byTeam.forEach((teamId, members) -> {
            if (!isMixed(members, staff)) return;
            membership.lockTeam(teamId);
            List<CardTeamMember> current = memberRepository.findByTeamIdOrderByJoinedAt(teamId);
            if (!isMixed(current, staff)) return;
            current.stream()
                    .filter(member -> isStaff(member.getUser(), staff))
                    .forEach(memberRepository::delete);
            memberRepository.flush();
            LOGGER.warn("Membres staff retires d'une equipe de cartes mixte : {}", teamId);
        });

        joinRequestRepository.findAllByStatus(CardTeamRequestStatus.PENDING).stream()
                .filter(request -> !compatibleWithTeam(request.getTeam(), request.getRequester(), staff))
                .forEach(this::cancel);
        invitationRepository.findAllByStatus(CardTeamRequestStatus.PENDING).stream()
                .filter(invitation -> invitation.getTeam() == null
                        ? isStaff(invitation.getInviter(), staff) != isStaff(invitation.getInvited(), staff)
                        : !compatibleWithTeam(invitation.getTeam(), invitation.getInvited(), staff))
                .forEach(this::cancel);
    }

    private boolean compatibleWithTeam(CardTeam team, AppUser candidate, Set<String> staff) {
        boolean candidateStaff = isStaff(candidate, staff);
        return memberRepository.findByTeamIdOrderByJoinedAt(team.getId()).stream()
                .allMatch(member -> isStaff(member.getUser(), staff) == candidateStaff);
    }

    private static boolean isMixed(List<CardTeamMember> members, Set<String> staff) {
        boolean hasStaff = members.stream().anyMatch(member -> isStaff(member.getUser(), staff));
        boolean hasPlayer = members.stream().anyMatch(member -> !isStaff(member.getUser(), staff));
        return hasStaff && hasPlayer;
    }

    private static boolean isStaff(AppUser user, Set<String> staff) {
        return user.getKeycloakSub() != null && staff.contains(user.getKeycloakSub());
    }

    private void cancel(CardTeamJoinRequest request) {
        request.setStatus(CardTeamRequestStatus.CANCELLED);
        request.setRespondedAt(TimeTravelContext.instantNow());
        joinRequestRepository.save(request);
    }

    private void cancel(CardTeamInvitation invitation) {
        invitation.setStatus(CardTeamRequestStatus.CANCELLED);
        invitation.setRespondedAt(TimeTravelContext.instantNow());
        invitationRepository.save(invitation);
    }
}
