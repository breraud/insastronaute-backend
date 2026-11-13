package fr.insastronaute.webapi.place.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Le canevas de la fresque, dans Redis.
 *
 * <p>Il n'y en a qu'un. La fresque des departements n'a pas de canevas a elle :
 * c'est une lecture de celui-ci (PlaceDepartmentFresqueService).</p>
 */
@Component
public class PlaceCanvasStore {

    static final String DESCRIPTOR_KEY = "place:canvas:active";
    static final String RGB_KEY_PREFIX = "place:canvas:rgb:";
    static final String PNG_KEY_PREFIX = "place:canvas:png:";
    static final String RESIZE_LOCK_KEY = "place:canvas:resize-lock";
    static final Duration RESIZE_LEASE = Duration.ofSeconds(30);
    static final Duration RESIZE_HEARTBEAT = Duration.ofSeconds(5);
    static final Duration PNG_RETENTION = Duration.ofHours(24);

    private final RedisGateway redis;
    private final String descriptorKey = DESCRIPTOR_KEY;
    private final String rgbKeyPrefix = RGB_KEY_PREFIX;
    private final String pngKeyPrefix = PNG_KEY_PREFIX;
    private final String resizeLockKey = RESIZE_LOCK_KEY;
    private final Duration resizeLease;
    private final Duration resizeHeartbeat;

    @Autowired
    public PlaceCanvasStore(StringRedisTemplate redisTemplate) {
        this(new TemplateRedisGateway(redisTemplate), RESIZE_LEASE, RESIZE_HEARTBEAT);
    }

    PlaceCanvasStore(RedisGateway redis) {
        this(redis, RESIZE_LEASE, RESIZE_HEARTBEAT);
    }

    PlaceCanvasStore(RedisGateway redis, Duration resizeLease, Duration resizeHeartbeat) {
        this.redis = redis;
        this.resizeLease = resizeLease;
        this.resizeHeartbeat = resizeHeartbeat;
        if (resizeHeartbeat.isZero() || resizeHeartbeat.isNegative()
                || resizeHeartbeat.compareTo(resizeLease) >= 0) {
            throw new IllegalArgumentException("Le heartbeat doit être positif et inférieur au bail.");
        }
    }

    public PlaceCanvasDescriptor initialize(int width, int height) {
        int byteCount = Math.multiplyExact(Math.multiplyExact(width, height), 3);
        return redis.initialize(descriptorKey, rgbKeyPrefix, "1", pngKeyPrefix, width, height, byteCount);
    }

    public PlaceCanvasDescriptor descriptor() {
        return readCanvas().descriptor();
    }

    public CanvasRead readCanvas() {
        return redis.read(descriptorKey, rgbKeyPrefix);
    }

    public PlaceCanvasDescriptor placePixel(int x, int y, String color) {
        return redis.placePixel(descriptorKey, rgbKeyPrefix, x, y, rgb(color));
    }

    public ResizeLease acquireResizeLease(int width, int height) {
        initialize(width, height);
        String owner = UUID.randomUUID().toString();
        if (!redis.acquireResize(descriptorKey, resizeLockKey, owner, resizeLease.toMillis())) return null;
        ResizeLease lease = new ResizeLease(owner);
        try {
            lease.startHeartbeat(() -> lease.isStopped() || redis.renewResize(
                    resizeLockKey, owner, resizeLease.toMillis()), resizeHeartbeat);
        } catch (RuntimeException | Error startupFailure) {
            redis.releaseResize(resizeLockKey, owner);
            throw startupFailure;
        }
        return lease;
    }

    public void releaseResizeLease(ResizeLease lease) {
        lease.stopHeartbeat();
        redis.releaseResize(resizeLockKey, lease.owner());
    }

    public void assertResizeLeaseOwned(ResizeLease lease) {
        lease.assertOwned();
        if (!redis.renewResize(resizeLockKey, lease.owner(), resizeLease.toMillis())) {
            lease.markLost();
            throw new PlaceResizeLockedException();
        }
    }

