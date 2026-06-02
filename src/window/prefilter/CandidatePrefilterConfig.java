package window.prefilter;

/**
 * Configurazione del prefiltraggio strutturale dei candidati.
 *
 * <p>I parametri storici restano esposti per compatibilità, ma il prefilter
 * ufficiale non elimina alternative raggiungibili soltanto perché deboli.
 * Il CLOUD usa il gateway radio attivo dello snapshot.</p>
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

    public static CandidatePrefilterConfig defaultConfig() {
        return new CandidatePrefilterConfig(true, 0.0, 0.0, 0.0, 1.0, 1.0, 300.0, 300.0, false);
    }

    public static CandidatePrefilterConfig disabled() {
        return new CandidatePrefilterConfig(false, 0.0, 0.0, 0.0, 1.0, 1.0, 300.0, 300.0, false);
    }

    public boolean isEnabled() { return enabled; }
    @Deprecated public double getMinRemoteCpu() { return minRemoteCpu; }
    @Deprecated public double getMinRemoteBandwidth() { return minRemoteBandwidth; }
    @Deprecated public double getMinCoverageSeconds() { return minCoverageSeconds; }
    @Deprecated public double getCoverageSafetyFactor() { return coverageSafetyFactor; }
    @Deprecated public double getDeadlineSlackFactor() { return deadlineSlackFactor; }
    public double getV2vCoverageRadiusMeters() { return v2vCoverageRadiusMeters; }
    /** Usato soltanto come clamp convenzionale per V2V con velocità relativa nulla. */
    public double getCloudCoverageSeconds() { return cloudCoverageSeconds; }
    /** @deprecated il CLOUD non viene più mantenuto automaticamente. */
    @Deprecated public boolean isKeepAllCloudCandidates() { return keepAllCloudCandidates; }

    private static double validateNonNegative(String field, double value) {
        if (!Double.isFinite(value) || value < 0.0) { throw new IllegalArgumentException(field + " must be finite and >= 0."); }
        return value;
    }
    private static double validatePositive(String field, double value) {
        if (!Double.isFinite(value) || value <= 0.0) { throw new IllegalArgumentException(field + " must be finite and > 0."); }
        return value;
    }
}
