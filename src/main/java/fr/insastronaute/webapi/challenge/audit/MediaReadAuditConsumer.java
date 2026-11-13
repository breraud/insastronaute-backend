package fr.insastronaute.webapi.challenge.audit;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class MediaReadAuditConsumer {
    private static final String GROUP = "postgres-audit";
    private static final String CONSUMER = "webapi";
    private final StringRedisTemplate redis;
    private final ModerationAuditService audit;
    public MediaReadAuditConsumer(StringRedisTemplate redis, ModerationAuditService audit) {
        this.redis=redis; this.audit=audit;
    }

    @Scheduled(fixedDelayString = "${app.moderation.audit-consumer-delay-ms:1000}")
    public void consume() {
        ensureGroup();
        List<MapRecord<String, Object, Object>> records;
        try {
            records = redis.opsForStream().read(Consumer.from(GROUP, CONSUMER),
                    StreamReadOptions.empty().count(100),
                    StreamOffset.create(MediaReadAuditPublisher.STREAM_KEY, ReadOffset.from("0")));
            if (records == null || records.isEmpty()) records = redis.opsForStream().read(Consumer.from(GROUP, CONSUMER),
                    StreamReadOptions.empty().count(100).block(Duration.ofMillis(100)),
                    StreamOffset.create(MediaReadAuditPublisher.STREAM_KEY, ReadOffset.lastConsumed()));
        } catch (DataAccessException exception) { return; }
        if (records == null) return;
        Map<AuditKey, Aggregate> aggregates = new LinkedHashMap<>();
        for (MapRecord<String, Object, Object> record : records) {
            Map<Object,Object> value=record.getValue();
            AuditKey key=new AuditKey(UUID.fromString(text(value,"submissionId")), text(value,"moderatorHash"),
                    Integer.parseInt(text(value,"mediaIndex")), channel(text(value,"channel")));
            aggregates.computeIfAbsent(key, ignored -> new Aggregate(UUID.fromString(text(value,"eventId"))))
                    .add(Long.parseLong(text(value,"bytes")), record.getId());
        }
        for (Map.Entry<AuditKey, Aggregate> entry : aggregates.entrySet()) {
            AuditKey key=entry.getKey(); Aggregate aggregate=entry.getValue();
            audit.recordMediaRead(aggregate.eventId, key.submissionId, key.moderatorHash, key.mediaIndex,
                    aggregate.bytes, aggregate.recordIds.size(), key.channel);
            redis.opsForStream().acknowledge(MediaReadAuditPublisher.STREAM_KEY, GROUP,
                    aggregate.recordIds.toArray(RecordId[]::new));
        }
    }
    private void ensureGroup() {
        try { redis.opsForStream().createGroup(MediaReadAuditPublisher.STREAM_KEY, ReadOffset.from("0-0"), GROUP); }
        catch (RuntimeException ignored) { }
    }
    private static String text(Map<Object,Object> map, String key) {
        Object value=map.get(key); return value == null ? null : value.toString();
    }
    // Un evenement publie avant le deploiement n'a pas de champ channel : c'etait la moderation.
    private static MediaReadChannel channel(String value) {
        return value == null ? MediaReadChannel.MODERATION : MediaReadChannel.valueOf(value);
    }
    private record AuditKey(UUID submissionId, String moderatorHash, int mediaIndex, MediaReadChannel channel) {}
    private static final class Aggregate {
        private final UUID eventId; private long bytes; private final List<RecordId> recordIds=new ArrayList<>();
        private Aggregate(UUID eventId) { this.eventId=eventId; }
        private void add(long value, RecordId recordId) { bytes += value; recordIds.add(recordId); }
    }
}
