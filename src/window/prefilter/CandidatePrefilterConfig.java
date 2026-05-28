package window.prefilter;

/**
 * Configurazione del prefiltraggio dei candidati.
 *
 * Il prefilter riduce lo spazio di ricerca prima del GA.
 * L'obiettivo non è trovare la soluzione, ma rimuovere candidati
 * chiaramente inutilizzabili o troppo deboli.
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
        this.minRemoteBandwidth = validateNonNegative("minRemoteBandwidth", minRemoteBandwidth);
        this.minCoverageSeconds = validateNonNegative("minCoverageSeconds", minCoverageSeconds);
        this.coverageSafetyFactor = validatePositive("coverageSafetyFactor", coverageSafetyFactor);
        this.deadlineSlackFactor = validatePositive("deadlineSlackFactor", deadlineSlackFactor);
        this.v2vCoverageRadiusMeters = validatePositive("v2vCoverageRadiusMeters", v2vCoverageRadiusMeters);
        this.cloudCoverageSeconds = validatePositive("cloudCoverageSeconds", cloudCoverageSeconds);
        this.keepAllCloudCandidates = keepAllCloudCandidates;
    }

    /**
     * Configurazione consigliata per test stress.
     *
     * È volutamente prudente: rimuove candidati fuori copertura o chiaramente
     * incompatibili, ma mantiene margine sulla deadline.
     */
    public static CandidatePrefilterConfig defaultConfig() {
        return new CandidatePrefilterConfig(
                true,
                1.0,
                1.0,
                0.25,
                1.05,
                2.50,
                300.0,
                300.0,
                false
        );
    }

    /**
     * Configurazione disabilitata.
     */
    public static CandidatePrefilterConfig disabled() {
        return new CandidatePrefilterConfig(
                false,
                0.0,
                0.0,
                0.0,
                1.0,
                10.0,
                300.0,
                300.0,
                true
        );
    }

    public boolean isEnabled() {
        return enabled;
    }

    public double getMinRemoteCpu() {
        return minRemoteCpu;
    }

    public double getMinRemoteBandwidth() {
        return minRemoteBandwidth;
    }

    public double getMinCoverageSeconds() {
        return minCoverageSeconds;
    }

    public double getCoverageSafetyFactor() {
        return coverageSafetyFactor;
    }

    public double getDeadlineSlackFactor() {
        return deadlineSlackFactor;
    }

    public double getV2vCoverageRadiusMeters() {
        return v2vCoverageRadiusMeters;
    }

    public double getCloudCoverageSeconds() {
        return cloudCoverageSeconds;
    }

    public boolean isKeepAllCloudCandidates() {
        return keepAllCloudCandidates;
    }

    private static double validateNonNegative(String fieldName, double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(fieldName + " must be finite and >= 0.");
        }

        return value;
    }

    private static double validatePositive(String fieldName, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(fieldName + " must be finite and > 0.");
        }

        return value;
    }
}