    public boolean activateResizedGeneration(long expectedRevision, int width, int height, byte[] rgb) {
        int expectedLength = Math.multiplyExact(Math.multiplyExact(width, height), 3);
        if (rgb.length != expectedLength) {
            throw new IllegalArgumentException("La taille RGB ne correspond pas aux dimensions du canevas.");
        }
        String generation = UUID.randomUUID().toString();
        redis.set(rgbKeyPrefix + generation, rgb);
        return redis.activate(descriptorKey, expectedRevision, generation, width, height);
    }

    public ResizeActivation activateResizedGeneration(ResizeLease lease, long expectedRevision,
                                                       int width, int height, byte[] rgb) {
        lease.assertOwned();
        int expectedLength = Math.multiplyExact(Math.multiplyExact(width, height), 3);
        if (rgb.length != expectedLength) throw new IllegalArgumentException("La taille RGB ne correspond pas aux dimensions du canevas.");
        String generation = UUID.randomUUID().toString();
        redis.set(rgbKeyPrefix + generation, rgb);
        boolean active = redis.activateLocked(descriptorKey, resizeLockKey, expectedRevision,
                generation, width, height, lease.owner());
        if (!active) redis.deleteGeneration(descriptorKey, rgbKeyPrefix, generation);
        return active ? new ResizeActivation(generation, Math.incrementExact(expectedRevision)) : null;
    }

    /** Reactivates an already stored generation for compensating a failed resize orchestration. */
    public boolean activateExistingGeneration(long expectedRevision, String generation, int width, int height) {
        return redis.activate(descriptorKey, expectedRevision, generation, width, height);
    }

    public boolean activateExistingGeneration(ResizeLease lease, long expectedRevision, String generation,
                                              int width, int height) {
        lease.assertOwned();
        return redis.activateLocked(descriptorKey, resizeLockKey, expectedRevision,
                generation, width, height, lease.owner());
    }

    public void deleteGeneration(String generation) {
        redis.deleteGeneration(descriptorKey, rgbKeyPrefix, generation);
    }

    public PlaceCanvasSnapshot snapshot() {
        CanvasRead canvas = readCanvas();
        long revision = canvas.descriptor().revision();
        String cacheKey = pngKeyPrefix + revision;
        byte[] png = redis.get(cacheKey);
        if (png == null) {
            png = renderPng(canvas.rgb(), canvas.descriptor().width(), canvas.descriptor().height());
            redis.setPng(cacheKey, png, PNG_RETENTION.toMillis());
        }
        return new PlaceCanvasSnapshot(png, canvas.descriptor());
    }

    // Compatibility for the current PlaceService; Task 4 can migrate to revision-returning placement.
    public void writePixel(int x, int y, int width, int height, String color) {
        ensureInitialized(width, height);
        placePixel(x, y, color);
    }

    // Compatibility for the current controller boundary; dimensions come from the atomic descriptor.
    public byte[] png(int width, int height) {
        ensureInitialized(width, height);
        return snapshot().png();
    }

    private void ensureInitialized(int width, int height) {
        initialize(width, height);
    }

    static byte[] renderPng(byte[] canvas, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int expectedBytes = Math.multiplyExact(Math.multiplyExact(width, height), 3);
        byte[] source = canvas == null ? new byte[0] : canvas;
        for (int index = 0; index < expectedBytes; index += 3) {
            int red = index < source.length ? Byte.toUnsignedInt(source[index]) : 0;
            int green = index + 1 < source.length ? Byte.toUnsignedInt(source[index + 1]) : 0;
            int blue = index + 2 < source.length ? Byte.toUnsignedInt(source[index + 2]) : 0;
            int pixel = index / 3;
            image.setRGB(pixel % width, pixel / width, (red << 16) | (green << 8) | blue);
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Impossible de générer l'image du canevas.", exception);
        }
    }

    static byte[] rgb(String color) {
        return new byte[]{(byte) Integer.parseInt(color.substring(1, 3), 16),
                (byte) Integer.parseInt(color.substring(3, 5), 16),
                (byte) Integer.parseInt(color.substring(5, 7), 16)};
    }

    public record CanvasRead(byte[] rgb, PlaceCanvasDescriptor descriptor) {
        public CanvasRead { rgb = rgb.clone(); }
        @Override public byte[] rgb() { return rgb.clone(); }
    }
    public static final class ResizeLease {
        private final String owner;
        private final AtomicBoolean lost = new AtomicBoolean();
        private final AtomicBoolean stopped = new AtomicBoolean();
        private ScheduledExecutorService heartbeatExecutor;
        private ScheduledFuture<?> heartbeat;

