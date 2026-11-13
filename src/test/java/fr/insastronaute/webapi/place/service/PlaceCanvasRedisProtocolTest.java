package fr.insastronaute.webapi.place.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisScriptingCommands;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings({"rawtypes", "unchecked"})
class PlaceCanvasRedisProtocolTest {

    @Test
    void luaUsesExactDecimalRevisionComparisonAndSafeIncrement() {
        String placement = new String(PlaceCanvasStore.TemplateRedisGateway.PLACE, StandardCharsets.UTF_8);
        String activation = new String(PlaceCanvasStore.TemplateRedisGateway.ACTIVATE, StandardCharsets.UTF_8);

        assertThat(placement).contains("increment_revision(revision)")
                .doesNotContain("tonumber(revision)");
        assertThat(activation).contains("revision ~= ARGV[1]", "increment_revision(revision)")
                .doesNotContain("tonumber(revision)", "tonumber(ARGV[1])");
        assertThat(placement).contains(Long.toString(Long.MAX_VALUE), "REVISION_OVERFLOW");
        assertThat(activation).contains(Long.toString(Long.MAX_VALUE), "REVISION_OVERFLOW");
        assertThat(new String(PlaceCanvasStore.TemplateRedisGateway.INITIALIZE, StandardCharsets.UTF_8))
                .contains("redis.call('DEL', ARGV[6] .. '1')");
    }

    @Test
    void resizeLockIsExclusiveOwnerSafeAndBlocksPlacementBeforeMutation() {
        String placement = new String(PlaceCanvasStore.TemplateRedisGateway.PLACE, StandardCharsets.UTF_8);
        String acquire = new String(PlaceCanvasStore.TemplateRedisGateway.ACQUIRE_RESIZE, StandardCharsets.UTF_8);
        String release = new String(PlaceCanvasStore.TemplateRedisGateway.RELEASE_RESIZE, StandardCharsets.UTF_8);
        String renew = new String(PlaceCanvasStore.TemplateRedisGateway.RENEW_RESIZE, StandardCharsets.UTF_8);

        assertThat(acquire).contains("SET', KEYS[2], ARGV[1], 'NX', 'PX'");
        assertThat(placement).contains("EXISTS', KEYS[2]", "RESIZE_LOCKED")
                .containsSubsequence("EXISTS', KEYS[2]", "SETRANGE");
        assertThat(release).contains("GET', KEYS[1]", "ARGV[1]", "DEL', KEYS[1]");
        assertThat(renew).contains("GET', KEYS[1]", "ARGV[1]", "PEXPIRE', KEYS[1]");
    }

    @Test
    void candidateActivationAndCompensationRequireResizeLockOwner() {
        String activation = new String(PlaceCanvasStore.TemplateRedisGateway.ACTIVATE, StandardCharsets.UTF_8);
        assertThat(activation).contains("GET', KEYS[2]", "ARGV[5]", "LOCK_NOT_OWNER", "PEXPIRE");
    }

    @Test
    void cleanupAndPngRetentionAreBoundedAndNeverDeleteActiveGeneration() {
        String cleanup = new String(PlaceCanvasStore.TemplateRedisGateway.DELETE_GENERATION, StandardCharsets.UTF_8);
        String png = new String(PlaceCanvasStore.TemplateRedisGateway.SET_PNG, StandardCharsets.UTF_8);
        assertThat(cleanup).contains("generation ~= ARGV[1]", "DEL");
        assertThat(png).contains("'PX'");
    }

    @Test
    void productionAdapterPreservesBinaryPixelArgumentAndParsesLargeRevision() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        RedisConnection connection = mock(RedisConnection.class);
        RedisScriptingCommands scripting = mock(RedisScriptingCommands.class);
        when(connection.scriptingCommands()).thenReturn(scripting);
        when(template.execute(any(RedisCallback.class))).thenAnswer(invocation ->
                invocation.<RedisCallback<?>>getArgument(0).doInRedis(connection));
        when(scripting.eval(any(byte[].class), any(ReturnType.class), anyInt(), any(byte[][].class)))
                .thenAnswer(invocation -> {
                    assertThat((byte[]) invocation.getArgument(3))
                            .isEqualTo("place:canvas:active".getBytes(StandardCharsets.UTF_8));
                    assertThat((byte[]) invocation.getArgument(8))
                            .containsExactly((byte) 0x80, (byte) 0xFF, 0);
                    return "1,9007199254740993,1,1".getBytes(StandardCharsets.UTF_8);
                });
        PlaceCanvasStore.TemplateRedisGateway gateway = new PlaceCanvasStore.TemplateRedisGateway(template);

