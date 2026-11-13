package fr.insastronaute.webapi.challenge.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.challenge.dto.AdminSubmissionAuditDto;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModerationAuditService {
    private final ModerationAuditRepository repository;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    public ModerationAuditService(
            ModerationAuditRepository repository,
            ObjectMapper objectMapper,
            UserRepository userRepository
    ) {
        this.repository=repository; this.objectMapper=objectMapper; this.userRepository=userRepository;
    }
    public void record(UUID submissionId, String subject, String type, String outcome, Map<String, ?> metadata) {
        metadata.keySet().forEach(key -> {
            String normalized = key.toLowerCase(java.util.Locale.ROOT);
            if (normalized.contains("proof") || normalized.contains("token") || normalized.contains("cookie")
                    || normalized.contains("range") || normalized.contains("url")) {
                throw new IllegalArgumentException("Métadonnée d'audit sensible interdite.");
            }
        });
        try {
            String hash=hashSubject(subject);
            repository.save(new ModerationAuditEvent(submissionId, hash, type, outcome,
                    objectMapper.writeValueAsString(metadata)));
        } catch (Exception exception) {
            throw new IllegalStateException("Impossible de journaliser l'action de modération.", exception);
        }
    }

    public static String hashSubject(String subject) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(subject.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 indisponible.", exception);
        }
    }

    @Transactional
    public void recordMediaRead(UUID eventId, UUID submissionId, String moderatorHash, int mediaIndex, long bytes) {
        recordMediaRead(eventId, submissionId, moderatorHash, mediaIndex, bytes, 1);
    }

    @Transactional
    public void recordMediaRead(UUID eventId, UUID submissionId, String moderatorHash, int mediaIndex, long bytes, int requestCount) {
        recordMediaRead(eventId, submissionId, moderatorHash, mediaIndex, bytes, requestCount, MediaReadChannel.MODERATION);
    }

    @Transactional
    public void recordMediaRead(UUID eventId, UUID submissionId, String moderatorHash, int mediaIndex, long bytes,
                                int requestCount, MediaReadChannel channel) {
        if (repository.existsById(eventId)) return;
        try {
            repository.save(new ModerationAuditEvent(eventId, submissionId, moderatorHash,
                    "MEDIA_READ", "SUCCESS", objectMapper.writeValueAsString(
                    Map.of("mediaIndex", mediaIndex, "bytes", bytes, "requestCount", requestCount,
                            "channel", channel.name()))));
        } catch (Exception exception) {
            throw new IllegalStateException("Impossible de persister la lecture média.", exception);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDenied(UUID submissionId, String subject, String reason) {
        record(submissionId, subject, "ACCESS_DENIED", "DENIED", Map.of("reason", reason));
    }

    /**
     * Retenir la trace de soumissions qui vont disparaitre.
     *
     * <p>Appelee juste avant la suppression, parce qu'apres il n'y a plus rien a
     * nommer : la base cascade sans estampiller. Ce que cette table garde -- quel
     * moderateur a ouvert quel media, et quand -- n'a pas de double ailleurs, a la
     * difference de la decision elle-meme, qui est aussi au registre
     * d'administration.</p>
     *
     * @return le nombre de lignes d'audit conservees, pour le dire a qui supprime
     */
    @Transactional
    public int markSubmissionsDeleted(Collection<UUID> submissionIds, Instant deletedAt) {
        if (submissionIds == null || submissionIds.isEmpty()) {
            return 0;
        }
        return repository.markSubmissionsDeleted(submissionIds, deletedAt);
    }

    @Transactional(readOnly = true)
    public AdminSubmissionAuditDto getSubmissionAudit(UUID submissionId) {
        Map<String, String> actorNames = new HashMap<>();
        for (AppUser user : userRepository.findAll()) {
            actorNames.put(hashSubject(user.getKeycloakSub()), displayName(user));
        }

        AdminSubmissionAuditDto.Decision decision = null;
        List<AdminSubmissionAuditDto.MediaConsultation> consultations = new java.util.ArrayList<>();
        for (ModerationAuditEvent event : repository.findBySubmissionIdOrderByCreatedAtAsc(submissionId)) {
            String actorName = actorNames.getOrDefault(event.moderatorHash(), "Utilisateur inconnu");
            try {
                var metadata = objectMapper.readTree(event.metadataJson());
                if ("DECISION".equals(event.eventType()) && "SUCCESS".equals(event.outcome())) {
                    String status = metadata.path("status").asText();
                    if ("VALIDATED".equals(status) || "REJECTED".equals(status)) {
                        decision = new AdminSubmissionAuditDto.Decision(status, actorName, event.createdAt());
                    }
                } else if ("MEDIA_READ".equals(event.eventType()) && "SUCCESS".equals(event.outcome())) {
                    consultations.add(new AdminSubmissionAuditDto.MediaConsultation(
                            actorName, metadata.path("mediaIndex").asInt(), event.createdAt(),
                            metadata.path("channel").asText("MODERATION")));
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Impossible de lire l'audit de modération.", exception);
            }
        }
        return new AdminSubmissionAuditDto(decision, List.copyOf(consultations));
    }

    private static String displayName(AppUser user) {
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName();
        }
        return "Utilisateur inconnu";
    }
}
