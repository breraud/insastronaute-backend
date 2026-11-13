package fr.insastronaute.webapi.challenge.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.challenge.dto.ModeratedChallengeHistoryDto;
import fr.insastronaute.webapi.challenge.dto.ModerationHistoryType;
import fr.insastronaute.webapi.challenge.repository.ChallengeRepository;
import fr.insastronaute.webapi.challenge.repository.ChallengeSubmissionRepository;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionEvent;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.common.adminaudit.repository.AdminActionEventRepository;
import fr.insastronaute.webapi.common.web.ForbiddenException;
import fr.insastronaute.webapi.common.web.PageResponse;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ModeratedChallengeHistoryService {
    private static final TypeReference<LinkedHashMap<String, Object>> DETAILS = new TypeReference<>() {};
    private static final EnumSet<AdminActionType> ALL_ACTIONS = EnumSet.of(
            AdminActionType.CHALLENGE_VALIDATED,
            AdminActionType.CHALLENGE_REJECTED,
            AdminActionType.CHALLENGE_GRANTED,
            AdminActionType.CHALLENGE_BONUS_ADJUSTED);

    private final AdminActionEventRepository events;
    private final UserRepository users;
    private final ObjectMapper objectMapper;
    /**
     * De quoi savoir ce qui existe encore.
     *
     * <p>Absents, la page se lit comme avant : rien n'est dit supprime. Ils sont
     * toujours la en production -- c'est le constructeur a trois arguments, garde
     * pour les tests qui n'ont rien a dire de la suppression, qui les laisse
     * nuls.</p>
     */
    private final ChallengeRepository challenges;
    private final ChallengeSubmissionRepository submissions;

    public ModeratedChallengeHistoryService(
            AdminActionEventRepository events,
            UserRepository users,
            ObjectMapper objectMapper
    ) {
        this(events, users, objectMapper, null, null);
    }

    /** Celui que Spring appelle : deux constructeurs sans marqueur, et il ne choisit pas. */
    @Autowired
    public ModeratedChallengeHistoryService(
            AdminActionEventRepository events,
            UserRepository users,
            ObjectMapper objectMapper,
            ChallengeRepository challenges,
            ChallengeSubmissionRepository submissions
    ) {
        this.events = events;
        this.users = users;
        this.objectMapper = objectMapper;
        this.challenges = challenges;
        this.submissions = submissions;
    }

    @Transactional(readOnly = true)
    public PageResponse<ModeratedChallengeHistoryDto> search(
            String requesterKeycloakSub,
            Collection<String> requesterAuthorities,
            UUID playerUserId,
            UUID actorUserId,
            String challenge,
            Collection<ModerationHistoryType> types,
            Instant from,
            Instant to,
            Sort.Direction direction,
            int page,
            int size
    ) {
        boolean canViewActors = hasRole(requesterAuthorities, "ADMIN")
                || hasRole(requesterAuthorities, "SUPER_ADMIN")
                || hasRole(requesterAuthorities, "RESPOS_DEFIS")
                || hasRole(requesterAuthorities, "SOUS_RESPO_DEFIS");
        if (!canViewActors && actorUserId != null) {
            throw new ForbiddenException("Le filtre par valideur est réservé aux responsables défis.");
        }
        AppUser requester = canViewActors ? null : users.findByKeycloakSub(requesterKeycloakSub).orElse(null);
        EnumSet<AdminActionType> actions = actions(types);
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)));
        String playerPattern = playerUserId == null ? "%" : "%" + playerUserId.toString().toLowerCase(Locale.ROOT) + "%";
        String challengePattern = challengePattern(challenge);
        Page<AdminActionEvent> result = direction == Sort.Direction.ASC
                ? events.searchModeratedChallengesAsc(actions, actorUserId, playerUserId, playerPattern,
                        challengePattern, from, to, pageable)
                : events.searchModeratedChallengesDesc(actions, actorUserId, playerUserId, playerPattern,
                        challengePattern, from, to, pageable);
        Survivors survivors = survivorsOf(result.getContent());
        return PageResponse.from(result.map(event -> toDto(event, canViewActors, requester, survivors)));
    }

    /**
     * Ce qui existe encore, parmi ce que cette page nomme.
     *
     * <p>Une ligne d'historique garde le titre du defi et les points dans son propre
     * JSON : elle reste lisible apres la suppression, et c'est voulu. Mais elle garde
     * aussi les identifiants, et l'ecran s'en sert pour proposer une grace ou un
     * ajustement -- deux gestes qui partiraient en 404. Il faut donc le dire.</p>
     */
    private Survivors survivorsOf(List<AdminActionEvent> page) {
        if (challenges == null || submissions == null) {
            return Survivors.unknown();
        }
        Set<UUID> challengeIds = new LinkedHashSet<>();
        Set<UUID> submissionIds = new LinkedHashSet<>();
        for (AdminActionEvent event : page) {
            Map<String, Object> details = details(event);
            UUID challengeId = uuid(details.get("challengeId"));
            if (challengeId != null) challengeIds.add(challengeId);
            UUID submissionId = uuid(details.get("submissionId"));
            if (submissionId != null) submissionIds.add(submissionId);
        }
        return new Survivors(
                challengeIds.isEmpty() ? Set.of() : Set.copyOf(challenges.findExistingIds(challengeIds)),
                submissionIds.isEmpty() ? Set.of() : Set.copyOf(submissions.findExistingIds(submissionIds)),
                true);
    }

    /**
     * @param known faux quand rien ne permet de trancher : alors rien n'est dit
     *              supprime, plutot que tout
     */
    private record Survivors(Set<UUID> challenges, Set<UUID> submissions, boolean known) {
        static Survivors unknown() {
            return new Survivors(Set.of(), Set.of(), false);
        }

        boolean gone(Set<UUID> alive, UUID id) {
            return known && id != null && !alive.contains(id);
        }
    }

    ModeratedChallengeHistoryDto toDto(AdminActionEvent event, boolean administrator, AppUser requester) {
        return toDto(event, administrator, requester, Survivors.unknown());
    }

    ModeratedChallengeHistoryDto toDto(
            AdminActionEvent event, boolean administrator, AppUser requester, Survivors survivors) {
        Map<String, Object> details = details(event);
        UUID submissionId = uuid(details.get("submissionId"));
        UUID challengeId = uuid(details.get("challengeId"));
        AppUser eventActor = event.getActor();
        boolean actorHidden = !administrator && eventActor != null
                && (requester == null || !eventActor.getId().equals(requester.getId()));
        return new ModeratedChallengeHistoryDto(
                event.getId(),
                event.getOccurredAt(),
                publicType(event.getAction()),
                actorHidden ? null : party(eventActor),
                actorHidden,
                party(event.getTarget()),
                participants(details, event.getTarget()),
                text(details.get("challengeTitle")),
                integer(details.get("points")),
                integer(details.get("bonusPoints")),
                integer(details.get("previousBonusPoints")),
                text(details.get("comment")),
                submissionId,
                challengeId,
                survivors.gone(survivors.challenges(), challengeId),
                survivors.gone(survivors.submissions(), submissionId),
                event.isBackfilled());
    }

    private Map<String, Object> details(AdminActionEvent event) {
        if (!StringUtils.hasText(event.getDetailsJson())) return Map.of();
        try {
            return objectMapper.readValue(event.getDetailsJson(), DETAILS);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static List<ModeratedChallengeHistoryDto.Party> participants(
            Map<String, Object> details,
            AppUser target
    ) {
        if (!(details.get("participantIds") instanceof List<?> ids)) return List.of();
        List<?> names = details.get("participantNames") instanceof List<?> values ? values : List.of();
        List<ModeratedChallengeHistoryDto.Party> result = new ArrayList<>();
        for (int index = 0; index < ids.size(); index++) {
            UUID id = uuid(ids.get(index));
            if (id == null || target != null && id.equals(target.getId())) continue;
            String name = index < names.size() ? text(names.get(index)) : null;
            result.add(new ModeratedChallengeHistoryDto.Party(id, name == null ? "Utilisateur inconnu" : name));
        }
        return List.copyOf(result);
    }

    private static ModeratedChallengeHistoryDto.Party party(AppUser user) {
        if (user == null) return null;
        String name = StringUtils.hasText(user.getDisplayName()) ? user.getDisplayName() : "Utilisateur inconnu";
        return new ModeratedChallengeHistoryDto.Party(user.getId(), name);
    }

    private static ModerationHistoryType publicType(AdminActionType action) {
        return switch (action) {
            case CHALLENGE_VALIDATED -> ModerationHistoryType.VALIDATED;
            case CHALLENGE_REJECTED -> ModerationHistoryType.REJECTED;
            case CHALLENGE_GRANTED -> ModerationHistoryType.MANUAL_GRANT;
            case CHALLENGE_BONUS_ADJUSTED -> ModerationHistoryType.BONUS_ADJUSTED;
            default -> throw new IllegalArgumentException("Action étrangère à l'historique de modération.");
        };
    }

    private static EnumSet<AdminActionType> actions(Collection<ModerationHistoryType> types) {
        if (types == null || types.isEmpty()) return EnumSet.copyOf(ALL_ACTIONS);
        EnumSet<AdminActionType> result = EnumSet.noneOf(AdminActionType.class);
        for (ModerationHistoryType type : types) {
            result.add(switch (type) {
                case VALIDATED -> AdminActionType.CHALLENGE_VALIDATED;
                case REJECTED -> AdminActionType.CHALLENGE_REJECTED;
                case MANUAL_GRANT -> AdminActionType.CHALLENGE_GRANTED;
                case BONUS_ADJUSTED -> AdminActionType.CHALLENGE_BONUS_ADJUSTED;
            });
        }
        return result;
    }

    private static String challengePattern(String challenge) {
        if (!StringUtils.hasText(challenge)) return null;
        String escaped = challenge.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }

    private static boolean hasRole(Collection<String> authorities, String role) {
        if (authorities == null) return false;
        return authorities.stream().map(value -> value.startsWith("ROLE_") ? value.substring(5) : value)
                .anyMatch(role::equalsIgnoreCase);
    }

    private static String text(Object value) {
        if (!(value instanceof String text) || !StringUtils.hasText(text)) return null;
        return text;
    }

    private static Integer integer(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static UUID uuid(Object value) {
        if (!(value instanceof String text)) return null;
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
