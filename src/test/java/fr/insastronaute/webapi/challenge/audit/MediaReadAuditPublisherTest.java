package fr.insastronaute.webapi.challenge.audit;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class MediaReadAuditPublisherTest {
    @Test
    void failsClosedWhenRedisCannotJournalTheRead() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForStream()).thenThrow(new RedisConnectionFailureException("down"));
        MediaReadAuditPublisher publisher = new MediaReadAuditPublisher(redis);

        assertThatThrownBy(() -> publisher.publish(UUID.randomUUID(), "moderator", 0, 42))
                .isInstanceOf(ModerationAuditUnavailableException.class);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void writesTheChannelOfTheRead() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        StreamOperations<String, Object, Object> streams = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(streams);
        MediaReadAuditPublisher publisher = new MediaReadAuditPublisher(redis);

        publisher.publish(UUID.randomUUID(), "moderator", 2, 42, MediaReadChannel.MEDIA_LIBRARY);
        publisher.publish(UUID.randomUUID(), "moderator", 0, 10);

        ArgumentCaptor<MapRecord> records = ArgumentCaptor.forClass(MapRecord.class);
        verify(streams, times(2)).add(records.capture());
        assertThat((java.util.Map<String, String>) records.getAllValues().get(0).getValue()).containsEntry("channel", "MEDIA_LIBRARY");
        assertThat((java.util.Map<String, String>) records.getAllValues().get(1).getValue()).containsEntry("channel", "MODERATION");
    }
}
