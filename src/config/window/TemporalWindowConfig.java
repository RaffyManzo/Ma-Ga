package config.window;

/**
 * Configurazione del gestore temporale del MA-GA.
 *
 * <p>La finestra iniziale resta configurata tramite {@code fixedIntervalSeconds}
 * per compatibilità con il codice precedente. Dopo la prima esecuzione, la
 * durata della finestra può essere aggiornata dal controller adattivo.</p>
 */
public final class TemporalWindowConfig {

    private static final double DEFAULT_STRATEGY_APPLICATION_SECONDS = 0.05;
    private static final double DEFAULT_GA_RUNTIME_ESTIMATE_SECONDS = 0.10;

    private final double fixedIntervalSeconds;
    private final double dataCollectionDelaySeconds;
    private final double thetaLow;
    private final double thetaHigh;
    private final double rhoKeep;
    private final double lambdaVehicles;
    private final double lambdaTasks;
    private final double lambdaResources;
    private final double lambdaLinks;
    private final double alphaT;
    private final double etaUp;
    private final double etaDown;
    private final double epsilonT;
    private final double strategyApplicationSeconds;
    private final double defaultGaRuntimeEstimateSeconds;

    /**
     * Costruttore compatibile con la versione precedente.
     */
    public TemporalWindowConfig(
            double fixedIntervalSeconds,
            double thetaLow,
            double thetaHigh,
            double rhoKeep,
            double lambdaVehicles,
            double lambdaTasks,
            double lambdaResources,
            double lambdaLinks,
            double alphaT,
            double etaUp,
            double etaDown,
            double epsilonT
    ) {
        this(
                fixedIntervalSeconds,
                0.0,
                thetaLow,
                thetaHigh,
                rhoKeep,
                lambdaVehicles,
                lambdaTasks,
                lambdaResources,
                lambdaLinks,
                alphaT,
                etaUp,
                etaDown,
                epsilonT,
                DEFAULT_STRATEGY_APPLICATION_SECONDS,
                DEFAULT_GA_RUNTIME_ESTIMATE_SECONDS
        );
    }

    /**
     * Costruisce la configurazione temporale completa.
     */
    public TemporalWindowConfig(
            double fixedIntervalSeconds,
            double dataCollectionDelaySeconds,
            double thetaLow,
            double thetaHigh,
            double rhoKeep,
            double lambdaVehicles,
            double lambdaTasks,
            double lambdaResources,
            double lambdaLinks,
            double alphaT,
            double etaUp,
            double etaDown,
            double epsilonT
    ) {
        this(
                fixedIntervalSeconds,
                dataCollectionDelaySeconds,
                thetaLow,
                thetaHigh,
                rhoKeep,
                lambdaVehicles,
                lambdaTasks,
                lambdaResources,
                lambdaLinks,
                alphaT,
                etaUp,
                etaDown,
                epsilonT,
                DEFAULT_STRATEGY_APPLICATION_SECONDS,
                DEFAULT_GA_RUNTIME_ESTIMATE_SECONDS
        );
    }

    /**
     * Costruisce la configurazione temporale completa, includendo i tempi
     * operativi usati per il limite inferiore della finestra adattiva.
     */
    public TemporalWindowConfig(
            double fixedIntervalSeconds,
            double dataCollectionDelaySeconds,
            double thetaLow,
            double thetaHigh,
            double rhoKeep,
            double lambdaVehicles,
            double lambdaTasks,
            double lambdaResources,
            double lambdaLinks,
            double alphaT,
            double etaUp,
            double etaDown,
            double epsilonT,
            double strategyApplicationSeconds,
            double defaultGaRuntimeEstimateSeconds
    ) {
        validatePositive("fixedIntervalSeconds", fixedIntervalSeconds);
        validateFiniteAndNonNegative("dataCollectionDelaySeconds", dataCollectionDelaySeconds);
        validateRate("thetaLow", thetaLow);
        validateRate("thetaHigh", thetaHigh);
        if (thetaLow >= thetaHigh) {
            throw new IllegalArgumentException("thetaLow must be smaller than thetaHigh.");
        }
        validateRate("rhoKeep", rhoKeep);
        validateFiniteAndNonNegative("lambdaVehicles", lambdaVehicles);
        validateFiniteAndNonNegative("lambdaTasks", lambdaTasks);
        validateFiniteAndNonNegative("lambdaResources", lambdaResources);
        validateFiniteAndNonNegative("lambdaLinks", lambdaLinks);
        double lambdaSum = lambdaVehicles + lambdaTasks + lambdaResources + lambdaLinks;
        if (lambdaSum <= 0.0) {
            throw new IllegalArgumentException("At least one dynamicity lambda must be > 0.");
        }
        validateRate("alphaT", alphaT);
        validatePositive("etaUp", etaUp);
        validatePositive("etaDown", etaDown);
        validateFiniteAndNonNegative("epsilonT", epsilonT);
        validateFiniteAndNonNegative("strategyApplicationSeconds", strategyApplicationSeconds);
        validatePositive("defaultGaRuntimeEstimateSeconds", defaultGaRuntimeEstimateSeconds);

        this.fixedIntervalSeconds = fixedIntervalSeconds;
        this.dataCollectionDelaySeconds = dataCollectionDelaySeconds;
        this.thetaLow = thetaLow;
        this.thetaHigh = thetaHigh;
        this.rhoKeep = rhoKeep;
        this.lambdaVehicles = lambdaVehicles;
        this.lambdaTasks = lambdaTasks;
        this.lambdaResources = lambdaResources;
        this.lambdaLinks = lambdaLinks;
        this.alphaT = alphaT;
        this.etaUp = etaUp;
        this.etaDown = etaDown;
        this.epsilonT = epsilonT;
        this.strategyApplicationSeconds = strategyApplicationSeconds;
        this.defaultGaRuntimeEstimateSeconds = defaultGaRuntimeEstimateSeconds;
    }

