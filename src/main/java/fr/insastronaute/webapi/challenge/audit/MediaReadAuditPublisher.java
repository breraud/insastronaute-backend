package fr.insastronaute.webapi.challenge.audit;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.stereotype.Service;

@Service
public class MediaReadAuditPublisher {
    public static final String STREAM_KEY = "insastronaute:moderation:media-audit";
    private final StringRedisTemplate redis;
    public MediaReadAuditPublisher(StringRedisTemplate redis) { this.redis = redis; }

    public UUID publish(UUID submissionId, String moderatorSubject, int mediaIndex, long bytes) {
        return publish(submissionId, moderatorSubject, mediaIndex, bytes, MediaReadChannel.MODERATION);
    }

    public UUID publish(UUID submissionId, String moderatorSubject, int mediaIndex, long bytes, MediaReadChannel channel) {
        UUID eventId = UUID.randomUUID();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("eventId", eventId.toString());
        fields.put("submissionId", submissionId.toString());
        fields.put("moderatorHash", ModerationAuditService.hashSubject(moderatorSubject));
        fields.put("mediaIndex", Integer.toString(mediaIndex));
        fields.put("bytes", Long.toString(bytes));
        fields.put("channel", channel.name());
        fields.put("occurredAt", fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow().toString());
        try {
            redis.opsForStream().add(StreamRecords.mapBacked(fields).withStreamKey(STREAM_KEY));
            return eventId;
        } catch (RuntimeException exception) {
            throw new ModerationAuditUnavailableException(exception);
        }
    }
}