        public ResizeLease(String owner) { this.owner = owner; }
        public String owner() { return owner; }
        boolean isStopped() { return stopped.get(); }

        private void startHeartbeat(java.util.function.BooleanSupplier renew, Duration interval) {
            heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(
                    Thread.ofVirtual().name("place-resize-heartbeat-" + owner).factory());
            heartbeat = heartbeatExecutor.scheduleAtFixedRate(() -> {
                try {
                    if (!renew.getAsBoolean()) lost.set(true);
                } catch (RuntimeException | Error failure) {
                    lost.set(true);
                }
            }, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
        }

        private void stopHeartbeat() {
            stopped.set(true);
            if (heartbeat != null) heartbeat.cancel(true);
            if (heartbeatExecutor != null) heartbeatExecutor.shutdownNow();
        }

        private void assertOwned() {
            if (lost.get()) throw new PlaceResizeLockedException();
        }

        private void markLost() { lost.set(true); }
    }
    public record ResizeActivation(String generation, long revision) {}

    interface RedisGateway {
        PlaceCanvasDescriptor initialize(String descriptorKey, String rgbPrefix, String generation,
                                         String pngPrefix, int width, int height, int byteCount);
        CanvasRead read(String descriptorKey, String rgbPrefix);
        PlaceCanvasDescriptor placePixel(String descriptorKey, String rgbPrefix, int x, int y, byte[] rgb);
        boolean activate(String descriptorKey, long expectedRevision, String generation, int width, int height);
        byte[] get(String key);
        void set(String key, byte[] value);
        default boolean acquireResize(String descriptorKey, String lockKey, String owner, long leaseMs) { return true; }
        default boolean renewResize(String lockKey, String owner, long leaseMs) { return true; }
        default boolean releaseResize(String lockKey, String owner) { return true; }
        default boolean activateLocked(String descriptorKey, String lockKey, long expectedRevision,
                                       String generation, int width, int height, String owner) {
            return activate(descriptorKey, expectedRevision, generation, width, height);
        }
        default void deleteGeneration(String descriptorKey, String prefix, String generation) {}
        default void setPng(String key, byte[] value, long ttlMs) { set(key, value); }
    }

