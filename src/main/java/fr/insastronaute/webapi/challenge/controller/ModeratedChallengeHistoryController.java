package fr.insastronaute.webapi.challenge.controller;

import fr.insastronaute.webapi.challenge.dto.ModeratedChallengeHistoryDto;
import fr.insastronaute.webapi.challenge.dto.ModerationHistoryType;
import fr.insastronaute.webapi.challenge.service.ModeratedChallengeHistoryService;
import fr.insastronaute.webapi.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/moderation/history")
@Tag(name = "Moderation")
public class ModeratedChallengeHistoryController {
    private final ModeratedChallengeHistoryService history;

    public ModeratedChallengeHistoryController(ModeratedChallengeHistoryService history) {
        this.history = history;
    }

    @GetMapping
    @Operation(summary = "List moderated challenges", description = "Returns validations, rejections and manual grants. "
            + "Administrators and challenge leads see validator identities; other moderators only see their own.")
    public PageResponse<ModeratedChallengeHistoryDto> search(
            @AuthenticationPrincipal OidcUser oidcUser,
            @RequestParam(required = false) UUID playerUserId,
            @RequestParam(required = false) UUID actorUserId,
            @RequestParam(required = false) String challenge,
            @RequestParam(required = false) Collection<ModerationHistoryType> type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        return history.search(oidcUser.getSubject(), authorities(oidcUser), playerUserId, actorUserId,
                challenge, type, from, to, direction, page, size);
    }

    private static Set<String> authorities(OidcUser oidcUser) {
        return oidcUser.getAuthorities().stream()
                .map(authority -> authority == null ? null : authority.getAuthority())
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
    }
}
