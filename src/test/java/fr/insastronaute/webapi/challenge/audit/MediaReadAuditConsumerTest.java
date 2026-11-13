package fr.insastronaute.webapi.challenge.audit;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Le seul chemin de compatibilite du deploiement : les lectures publiees avant la
 * Mediatheque attendent encore dans le flux Redis, sans champ {@code channel}. Elles
 * venaient toutes de la moderation.
 */
class MediaReadAuditConsumerTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final StreamOperations<String, Object, Object> streams = mock(StreamOperations.class);
    private final ModerationAuditService audit = mock(ModerationAuditService.class);
    private final MediaReadAuditConsumer consumer = new MediaReadAuditConsumer(redis, audit);

    @Test
    void aReadPublishedBeforeTheChannelExistedIsAModerationRead() {
        UUID eventId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        givenTheStreamHolds(record("1-0", eventId, submissionId, null));

        consumer.consume();

        verify(audit).recordMediaRead(eq(eventId), eq(submissionId), eq("hash"), eq(0), eq(42L), eq(1),
                eq(MediaReadChannel.MODERATION));
    }

    @Test
    void aMediaLibraryReadKeepsItsChannel() {
        UUID eventId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        givenTheStreamHolds(record("2-0", eventId, submissionId, "MEDIA_LIBRARY"));

        consumer.consume();

        verify(audit).recordMediaRead(eq(eventId), eq(submissionId), eq("hash"), eq(0), eq(42L), eq(1),
                eq(MediaReadChannel.MEDIA_LIBRARY));
    }

    /** Une ancienne lecture et une nouvelle sur le meme media ne fusionnent pas. */
    @Test
    void oldAndNewReadsOfTheSameMediaStaySeparate() {
        UUID submissionId = UUID.randomUUID();
        givenTheStreamHolds(record("3-0", UUID.randomUUID(), submissionId, null),
                record("4-0", UUID.randomUUID(), submissionId, "MEDIA_LIBRARY"));

        consumer.consume();

        verify(audit).recordMediaRead(any(), eq(submissionId), eq("hash"), anyInt(), anyLong(), eq(1),
                eq(MediaReadChannel.MODERATION));
        verify(audit).recordMediaRead(any(), eq(submissionId), eq("hash"), anyInt(), anyLong(), eq(1),
                eq(MediaReadChannel.MEDIA_LIBRARY));
    }

    @SafeVarargs
    @SuppressWarnings("unchecked")
    private void givenTheStreamHolds(MapRecord<String, Object, Object>... records) {
        when(redis.opsForStream()).thenReturn(streams);
        when(streams.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(List.of(records));
    }

    private static MapRecord<String, Object, Object> record(String id, UUID eventId, UUID submissionId, String channel) {
        Map<Object, Object> fields = new HashMap<>();
        fields.put("eventId", eventId.toString());
        fields.put("submissionId", submissionId.toString());
        fields.put("moderatorHash", "hash");
        fields.put("mediaIndex", "0");
        fields.put("bytes", "42");
        if (channel != null) {
            fields.put("channel", channel);
        }
        return StreamRecords.<String, Object, Object>mapBacked(fields)
                .withStreamKey(MediaReadAuditPublisher.STREAM_KEY)
                .withId(RecordId.of(id));
    }
}
