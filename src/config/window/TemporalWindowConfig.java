package config.window;

/**
 * Configurazione del gestore temporale del MA-GA.
 *
 * Contiene i parametri usati dal package window per:
 * - programmare la riesecuzione periodica;
 * - modellare il ritardo di raccolta dello stato;
 * - classificare la dinamicità tra snapshot;
 * - decidere il riuso della popolazione precedente.
 */
public final class TemporalWindowConfig {

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

    /**
     * Costruttore compatibile con la versione precedente.
     *
     * Imposta dataCollectionDelaySeconds a 0.0, adatto alla simulazione statica
     * basata su snapshot JSON già disponibili.
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
                epsilonT
        );
    }

    /**
     * Costruisce la configurazione temporale completa.
     *
     * @param fixedIntervalSeconds intervallo programmato tra due riesecuzioni
     * @param dataCollectionDelaySeconds ritardo tra trigger e snapshot osservato
     * @param thetaLow soglia inferiore della dinamicità
     * @param thetaHigh soglia superiore della dinamicità
     * @param rhoKeep quota mantenuta in partial restart
     * @param lambdaVehicles peso variazione veicoli
     * @param lambdaTasks peso variazione task
     * @param lambdaResources peso variazione risorse
     * @param lambdaLinks peso variazione link
     * @param alphaT coefficiente per futura finestra adattiva
     * @param etaUp fattore di incremento futuro della finestra
     * @param etaDown fattore di riduzione futuro della finestra
     * @param epsilonT soglia numerica per aggiornamenti trascurabili
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

        if (etaUp < 1.0) {
            throw new IllegalArgumentException("etaUp should be >= 1.0.");
        }

        if (etaDown > 1.0) {
            throw new IllegalArgumentException("etaDown should be <= 1.0.");
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
    }

    /**
     * Configurazione iniziale per test statici.
     *
     * Il ritardo di raccolta dati è 0.0 perché gli snapshot JSON sono già pronti.
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
                0.50,
                1.20,
                0.80,
                1.0E-6
        );
    }

    /**
     * Crea una configurazione con finestra fissa e raccolta dati istantanea.
     *
     * @param fixedIntervalSeconds intervallo programmato
     * @return configurazione temporale
     */
    public static TemporalWindowConfig fixedInterval(double fixedIntervalSeconds) {
        return fixedIntervalWithCollectionDelay(fixedIntervalSeconds, 0.0);
    }

    /**
     * Crea una configurazione con finestra fissa e ritardo di raccolta esplicito.
     *
     * @param fixedIntervalSeconds intervallo programmato
     * @param dataCollectionDelaySeconds ritardo di raccolta dati
     * @return configurazione temporale
     */
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
                0.50,
                1.20,
                0.80,
                1.0E-6
        );
    }

    public double getFixedIntervalSeconds() {
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
        return "TemporalWindowConfig{"
                + "fixedIntervalSeconds=" + fixedIntervalSeconds
                + ", dataCollectionDelaySeconds=" + dataCollectionDelaySeconds
                + ", thetaLow=" + thetaLow
                + ", thetaHigh=" + thetaHigh
                + ", rhoKeep=" + rhoKeep
                + ", lambdaVehicles=" + lambdaVehicles
                + ", lambdaTasks=" + lambdaTasks
                + ", lambdaResources=" + lambdaResources
                + ", lambdaLinks=" + lambdaLinks
                + ", alphaT=" + alphaT
                + ", etaUp=" + etaUp
                + ", etaDown=" + etaDown
                + ", epsilonT=" + epsilonT
                + '}';
    }
}