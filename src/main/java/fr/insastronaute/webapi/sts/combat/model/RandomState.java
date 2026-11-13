package fr.insastronaute.webapi.sts.combat.model;

/**
 * Deterministic SplitMix64 state whose stored value and sequence algorithm form a stable
 * snapshot/replay contract.
 */
public record RandomState(long value) {

    /**
     * Advances the stream without drawing a bounded value.
     *
     * <p>Used where the engine needs a fresh unique value rather than a die roll, such as naming
     * a card an effect just created. SplitMix64 never repeats a state within a combat, so those
     * names are unique, and they are reproduced exactly on replay.
     */
    public RandomState advance() {
        return new RandomState(value + 0x9E3779B97F4A7C15L);
    }

    public RandomStep nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        long nextValue = value + 0x9E3779B97F4A7C15L;
        long mixed = nextValue;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        mixed ^= mixed >>> 31;
        return new RandomStep((int) Long.remainderUnsigned(mixed, bound), new RandomState(nextValue));
    }

    public record RandomStep(int value, RandomState next) {
    }
}
