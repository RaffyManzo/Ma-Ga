package ga.operators;

/**
 * Risultato di una scelta di allocazione delle risorse per un gene.
 *
 * <p>La classe è immutabile e contiene CPU, banda e modalità diagnostica
 * dell'allocazione.</p>
 */
public final class ResourceAllocationDecision {

    public enum Mode {
        LOCAL,
        RANDOM,
        DEADLINE_AWARE,
        BORDERLINE,
        MODERATE,
        AGGRESSIVE,
        SMALL_STEP
    }

    private final double allocatedCpu;
    private final double allocatedBandwidth;
    private final Mode mode;

    public ResourceAllocationDecision(
            double allocatedCpu,
            double allocatedBandwidth,
            Mode mode
    ) {
        this.allocatedCpu = validateFinite("allocatedCpu", allocatedCpu);
        this.allocatedBandwidth = validateFinite("allocatedBandwidth", allocatedBandwidth);
        this.mode = mode == null ? Mode.RANDOM : mode;
    }

    public double getAllocatedCpu() {
        return allocatedCpu;
    }

    public double getAllocatedBandwidth() {
        return allocatedBandwidth;
    }

    public Mode getMode() {
        return mode;
    }

    private static double validateFinite(String fieldName, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite.");
        }

        return Math.max(0.0, value);
    }

    @Override
    public String toString() {
        return "ResourceAllocationDecision{" +
                "allocatedCpu=" + allocatedCpu +
                ", allocatedBandwidth=" + allocatedBandwidth +
                ", mode=" + mode +
                '}';
    }
}