        PlaceCanvasDescriptor result = gateway.placePixel("place:canvas:active", "place:canvas:rgb:",
                0, 0, new byte[]{(byte) 0x80, (byte) 0xFF, 0});

        assertThat(result.revision()).isEqualTo(9_007_199_254_740_993L);
    }

    @Test
    void productionAdapterDecodesAtomicDescriptorAndBinaryBufferRead() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        RedisConnection connection = mock(RedisConnection.class);
        RedisScriptingCommands scripting = mock(RedisScriptingCommands.class);
        when(connection.scriptingCommands()).thenReturn(scripting);
        when(template.execute(any(RedisCallback.class))).thenAnswer(invocation ->
                invocation.<RedisCallback<?>>getArgument(0).doInRedis(connection));
        when(scripting.eval(any(byte[].class), any(ReturnType.class), anyInt(), any(byte[][].class)))
                .thenReturn(List.of("g,9007199254740993,2,1".getBytes(StandardCharsets.UTF_8),
                        new byte[]{0, (byte) 0x80, (byte) 0xFF, 1, 2, 3}));
        PlaceCanvasStore.TemplateRedisGateway gateway = new PlaceCanvasStore.TemplateRedisGateway(template);

        PlaceCanvasStore.CanvasRead read = gateway.read("place:canvas:active", "place:canvas:rgb:");

        assertThat(read.descriptor()).isEqualTo(new PlaceCanvasDescriptor("g", 9_007_199_254_740_993L, 2, 1));
        assertThat(read.rgb()).containsExactly(0, (byte) 0x80, (byte) 0xFF, 1, 2, 3);
    }

    @Test
    void productionAdapterSendsExactLargeCasRevisionAndReportsFailure() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        RedisConnection connection = mock(RedisConnection.class);
        RedisScriptingCommands scripting = mock(RedisScriptingCommands.class);
        when(connection.scriptingCommands()).thenReturn(scripting);
        when(template.execute(any(RedisCallback.class))).thenAnswer(invocation ->
                invocation.<RedisCallback<?>>getArgument(0).doInRedis(connection));
        when(scripting.eval(any(byte[].class), any(ReturnType.class), anyInt(), any(byte[][].class)))
                .thenAnswer(invocation -> {
                    assertThat((byte[]) invocation.getArgument(4))
                            .isEqualTo("9007199254740993".getBytes(StandardCharsets.US_ASCII));
                    return "0".getBytes(StandardCharsets.US_ASCII);
                });
        PlaceCanvasStore.TemplateRedisGateway gateway = new PlaceCanvasStore.TemplateRedisGateway(template);

        assertThat(gateway.activate("place:canvas:active", 9_007_199_254_740_993L, "g", 1, 1)).isFalse();
    }

    @Test
    void productionAdapterMapsRevisionOverflowWithoutParsingAsDescriptor() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        RedisConnection connection = mock(RedisConnection.class);
        RedisScriptingCommands scripting = mock(RedisScriptingCommands.class);
        when(connection.scriptingCommands()).thenReturn(scripting);
        when(template.execute(any(RedisCallback.class))).thenAnswer(invocation ->
                invocation.<RedisCallback<?>>getArgument(0).doInRedis(connection));
        when(scripting.eval(any(byte[].class), any(ReturnType.class), anyInt(), any(byte[][].class)))
                .thenReturn("REVISION_OVERFLOW".getBytes(StandardCharsets.US_ASCII));
        PlaceCanvasStore.TemplateRedisGateway gateway = new PlaceCanvasStore.TemplateRedisGateway(template);

        assertThatThrownBy(() -> gateway.placePixel("place:canvas:active", "place:canvas:rgb:",
                0, 0, new byte[]{1, 2, 3}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("La révision du canevas a atteint sa valeur maximale.");
    }
}