    static final class TemplateRedisGateway implements RedisGateway {
        static final byte[] INITIALIZE = script("""
                local current = redis.call('GET', KEYS[1])
                if current then return current end
                local descriptor = ARGV[1] .. ',1,' .. ARGV[2] .. ',' .. ARGV[3]
                redis.call('SET', ARGV[5] .. ARGV[1], string.rep(string.char(0), tonumber(ARGV[4])))
                redis.call('DEL', ARGV[6] .. '1')
                redis.call('SET', KEYS[1], descriptor)
                return descriptor
                """);
        static final byte[] READ = script("""
                local descriptor = redis.call('GET', KEYS[1])
                if not descriptor then return nil end
                local generation = string.match(descriptor, '^([^,]+)')
                return {descriptor, redis.call('GET', ARGV[1] .. generation)}
                """);
        static final byte[] PLACE = script("""
                local function increment_revision(value)
                    if value == '9223372036854775807' then return nil end
                    local result = ''
                    local carry = 1
                    for index = string.len(value), 1, -1 do
                        local digit = string.byte(value, index) - 48 + carry
                        if digit == 10 then digit = 0 else carry = 0 end
                        result = string.char(digit + 48) .. result
                    end
                    if carry == 1 then result = '1' .. result end
                    return result
                end
                if redis.call('EXISTS', KEYS[2]) == 1 then return 'RESIZE_LOCKED' end
                local descriptor = redis.call('GET', KEYS[1])
                if not descriptor then return nil end
                local generation, revision, width, height = string.match(descriptor, '^([^,]+),([^,]+),([^,]+),([^,]+)$')
                local x, y = tonumber(ARGV[2]), tonumber(ARGV[3])
                if x < 0 or y < 0 or x >= tonumber(width) or y >= tonumber(height) then return redis.error_reply('pixel out of bounds') end
                local next_revision = increment_revision(revision)
                if not next_revision then return 'REVISION_OVERFLOW' end
                redis.call('SETRANGE', ARGV[1] .. generation, (y * tonumber(width) + x) * 3, ARGV[4])
                local next = generation .. ',' .. next_revision .. ',' .. width .. ',' .. height
                redis.call('SET', KEYS[1], next)
                return next
                """);
        static final byte[] ACTIVATE = script("""
                local function increment_revision(value)
                    if value == '9223372036854775807' then return nil end
                    local result = ''
                    local carry = 1
                    for index = string.len(value), 1, -1 do
                        local digit = string.byte(value, index) - 48 + carry
                        if digit == 10 then digit = 0 else carry = 0 end
                        result = string.char(digit + 48) .. result
                    end
                    if carry == 1 then result = '1' .. result end
                    return result
                end
                if KEYS[2] and redis.call('GET', KEYS[2]) ~= ARGV[5] then return 'LOCK_NOT_OWNER' end
                if KEYS[2] then redis.call('PEXPIRE', KEYS[2], ARGV[6]) end
                local descriptor = redis.call('GET', KEYS[1])
                if not descriptor then return '0' end
                local generation, revision = string.match(descriptor, '^([^,]+),([^,]+)')
                if revision ~= ARGV[1] then return '0' end
                local next_revision = increment_revision(revision)
                if not next_revision then return 'REVISION_OVERFLOW' end
                redis.call('SET', KEYS[1], ARGV[2] .. ',' .. next_revision .. ',' .. ARGV[3] .. ',' .. ARGV[4])
                return '1'
                """);
        static final byte[] ACQUIRE_RESIZE = script("""
                if not redis.call('GET', KEYS[1]) then return '0' end
                local acquired = redis.call('SET', KEYS[2], ARGV[1], 'NX', 'PX', ARGV[2])
                if acquired then return '1' end
                return '0'
                """);
        static final byte[] RELEASE_RESIZE = script("""
                if redis.call('GET', KEYS[1]) == ARGV[1] then
                    redis.call('DEL', KEYS[1])
                    return '1'
                end
                return '0'
                """);
        static final byte[] RENEW_RESIZE = script("""
                if redis.call('GET', KEYS[1]) == ARGV[1] then
                    redis.call('PEXPIRE', KEYS[1], ARGV[2])
                    return '1'
                end
                return '0'
                """);
        static final byte[] DELETE_GENERATION = script("""
                local descriptor = redis.call('GET', KEYS[1])
                local generation = descriptor and string.match(descriptor, '^([^,]+)')
                if generation ~= ARGV[1] then redis.call('DEL', ARGV[2] .. ARGV[1]) return '1' end
                return '0'
                """);
        static final byte[] SET_PNG = script("""
                redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
                return '1'
                """);

        private final StringRedisTemplate template;
        TemplateRedisGateway(StringRedisTemplate template) { this.template = template; }

        @Override
        public PlaceCanvasDescriptor initialize(String key, String prefix, String generation, String pngPrefix,
                                                int width, int height, int count) {
            byte[] result = eval(INITIALIZE, ReturnType.VALUE, key, bytes(generation), bytes(width), bytes(height),
                    bytes(count), bytes(prefix), bytes(pngPrefix));
            return parse(result);
        }

        @Override
        public CanvasRead read(String key, String prefix) {
            @SuppressWarnings("unchecked") List<byte[]> result = eval(READ, ReturnType.MULTI, key, bytes(prefix));
            if (result == null) throw new IllegalStateException("Le canevas Redis n'est pas initialisé.");
            return new CanvasRead(result.get(1), parse(result.get(0)));
        }

        @Override
        public PlaceCanvasDescriptor placePixel(String key, String prefix, int x, int y, byte[] rgb) {
            byte[] result = eval(PLACE, ReturnType.VALUE, new String[]{key, RESIZE_LOCK_KEY},
                    bytes(prefix), bytes(x), bytes(y), rgb);
            if (result == null) throw new IllegalStateException("Le canevas Redis n'est pas initialisé.");
            if ("RESIZE_LOCKED".equals(new String(result, StandardCharsets.US_ASCII))) throw new PlaceResizeLockedException();
            throwIfOverflow(result);
            return parse(result);
        }