    /**
     * Configurazione iniziale per i test con snapshot JSON.
     */
    public static TemporalWindowConfig defaultConfig() {
        return new TemporalWindowConfig(
                5.0,
                0.0,
                0.25,
                0.65,
                0.40,
                0.25,
                0.25,
                0.25,
                0.25,
                0.60,
                1.0,
                1.0,
                1.0E-6,
                DEFAULT_STRATEGY_APPLICATION_SECONDS,
                DEFAULT_GA_RUNTIME_ESTIMATE_SECONDS
        );
    }

    public static TemporalWindowConfig fixedInterval(double fixedIntervalSeconds) {
        return fixedIntervalWithCollectionDelay(fixedIntervalSeconds, 0.0);
    }

    public static TemporalWindowConfig fixedIntervalWithCollectionDelay(
            double fixedIntervalSeconds,
            double dataCollectionDelaySeconds
    ) {
        return new TemporalWindowConfig(
                fixedIntervalSeconds,
                dataCollectionDelaySeconds,
                0.25,
                0.65,
                0.40,
                0.25,
                0.25,
                0.25,
                0.25,
                0.60,
                1.0,
                1.0,
                1.0E-6,
                DEFAULT_STRATEGY_APPLICATION_SECONDS,
                DEFAULT_GA_RUNTIME_ESTIMATE_SECONDS
        );
    }

    public double getFixedIntervalSeconds() {
        return fixedIntervalSeconds;
    }

    /**
     * Nome più chiaro per la finestra iniziale.
     */
    public double getInitialWindowSeconds() {
        return fixedIntervalSeconds;
    }

    public double getDataCollectionDelaySeconds() {
        return dataCollectionDelaySeconds;
    }

    public double getThetaLow() {
        return thetaLow;
    }

    public double getThetaHigh() {
        return thetaHigh;
    }

    public double getRhoKeep() {
        return rhoKeep;
    }

    public double getLambdaVehicles() {
        return lambdaVehicles;
    }

    public double getLambdaTasks() {
        return lambdaTasks;
    }

    public double getLambdaResources() {
        return lambdaResources;
    }

    public double getLambdaLinks() {
        return lambdaLinks;
    }

    /**
     * Coefficiente usato per DeltaT_max(k) = alphaT * T_coverage_ref(k).
     */
    public double getAlphaT() {
        return alphaT;
    }

    /**
     * Passo additivo, in secondi, usato quando la finestra può crescere.
     */
    public double getEtaUp() {
        return etaUp;
    }

    /**
     * Passo sottrattivo, in secondi, usato quando la finestra deve ridursi.
     */
    public double getEtaDown() {
        return etaDown;
    }

    public double getEpsilonT() {
        return epsilonT;
    }

    public double getStrategyApplicationSeconds() {
        return strategyApplicationSeconds;
    }

    public double getDefaultGaRuntimeEstimateSeconds() {
        return defaultGaRuntimeEstimateSeconds;
    }

    public double getLambdaSum() {
        return lambdaVehicles + lambdaTasks + lambdaResources + lambdaLinks;
    }

    public double getNormalizedLambdaVehicles() {
        return lambdaVehicles / getLambdaSum();
    }

    public double getNormalizedLambdaTasks() {
        return lambdaTasks / getLambdaSum();
    }

    public double getNormalizedLambdaResources() {
        return lambdaResources / getLambdaSum();
    }

    public double getNormalizedLambdaLinks() {
        return lambdaLinks / getLambdaSum();
    }

    private static void validatePositive(String fieldName, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite.");
        }
        if (value <= 0.0) {
            throw new IllegalArgumentException(fieldName + " must be > 0.");
        }
    }

    private static void validateFiniteAndNonNegative(String fieldName, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite.");
        }
        if (value < 0.0) {
            throw new IllegalArgumentException(fieldName + " must be >= 0.");
        }
    }

    private static void validateRate(String fieldName, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite.");
        }
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(fieldName + " must be in [0, 1].");
        }
    }

    @Override
    public String toString() {
        return "TemporalWindowConfig{" +
                "fixedIntervalSeconds=" + fixedIntervalSeconds +
                ", dataCollectionDelaySeconds=" + dataCollectionDelaySeconds +
                ", thetaLow=" + thetaLow +
                ", thetaHigh=" + thetaHigh +
                ", rhoKeep=" + rhoKeep +
                ", lambdaVehicles=" + lambdaVehicles +
                ", lambdaTasks=" + lambdaTasks +
                ", lambdaResources=" + lambdaResources +
                ", lambdaLinks=" + lambdaLinks +
                ", alphaT=" + alphaT +
                ", etaUp=" + etaUp +
                ", etaDown=" + etaDown +
                ", epsilonT=" + epsilonT +
                ", strategyApplicationSeconds=" + strategyApplicationSeconds +
                ", defaultGaRuntimeEstimateSeconds=" + defaultGaRuntimeEstimateSeconds +
                '}';
    }
}
