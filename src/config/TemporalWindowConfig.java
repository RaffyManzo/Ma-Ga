package config;

/**
 * Configurazione del gestore temporale del MA-GA.
 *
 * Contiene i parametri necessari al package window per decidere:
 *
 * - ogni quanto rieseguire il MA-GA;
 * - come interpretare la dinamicità tra due snapshot consecutivi;
 * - quanta popolazione genetica precedente riutilizzare;
 * - come predisporre, in futuro, l'adattamento della finestra temporale.
 *
 * Nella prima versione implementativa la finestra sarà statica.
 * Quindi il parametro realmente centrale sarà fixedIntervalSeconds.
 *
 * Gli altri parametri sono comunque presenti perché derivano dalla
 * formalizzazione e permettono di passare gradualmente da:
 *
 * - gestione temporale statica;
 * - a gestione temporale adattiva;
 * - a gestione reattiva con eventi critici.
 */
public final class TemporalWindowConfig {

    /**
     * Intervallo fisso di riesecuzione del MA-GA.
     *
     * Nella prima versione:
     *
     * t_{k+1} = t_k + fixedIntervalSeconds
     *
     * salvo presenza di evento critico anticipato.
     */
    private final double fixedIntervalSeconds;

    /**
     * Soglia inferiore della dinamicità.
     *
     * Se D_k < thetaLow, lo scenario è considerato stabile
     * e il package window può scegliere WARM_START.
     */
    private final double thetaLow;

    /**
     * Soglia superiore della dinamicità.
     *
     * Se D_k > thetaHigh, lo scenario è considerato molto dinamico
     * e il package window può scegliere COLD_START.
     */
    private final double thetaHigh;

    /**
     * Quota della popolazione precedente da conservare in caso di partial restart.
     *
     * Esempio:
     *
     * rhoKeep = 0.40
     *
     * significa conservare il 40% dei migliori cromosomi della popolazione finale
     * precedente e rigenerare il restante 60%.
     */
    private final double rhoKeep;

    /**
     * Peso della variazione dei veicoli nell'indice di dinamicità.
     */
    private final double lambdaVehicles;

    /**
     * Peso della variazione dei task attivi nell'indice di dinamicità.
     */
    private final double lambdaTasks;

    /**
     * Peso della variazione delle risorse computazionali nell'indice di dinamicità.
     */
    private final double lambdaResources;

    /**
     * Peso della variazione dei link/candidati nell'indice di dinamicità.
     */
    private final double lambdaLinks;

    /**
     * Peso di smoothing per la futura finestra adattiva.
     *
     * Non è necessario nella prima versione statica, ma viene mantenuto per
     * evitare di dover cambiare la struttura della configurazione quando verrà
     * introdotto l'adattamento di Delta t.
     */
    private final double alphaT;

    /**
     * Fattore di incremento della finestra temporale adattiva.
     *
     * In futuro potrà essere usato quando lo scenario è stabile.
     */
    private final double etaUp;

    /**
     * Fattore di riduzione della finestra temporale adattiva.
     *
     * In futuro potrà essere usato quando lo scenario è molto dinamico.
     */
    private final double etaDown;

    /**
     * Soglia numerica per evitare aggiornamenti trascurabili della finestra.
     */
    private final double epsilonT;

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
        validatePositive("fixedIntervalSeconds", fixedIntervalSeconds);

        validateRate("thetaLow", thetaLow);
        validateRate("thetaHigh", thetaHigh);

        if (thetaLow >= thetaHigh) {
            throw new IllegalArgumentException(
                    "thetaLow must be smaller than thetaHigh."
            );
        }

        validateRate("rhoKeep", rhoKeep);

        validateFiniteAndNonNegative("lambdaVehicles", lambdaVehicles);
        validateFiniteAndNonNegative("lambdaTasks", lambdaTasks);
        validateFiniteAndNonNegative("lambdaResources", lambdaResources);
        validateFiniteAndNonNegative("lambdaLinks", lambdaLinks);

        double lambdaSum = lambdaVehicles
                + lambdaTasks
                + lambdaResources
                + lambdaLinks;

        if (lambdaSum <= 0.0) {
            throw new IllegalArgumentException(
                    "At least one dynamicity lambda must be > 0."
            );
        }

        validateRate("alphaT", alphaT);
        validatePositive("etaUp", etaUp);
        validatePositive("etaDown", etaDown);
        validateFiniteAndNonNegative("epsilonT", epsilonT);

        if (etaUp < 1.0) {
            throw new IllegalArgumentException(
                    "etaUp should be >= 1.0 because it is an increase factor."
            );
        }

        if (etaDown > 1.0) {
            throw new IllegalArgumentException(
                    "etaDown should be <= 1.0 because it is a decrease factor."
            );
        }

        this.fixedIntervalSeconds = fixedIntervalSeconds;
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
     * Configurazione iniziale per il primo gestore temporale statico.
     *
     * I valori sono scelti per testare il comportamento del sistema su una
     * sequenza piccola di snapshot, non come configurazione definitiva.
     */
    public static TemporalWindowConfig defaultConfig() {
        return new TemporalWindowConfig(
                5.0,    // fixedIntervalSeconds
                0.25,   // thetaLow
                0.65,   // thetaHigh
                0.40,   // rhoKeep
                0.25,   // lambdaVehicles
                0.25,   // lambdaTasks
                0.25,   // lambdaResources
                0.25,   // lambdaLinks
                0.50,   // alphaT
                1.20,   // etaUp
                0.80,   // etaDown
                1.0E-6  // epsilonT
        );
    }

    /**
     * Variante utile nei primi test quando si vuole forzare una finestra fissa
     * senza ragionare ancora sui parametri adattivi.
     */
    public static TemporalWindowConfig fixedInterval(double fixedIntervalSeconds) {
        return new TemporalWindowConfig(
                fixedIntervalSeconds,
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

    /**
     * Restituisce la somma dei pesi lambda.
     *
     * DynamicityEvaluator potrà usarla per normalizzare l'indice globale di
     * dinamicità anche se i lambda non sommano esattamente a 1.
     */
    public double getLambdaSum() {
        return lambdaVehicles
                + lambdaTasks
                + lambdaResources
                + lambdaLinks;
    }

    /**
     * Restituisce il peso normalizzato della componente veicoli.
     */
    public double getNormalizedLambdaVehicles() {
        return lambdaVehicles / getLambdaSum();
    }

    /**
     * Restituisce il peso normalizzato della componente task.
     */
    public double getNormalizedLambdaTasks() {
        return lambdaTasks / getLambdaSum();
    }

    /**
     * Restituisce il peso normalizzato della componente risorse.
     */
    public double getNormalizedLambdaResources() {
        return lambdaResources / getLambdaSum();
    }

    /**
     * Restituisce il peso normalizzato della componente link.
     */
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