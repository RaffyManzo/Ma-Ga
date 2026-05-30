package config.window;

/**
 * Configurazione del gestore temporale del MA-GA.
 *
 * <p>Contiene soglie di dinamicità, pesi delle componenti, parametri di
 * crescita/riduzione della finestra e limiti operativi usati dal controller
 * adattivo.</p>
 *
 * <p>{@code fixedIntervalSeconds} identifica la durata iniziale della finestra.
 * Dopo la prima esecuzione, la durata può essere aggiornata dal controller
 * adattivo.</p>
 */
public final class TemporalWindowConfig {

    private static final double DEFAULT_STRATEGY_APPLICATION_SECONDS = 0.05;
    private static final double DEFAULT_GA_RUNTIME_ESTIMATE_SECONDS = 0.10;
    private static final double DEFAULT_CONFIGURED_MAX_WINDOW_SECONDS = 8.0;

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
    private final double configuredMaxWindowSeconds;
    private final TemporalMinimumBoundMode minimumBoundMode;
    private final TemporalMaximumBoundMode maximumBoundMode;

    /**
     * Overload storico senza ritardo di raccolta dati.
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
                DEFAULT_GA_RUNTIME_ESTIMATE_SECONDS,
                DEFAULT_CONFIGURED_MAX_WINDOW_SECONDS,
                TemporalMinimumBoundMode.CONFIGURED_GA_ESTIMATE,
                TemporalMaximumBoundMode.COVERAGE_ADAPTIVE
        );
    }

    /**
     * Overload storico con ritardo di raccolta dati esplicito.
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
                DEFAULT_GA_RUNTIME_ESTIMATE_SECONDS,
                DEFAULT_CONFIGURED_MAX_WINDOW_SECONDS,
                TemporalMinimumBoundMode.CONFIGURED_GA_ESTIMATE,
                TemporalMaximumBoundMode.COVERAGE_ADAPTIVE
        );
    }

    /**
     * Overload con stime operative esplicite per finestra adattiva.
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
                strategyApplicationSeconds,
                defaultGaRuntimeEstimateSeconds,
                DEFAULT_CONFIGURED_MAX_WINDOW_SECONDS,
                TemporalMinimumBoundMode.CONFIGURED_GA_ESTIMATE,
                TemporalMaximumBoundMode.COVERAGE_ADAPTIVE
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
            double epsilonT,
            double strategyApplicationSeconds,
            double defaultGaRuntimeEstimateSeconds,
            double configuredMaxWindowSeconds,
            TemporalMinimumBoundMode minimumBoundMode,
            TemporalMaximumBoundMode maximumBoundMode
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
        validatePositive("configuredMaxWindowSeconds", configuredMaxWindowSeconds);
        if (minimumBoundMode == null) {
            throw new IllegalArgumentException("minimumBoundMode must not be null.");
        }
        if (maximumBoundMode == null) {
            throw new IllegalArgumentException("maximumBoundMode must not be null.");
        }

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
        this.configuredMaxWindowSeconds = configuredMaxWindowSeconds;
        this.minimumBoundMode = minimumBoundMode;
        this.maximumBoundMode = maximumBoundMode;
    }

    /**
     * Configurazione iniziale per i test con snapshot JSON.
     *
     * <p>Il minimo usa una stima configurata del tempo GA. Questo evita che
     * il wall-clock della JVM domini DeltaT_min durante i test locali.</p>
     *
     * <p>Il massimo resta aderente alla formalizzazione:</p>
     *
     * <pre>
     * DeltaT_max(k) = alphaT * T_coverage_ref(k)
     * </pre>
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
                DEFAULT_GA_RUNTIME_ESTIMATE_SECONDS,
                DEFAULT_CONFIGURED_MAX_WINDOW_SECONDS,
                TemporalMinimumBoundMode.CONFIGURED_GA_ESTIMATE,
                TemporalMaximumBoundMode.COVERAGE_ADAPTIVE
        );
    }

    /**
     * Configurazione utile se si vuole studiare il comportamento operativo
     * usando il tempo reale osservato del GA.
     */
    public static TemporalWindowConfig observedRuntimeBoundsConfig() {
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
                DEFAULT_GA_RUNTIME_ESTIMATE_SECONDS,
                DEFAULT_CONFIGURED_MAX_WINDOW_SECONDS,
                TemporalMinimumBoundMode.OBSERVED_GA_RUNTIME,
                TemporalMaximumBoundMode.COVERAGE_ADAPTIVE
        );
    }

    /**
     * Configurazione controllata. Utile solo per test sintetici.
     */
    public static TemporalWindowConfig configuredBoundsForReplay(
            double initialWindowSeconds,
            double configuredGaRuntimeEstimateSeconds,
            double configuredMaxWindowSeconds
    ) {
        return new TemporalWindowConfig(
                initialWindowSeconds,
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
                configuredGaRuntimeEstimateSeconds,
                configuredMaxWindowSeconds,
                TemporalMinimumBoundMode.CONFIGURED_GA_ESTIMATE,
                TemporalMaximumBoundMode.CONFIGURED_MAX
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
                DEFAULT_GA_RUNTIME_ESTIMATE_SECONDS,
                DEFAULT_CONFIGURED_MAX_WINDOW_SECONDS,
                TemporalMinimumBoundMode.CONFIGURED_GA_ESTIMATE,
                TemporalMaximumBoundMode.COVERAGE_ADAPTIVE
        );
    }

    public double getFixedIntervalSeconds() {
        return fixedIntervalSeconds;
    }

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

    public double getAlphaT() {
        return alphaT;
    }

    public double getEtaUp() {
        return etaUp;
    }

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

    public double getConfiguredMaxWindowSeconds() {
        return configuredMaxWindowSeconds;
    }

    public TemporalMinimumBoundMode getMinimumBoundMode() {
        return minimumBoundMode;
    }

    public TemporalMaximumBoundMode getMaximumBoundMode() {
        return maximumBoundMode;
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
                ", configuredMaxWindowSeconds=" + configuredMaxWindowSeconds +
                ", minimumBoundMode=" + minimumBoundMode +
                ", maximumBoundMode=" + maximumBoundMode +
                '}';
    }
}
