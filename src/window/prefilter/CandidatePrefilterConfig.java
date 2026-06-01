package window.prefilter;

/**
 * Configurazione del prefiltraggio dei candidati.
 *
 * <p>Il prefilter aderente alla formalizzazione applica soltanto controlli
 * strutturali. Alcuni parametri storici restano esposti per compatibilità con
 * chiamanti precedenti, ma non vengono più usati per eliminare candidati
 * raggiungibili soltanto perché deboli o poco competitivi.</p>
 */
public final class CandidatePrefilterConfig {

    private final boolean enabled;
    private final double minRemoteCpu;
    private final double minRemoteBandwidth;
    private final double minCoverageSeconds;
    private final double coverageSafetyFactor;
    private final double deadlineSlackFactor;
    private final double v2vCoverageRadiusMeters;
    private final double cloudCoverageSeconds;
    private final boolean keepAllCloudCandidates;

    public CandidatePrefilterConfig(
            boolean enabled,
            double minRemoteCpu,
            double minRemoteBandwidth,
            double minCoverageSeconds,
            double coverageSafetyFactor,
            double deadlineSlackFactor,
            double v2vCoverageRadiusMeters,
            double cloudCoverageSeconds,
            boolean keepAllCloudCandidates
    ) {
        this.enabled = enabled;
        this.minRemoteCpu = validateNonNegative("minRemoteCpu", minRemoteCpu);
        this.minRemoteBandwidth = validateNonNegative(
                "minRemoteBandwidth",
                minRemoteBandwidth
        );
        this.minCoverageSeconds = validateNonNegative(
                "minCoverageSeconds",
                minCoverageSeconds
        );
        this.coverageSafetyFactor = validatePositive(
                "coverageSafetyFactor",
                coverageSafetyFactor
        );
        this.deadlineSlackFactor = validatePositive(
                "deadlineSlackFactor",
                deadlineSlackFactor
        );
        this.v2vCoverageRadiusMeters = validatePositive(
                "v2vCoverageRadiusMeters",
                v2vCoverageRadiusMeters
        );
        this.cloudCoverageSeconds = validatePositive(
                "cloudCoverageSeconds",
                cloudCoverageSeconds
        );
        this.keepAllCloudCandidates = keepAllCloudCandidates;
    }

    /**
     * Configurazione predefinita aderente alla formalizzazione.
     *
     * <p>I candidati remoti strutturalmente validi restano valutabili dal GA.
     * CPU, banda e copertura vengono filtrate soltanto quando sono nulle o non
     * valide. Le deadline non vengono usate come criterio di pruning.</p>
     */
    public static CandidatePrefilterConfig defaultConfig() {
        return new CandidatePrefilterConfig(
                true,
                0.0,
                0.0,
                0.0,
                1.0,
                1.0,
                300.0,
                300.0,
                true
        );
    }

    /** Configurazione completamente disabilitata. */
    public static CandidatePrefilterConfig disabled() {
        return new CandidatePrefilterConfig(
                false,
                0.0,
                0.0,
                0.0,
                1.0,
                1.0,
                300.0,
                300.0,
                true
        );
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** @deprecated mantenuto soltanto per compatibilità. */
    @Deprecated
    public double getMinRemoteCpu() {
        return minRemoteCpu;
    }

    /** @deprecated mantenuto soltanto per compatibilità. */
    @Deprecated
    public double getMinRemoteBandwidth() {
        return minRemoteBandwidth;
    }

    /** @deprecated mantenuto soltanto per compatibilità. */
    @Deprecated
    public double getMinCoverageSeconds() {
        return minCoverageSeconds;
    }

    /** @deprecated mantenuto soltanto per compatibilità. */
    @Deprecated
    public double getCoverageSafetyFactor() {
        return coverageSafetyFactor;
    }

    /** @deprecated mantenuto soltanto per compatibilità. */
    @Deprecated
    public double getDeadlineSlackFactor() {
        return deadlineSlackFactor;
    }

    public double getV2vCoverageRadiusMeters() {
        return v2vCoverageRadiusMeters;
    }

    public double getCloudCoverageSeconds() {
        return cloudCoverageSeconds;
    }

    /** @deprecated il cloud è sempre mantenuto finché manca un gateway esplicito. */
    @Deprecated
    public boolean isKeepAllCloudCandidates() {
        return keepAllCloudCandidates;
    }

    private static double validateNonNegative(String fieldName, double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(
                    fieldName + " must be finite and >= 0."
            );
        }
        return value;
    }

    private static double validatePositive(String fieldName, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(
                    fieldName + " must be finite and > 0."
            );
        }
        return value;
    }
}
