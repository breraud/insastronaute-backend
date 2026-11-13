package fr.insastronaute.webapi.place.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaceCanvasStoreTest {

    @Test
    void initializesAtRevisionOneAndReadsMatchingBytesAndDimensions() {
        FakeRedisGateway redis = new FakeRedisGateway();
        PlaceCanvasStore store = new PlaceCanvasStore(redis);

        PlaceCanvasDescriptor initialized = store.initialize(2, 3);
        PlaceCanvasStore.CanvasRead read = store.readCanvas();

        assertThat(initialized).isEqualTo(new PlaceCanvasDescriptor("1", 1, 2, 3));
        assertThat(read.descriptor()).isEqualTo(initialized);
        assertThat(read.rgb()).containsExactly(new byte[18]);
        assertThat(redis.bytes).containsKey("place:canvas:rgb:1");
    }

    @Test
    void initializationRemovesStaleRevisionOnePng() {
        FakeRedisGateway redis = new FakeRedisGateway();
        redis.set("place:canvas:png:1", new byte[]{9, 9, 9});
        PlaceCanvasStore store = new PlaceCanvasStore(redis);

        store.initialize(1, 1);

        assertThat(redis.get("place:canvas:png:1")).isNull();
        assertThat(store.snapshot().png()).isNotEqualTo(new byte[]{9, 9, 9});
    }

    @Test
    void revisionsAboveJavaScriptSafeIntegerIncrementExactly() {
        FakeRedisGateway redis = initializedAtRevision(9_007_199_254_740_992L);
        PlaceCanvasStore store = new PlaceCanvasStore(redis);

        assertThat(store.placePixel(0, 0, "#80FF00").revision())
                .isEqualTo(9_007_199_254_740_993L);
        assertThat(store.activateExistingGeneration(9_007_199_254_740_992L, "1", 1, 1)).isFalse();
    }

    @Test
    void placementAtLongMaxFailsWithoutMutatingRgbOrDescriptor() {
        FakeRedisGateway redis = initializedAtRevision(Long.MAX_VALUE);
        PlaceCanvasStore store = new PlaceCanvasStore(redis);

        assertThatThrownBy(() -> store.placePixel(0, 0, "#FFFFFF"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("La révision du canevas a atteint sa valeur maximale.");

        assertThat(store.readCanvas().descriptor().revision()).isEqualTo(Long.MAX_VALUE);
        assertThat(store.readCanvas().rgb()).containsExactly(0, 0, 0);
    }

    @Test
    void sequentialPixelWritesMutateOneGenerationAndAdvanceRevision() {
        FakeRedisGateway redis = new FakeRedisGateway();
        PlaceCanvasStore store = new PlaceCanvasStore(redis);
        store.initialize(2, 1);

        assertThat(store.placePixel(0, 0, "#112233").revision()).isEqualTo(2);
        assertThat(store.placePixel(1, 0, "#AABBCC").revision()).isEqualTo(3);

        assertThat(store.readCanvas().rgb()).containsExactly(0x11, 0x22, 0x33,
                (byte) 0xAA, (byte) 0xBB, (byte) 0xCC);
        assertThat(redis.bytes.keySet()).contains("place:canvas:rgb:1");
        assertThat(redis.bytes.keySet()).doesNotContain("place:canvas:rgb:2", "place:canvas:rgb:3");
    }

    @Test
    void lateRenderOfOldRevisionCannotPoisonNewRevisionCache() {
        FakeRedisGateway redis = new FakeRedisGateway();
        PlaceCanvasStore store = new PlaceCanvasStore(redis);
        store.initialize(1, 1);
        byte[] revisionOnePng = store.snapshot().png();
        redis.remove("place:canvas:png:1");
        redis.beforeNextPngSet = () -> store.placePixel(0, 0, "#FF0000");

        store.snapshot();
        PlaceCanvasSnapshot current = store.snapshot();

        assertThat(current.descriptor().revision()).isEqualTo(2);
        assertThat(current.png()).isNotEqualTo(revisionOnePng);
        assertThat(redis.bytes).containsKeys("place:canvas:png:1", "place:canvas:png:2");
    }

    @Test
    void resizeActivationChangesGenerationDimensionsAndRevisionTogether() {
        FakeRedisGateway redis = new FakeRedisGateway();
        PlaceCanvasStore store = new PlaceCanvasStore(redis);
        PlaceCanvasDescriptor original = store.initialize(1, 1);
        byte[] resized = new byte[2 * 3 * 3];
        resized[17] = 42;

        assertThat(store.activateResizedGeneration(original.revision(), 2, 3, resized)).isTrue();

        PlaceCanvasStore.CanvasRead current = store.readCanvas();
        assertThat(current.descriptor().generation()).isNotEqualTo(original.generation());
        assertThat(current.descriptor().revision()).isEqualTo(2);
        assertThat(current.descriptor().width()).isEqualTo(2);
        assertThat(current.descriptor().height()).isEqualTo(3);
        assertThat(current.rgb()).isEqualTo(resized);
    }

    @Test
    void failedResizeActivationLeavesOldDescriptorAndGenerationReadable() {
        FakeRedisGateway redis = new FakeRedisGateway();
        PlaceCanvasStore store = new PlaceCanvasStore(redis);
        PlaceCanvasDescriptor original = store.initialize(1, 1);
        store.placePixel(0, 0, "#123456");

        assertThat(store.activateResizedGeneration(original.revision(), 2, 2, new byte[12])).isFalse();

        PlaceCanvasStore.CanvasRead current = store.readCanvas();
        assertThat(current.descriptor()).isEqualTo(new PlaceCanvasDescriptor(original.generation(), 2, 1, 1));
        assertThat(current.rgb()).containsExactly(0x12, 0x34, 0x56);
    }

    @Test
    void existingGenerationCanBeReactivatedForResizeCompensation() {
        FakeRedisGateway redis = new FakeRedisGateway();
        PlaceCanvasStore store = new PlaceCanvasStore(redis);
        PlaceCanvasDescriptor original = store.initialize(1, 1);
        store.placePixel(0, 0, "#123456");
        assertThat(store.activateResizedGeneration(2, 2, 1, new byte[6])).isTrue();

        assertThat(store.activateExistingGeneration(3, original.generation(), 1, 1)).isTrue();

        PlaceCanvasStore.CanvasRead restored = store.readCanvas();
        assertThat(restored.descriptor()).isEqualTo(new PlaceCanvasDescriptor(original.generation(), 4, 1, 1));
        assertThat(restored.rgb()).containsExactly(0x12, 0x34, 0x56);
    }

    @Test
    void exclusiveResizeRejectsConcurrentPlacementAndCompensationRestoresOldDimensions() {
        FakeRedisGateway redis = new FakeRedisGateway();
        PlaceCanvasStore store = new PlaceCanvasStore(redis);
        PlaceCanvasDescriptor original = store.initialize(2, 1);
        store.placePixel(0, 0, "#123456");

        PlaceCanvasStore.ResizeLease lease = store.acquireResizeLease(2, 1);
        assertThatThrownBy(() -> store.placePixel(1, 0, "#FFFFFF"))
                .isInstanceOf(PlaceResizeLockedException.class);

        PlaceCanvasStore.ResizeActivation resized =
                store.activateResizedGeneration(lease, 2, 3, 1, new byte[9]);
        assertThat(resized).isNotNull();
        assertThat(store.activateExistingGeneration(lease, resized.revision(),
                original.generation(), 2, 1)).isTrue();
        store.deleteGeneration(resized.generation());
        store.releaseResizeLease(lease);

        assertThat(store.readCanvas().descriptor().width()).isEqualTo(2);
        assertThat(store.readCanvas().descriptor().height()).isEqualTo(1);
        assertThat(store.readCanvas().rgb()).containsExactly(0x12, 0x34, 0x56, 0, 0, 0);
        assertThat(redis.bytes).doesNotContainKey("place:canvas:rgb:" + resized.generation());
    }

    @Test
    void heartbeatKeepsResizeExclusiveBeyondOriginalLeaseAndStopsOnRelease() throws Exception {
        FakeRedisGateway redis = new FakeRedisGateway();
        // Le bail dure une seconde et le battement frappe toutes les vingt millisecondes : ce
        // qui est teste, c'est le rapport entre les deux, pas leur valeur absolue.
        //
        // L'ancien reglage -- trente millisecondes de bail, cinq de battement -- exigeait que
        // l'ordonnanceur lance la premiere tache dans les trente millisecondes suivant la prise
        // du verrou. Sur un agent d'integration a deux coeurs, un hoquet plus long laissait le
        // bail expirer ; le faux Redis oubliait alors son proprietaire, et **plus aucun**
        // renouvellement ne pouvait aboutir -- `renewResize` refuse un proprietaire inconnu, et
        // rien ne le reinscrit. Attendre par condition ne reparait pas cela : le test patientait
        // cinq secondes devant un evenement devenu impossible. Il faut que le bail survive au
        // hoquet, pas que le test l'attende plus poliment.
        Duration lease = Duration.ofSeconds(1);
        PlaceCanvasStore store = new PlaceCanvasStore(redis, lease, Duration.ofMillis(20));
        store.initialize(1, 1);

        PlaceCanvasStore.ResizeLease held = store.acquireResizeLease(1, 1);

        // On attend d'avoir depasse la duree du bail initial : c'est la, et seulement la, que la
        // propriete a un sens. Sans battement, le verrou serait tombe entre-temps.
        long acquiredAt = System.nanoTime();
        waitUntil(() -> System.nanoTime() - acquiredAt > lease.toNanos() && redis.renewals() > 1,
                "le battement n'a pas renouvele le bail");
        assertThatThrownBy(() -> store.placePixel(0, 0, "#FFFFFF"))
                .isInstanceOf(PlaceResizeLockedException.class);
        int renewalsAtRelease = redis.renewals();
        store.releaseResizeLease(held);

        // Ici l'attente reste necessaire : on verifie qu'il ne se passe **rien**, et une
        // absence ne s'attend pas par condition. Cinq fois l'intervalle du battement laisse a
        // une tache survivante tout le temps de se manifester.
        Thread.sleep(100);

        assertThat(redis.renewals()).isEqualTo(renewalsAtRelease);
        assertThat(store.placePixel(0, 0, "#FFFFFF").revision()).isEqualTo(2);
    }


    /**
     * Attend qu'une condition devienne vraie, plutot que de dormir une duree devinee.
     *
     * Un `sleep` fixe encode la vitesse de la machine ou le test a ete ecrit. Il passe chez
     * son auteur et tombe sur un agent charge -- sans rien apprendre a personne, puisque le
     * code teste n'a pas change.
     */
    private static void waitUntil(java.util.function.BooleanSupplier condition, String message)
            throws InterruptedException {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(5);
        }
        throw new AssertionError(message);
    }

    private static final class FakeRedisGateway implements PlaceCanvasStore.RedisGateway {
        private final Map<String, byte[]> bytes = new HashMap<>();
        private Runnable beforeNextPngSet;
        private String resizeOwner;
        private long resizeExpiresAt;
        private int renewals;

        @Override
        public PlaceCanvasDescriptor initialize(String descriptorKey, String prefix, String generation,
                                                String pngPrefix, int width, int height, int byteCount) {
            if (!bytes.containsKey(descriptorKey)) {
                bytes.put(prefix + generation, new byte[byteCount]);
                bytes.remove(pngPrefix + "1");
                setDescriptor(descriptorKey, new PlaceCanvasDescriptor(generation, 1, width, height));
            }
            return descriptor(descriptorKey);
        }

        @Override
        public PlaceCanvasStore.CanvasRead read(String descriptorKey, String prefix) {
            PlaceCanvasDescriptor descriptor = descriptor(descriptorKey);
            return new PlaceCanvasStore.CanvasRead(bytes.get(prefix + descriptor.generation()), descriptor);
        }

        @Override
        public PlaceCanvasDescriptor placePixel(String descriptorKey, String prefix, int x, int y, byte[] rgb) {
            if (resizeLocked()) throw new PlaceResizeLockedException();
            PlaceCanvasDescriptor current = descriptor(descriptorKey);
            if (current.revision() == Long.MAX_VALUE) {
                throw new IllegalStateException("La révision du canevas a atteint sa valeur maximale.");
            }
            byte[] canvas = bytes.get(prefix + current.generation());
            System.arraycopy(rgb, 0, canvas, (y * current.width() + x) * 3, 3);
            PlaceCanvasDescriptor next = new PlaceCanvasDescriptor(current.generation(), current.revision() + 1,
                    current.width(), current.height());
            setDescriptor(descriptorKey, next);
            return next;
        }

        @Override
        public boolean activate(String descriptorKey, long expectedRevision, String generation, int width, int height) {
            PlaceCanvasDescriptor current = descriptor(descriptorKey);
            if (current.revision() != expectedRevision) return false;
            if (current.revision() == Long.MAX_VALUE) {
                throw new IllegalStateException("La révision du canevas a atteint sa valeur maximale.");
            }
            setDescriptor(descriptorKey, new PlaceCanvasDescriptor(generation, expectedRevision + 1, width, height));
            return true;
        }

        @Override
        public byte[] get(String key) {
            return bytes.get(key);
        }

        @Override
        public void set(String key, byte[] value) {
            if (key.startsWith("place:canvas:png:") && beforeNextPngSet != null) {
                Runnable hook = beforeNextPngSet;
                beforeNextPngSet = null;
                hook.run();
            }
            bytes.put(key, value.clone());
        }

        @Override
        public synchronized boolean acquireResize(String descriptorKey, String lockKey, String owner, long leaseMs) {
            if (resizeOwner != null) return false;
            resizeOwner = owner;
            resizeExpiresAt = System.nanoTime() + leaseMs * 1_000_000;
            return true;
        }

        @Override
        public synchronized boolean renewResize(String lockKey, String owner, long leaseMs) {
            if (resizeOwner != null && System.nanoTime() >= resizeExpiresAt) resizeOwner = null;
            if (!owner.equals(resizeOwner)) return false;
            renewals++;
            resizeExpiresAt = System.nanoTime() + leaseMs * 1_000_000;
            return true;
        }

        @Override
        public synchronized boolean releaseResize(String lockKey, String owner) {
            if (!owner.equals(resizeOwner)) return false;
            resizeOwner = null;
            return true;
        }

        @Override
        public boolean activateLocked(String descriptorKey, String lockKey, long expectedRevision,
                                      String generation, int width, int height, String owner) {
            return owns(owner)
                    && activate(descriptorKey, expectedRevision, generation, width, height);
        }

        /** Le battement ecrit ces champs depuis son propre fil ; le test ne les lit pas a nu. */
        private synchronized boolean resizeLocked() {
            if (resizeOwner != null && System.nanoTime() >= resizeExpiresAt) resizeOwner = null;
            return resizeOwner != null;
        }

        private synchronized boolean owns(String owner) {
            return owner.equals(resizeOwner);
        }

        private synchronized int renewals() {
            return renewals;
        }

        @Override
        public void deleteGeneration(String descriptorKey, String prefix, String generation) {
            if (!descriptor(descriptorKey).generation().equals(generation)) bytes.remove(prefix + generation);
        }

        private void remove(String key) { bytes.remove(key); }

        private PlaceCanvasDescriptor descriptor(String key) {
            String[] fields = new String(bytes.get(key)).split(",");
            return new PlaceCanvasDescriptor(fields[0], Long.parseLong(fields[1]),
                    Integer.parseInt(fields[2]), Integer.parseInt(fields[3]));
        }

        private void setDescriptor(String key, PlaceCanvasDescriptor value) {
            set(key, (value.generation() + "," + value.revision() + "," + value.width() + "," + value.height()).getBytes());
        }
    }

    private static FakeRedisGateway initializedAtRevision(long revision) {
        FakeRedisGateway redis = new FakeRedisGateway();
        redis.bytes.put("place:canvas:rgb:1", new byte[3]);
        redis.setDescriptor("place:canvas:active", new PlaceCanvasDescriptor("1", revision, 1, 1));
        return redis;
    }
}