        @Override
        public boolean activate(String key, long expected, String generation, int width, int height) {
            byte[] result = eval(ACTIVATE, ReturnType.VALUE, new String[]{key},
                    bytes(expected), bytes(generation), bytes(width), bytes(height), bytes(""));
            throwIfOverflow(result);
            return result != null && "1".equals(new String(result, StandardCharsets.US_ASCII));
        }

        @Override public boolean acquireResize(String descriptorKey, String lockKey, String owner, long leaseMs) {
            byte[] result = eval(ACQUIRE_RESIZE, ReturnType.VALUE, new String[]{descriptorKey, lockKey},
                    bytes(owner), bytes(leaseMs));
            return result != null && "1".equals(new String(result, StandardCharsets.US_ASCII));
        }
        @Override public boolean releaseResize(String lockKey, String owner) {
            byte[] result = eval(RELEASE_RESIZE, ReturnType.VALUE, new String[]{lockKey}, bytes(owner));
            return result != null && "1".equals(new String(result, StandardCharsets.US_ASCII));
        }
        @Override public boolean renewResize(String lockKey, String owner, long leaseMs) {
            byte[] result = eval(RENEW_RESIZE, ReturnType.VALUE, new String[]{lockKey},
                    bytes(owner), bytes(leaseMs));
            return result != null && "1".equals(new String(result, StandardCharsets.US_ASCII));
        }
        @Override public boolean activateLocked(String descriptorKey, String lockKey, long expected,
                                                String generation, int width, int height, String owner) {
            byte[] result = eval(ACTIVATE, ReturnType.VALUE, new String[]{descriptorKey, lockKey},
                    bytes(expected), bytes(generation), bytes(width), bytes(height), bytes(owner),
                    bytes(RESIZE_LEASE.toMillis()));
            throwIfOverflow(result);
            if (result != null && "LOCK_NOT_OWNER".equals(new String(result, StandardCharsets.US_ASCII)))
                throw new PlaceResizeLockedException();
            return result != null && "1".equals(new String(result, StandardCharsets.US_ASCII));
        }
        @Override public void deleteGeneration(String descriptorKey, String prefix, String generation) {
            eval(DELETE_GENERATION, ReturnType.VALUE, new String[]{descriptorKey}, bytes(generation), bytes(prefix));
        }
        @Override public void setPng(String key, byte[] value, long ttlMs) {
            eval(SET_PNG, ReturnType.VALUE, new String[]{key}, value, bytes(ttlMs));
        }

        @Override public byte[] get(String key) {
            return template.execute((RedisCallback<byte[]>) c -> c.stringCommands().get(bytes(key)));
        }
        @Override public void set(String key, byte[] value) {
            template.execute((RedisCallback<Void>) c -> { c.stringCommands().set(bytes(key), value); return null; });
        }

        @SuppressWarnings("unchecked")
        private <T> T eval(byte[] script, ReturnType type, String key, byte[]... arguments) {
            return eval(script, type, new String[]{key}, arguments);
        }
        @SuppressWarnings("unchecked")
        private <T> T eval(byte[] script, ReturnType type, String[] keys, byte[]... arguments) {
            byte[][] keysAndArgs = new byte[arguments.length + keys.length][];
            for (int i = 0; i < keys.length; i++) keysAndArgs[i] = bytes(keys[i]);
            System.arraycopy(arguments, 0, keysAndArgs, keys.length, arguments.length);
            return (T) template.execute((RedisCallback<Object>) c ->
                    c.scriptingCommands().eval(script, type, keys.length, keysAndArgs));
        }
        private static PlaceCanvasDescriptor parse(byte[] value) {
            String[] fields = new String(value, StandardCharsets.UTF_8).split(",");
            return new PlaceCanvasDescriptor(fields[0], Long.parseLong(fields[1]),
                    Integer.parseInt(fields[2]), Integer.parseInt(fields[3]));
        }
        private static void throwIfOverflow(byte[] result) {
            if (result != null && "REVISION_OVERFLOW".equals(new String(result, StandardCharsets.US_ASCII))) {
                throw new IllegalStateException("La révision du canevas a atteint sa valeur maximale.");
            }
        }
        private static byte[] bytes(Object value) { return value.toString().getBytes(StandardCharsets.UTF_8); }
        private static byte[] script(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    }
}
