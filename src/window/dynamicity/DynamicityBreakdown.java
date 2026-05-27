package window.dynamicity;

import window.population.PopulationReuseMode;

import java.util.Objects;

/**
 * Risultato immutabile del confronto tra due snapshot consecutivi.
 *
 * <p>Questa classe non calcola la dinamicità: conserva il risultato prodotto da
 * {@link DynamicityEvaluator} in una forma leggibile, validata e facile da
 * usare nel resto del package {@code window}.</p>
 *
 * <p>Il breakdown mantiene sia i valori numerici intermedi sia la decisione
 * operativa finale. In particolare permette di sapere:</p>
 *
 * <ul>
 *     <li>quali snapshot sono stati confrontati;</li>
 *     <li>a quali istanti temporali appartengono;</li>
 *     <li>quanto sono cambiati veicoli, task, risorse e link;</li>
 *     <li>qual è la dinamicità globale normalizzata;</li>
 *     <li>quale livello qualitativo è stato rilevato;</li>
 *     <li>quale modalità di riuso della popolazione è suggerita.</li>
 * </ul>
 *
 * <p>Tutte le componenti di variazione sono attese nell'intervallo
 * {@code [0, 1]}, dove {@code 0} significa nessun cambiamento e {@code 1}
 * significa cambiamento massimo.</p>
 */
public final class DynamicityBreakdown {

    /**
     * Identificativo dello snapshot precedente.
     *
     * <p>Può essere {@code null} solo nel caso speciale della prima finestra,
     * quando non esiste ancora uno snapshot precedente.</p>
     */
    private final String previousSnapshotId;

    /**
     * Identificativo dello snapshot corrente valutato.
     */
    private final String currentSnapshotId;

    /**
     * Tempo simulato associato allo snapshot precedente.
     */
    private final double previousSnapshotTimeSeconds;

    /**
     * Tempo simulato associato allo snapshot corrente.
     */
    private final double currentSnapshotTimeSeconds;

    /**
     * Variazione dell'insieme dei veicoli osservati.
     */
    private final double vehicleVariation;

    /**
     * Variazione dell'insieme dei task attivi.
     */
    private final double taskVariation;

    /**
     * Variazione media delle risorse computazionali disponibili.
     */
    private final double resourceVariation;

    /**
     * Variazione media dei link/candidati source-aware.
     */
    private final double linkVariation;

    /**
     * Indice globale ottenuto combinando le quattro componenti elementari.
     */
    private final double globalDynamicity;

    /**
     * Interpretazione qualitativa dell'indice globale.
     */
    private final DynamicityLevel dynamicityLevel;

    /**
     * Modalità di riuso della popolazione suggerita dal livello rilevato.
     */
    private final PopulationReuseMode suggestedReuseMode;

    /**
     * Costruisce un breakdown completo della dinamicità.
     *
     * <p>Il costruttore valida i campi numerici per impedire la creazione di
     * risultati incoerenti. Gli identificativi e le decisioni qualitative
     * vengono invece conservati così come prodotti dal valutatore, con la sola
     * eccezione dello snapshot corrente e delle decisioni finali, che sono
     * obbligatori.</p>
     *
     * @param previousSnapshotId identificativo dello snapshot precedente, o {@code null}
     * @param currentSnapshotId identificativo dello snapshot corrente
     * @param previousSnapshotTimeSeconds tempo dello snapshot precedente
     * @param currentSnapshotTimeSeconds tempo dello snapshot corrente
     * @param vehicleVariation variazione dei veicoli in {@code [0, 1]}
     * @param taskVariation variazione dei task in {@code [0, 1]}
     * @param resourceVariation variazione delle risorse in {@code [0, 1]}
     * @param linkVariation variazione dei link in {@code [0, 1]}
     * @param globalDynamicity dinamicità globale in {@code [0, 1]}
     * @param dynamicityLevel livello qualitativo associato alla dinamicità
     * @param suggestedReuseMode modalità di riuso suggerita
     */
    public DynamicityBreakdown(
            String previousSnapshotId,
            String currentSnapshotId,
            double previousSnapshotTimeSeconds,
            double currentSnapshotTimeSeconds,
            double vehicleVariation,
            double taskVariation,
            double resourceVariation,
            double linkVariation,
            double globalDynamicity,
            DynamicityLevel dynamicityLevel,
            PopulationReuseMode suggestedReuseMode
    ) {
        this.previousSnapshotId = previousSnapshotId;

        // Lo snapshot corrente identifica sempre il risultato della valutazione.
        this.currentSnapshotId = Objects.requireNonNull(
                currentSnapshotId,
                "currentSnapshotId must not be null."
        );

        // I tempi devono essere numeri reali validi, ma non sono limitati a [0, 1].
        validateFinite("previousSnapshotTimeSeconds", previousSnapshotTimeSeconds);
        validateFinite("currentSnapshotTimeSeconds", currentSnapshotTimeSeconds);

        // Tutte le metriche di variazione condividono il dominio normalizzato [0, 1].
        validateRate("vehicleVariation", vehicleVariation);
        validateRate("taskVariation", taskVariation);
        validateRate("resourceVariation", resourceVariation);
        validateRate("linkVariation", linkVariation);
        validateRate("globalDynamicity", globalDynamicity);

        this.previousSnapshotTimeSeconds = previousSnapshotTimeSeconds;
        this.currentSnapshotTimeSeconds = currentSnapshotTimeSeconds;

        this.vehicleVariation = vehicleVariation;
        this.taskVariation = taskVariation;
        this.resourceVariation = resourceVariation;
        this.linkVariation = linkVariation;

        this.globalDynamicity = globalDynamicity;

        this.dynamicityLevel = Objects.requireNonNull(
                dynamicityLevel,
                "dynamicityLevel must not be null."
        );

        this.suggestedReuseMode = Objects.requireNonNull(
                suggestedReuseMode,
                "suggestedReuseMode must not be null."
        );
    }

    /**
     * Breakdown speciale per la prima finestra.
     *
     * <p>Nella prima finestra non esiste uno snapshot precedente, quindi la
     * dinamicità non è realmente misurabile. Per convenzione tutte le
     * variazioni numeriche vengono impostate a {@code 0}, mentre il livello è
     * {@link DynamicityLevel#UNKNOWN} e la modalità suggerita è
     * {@link PopulationReuseMode#FIRST_RUN}.</p>
     *
     * @param currentSnapshotId identificativo dello snapshot corrente
     * @param currentSnapshotTimeSeconds tempo simulato dello snapshot corrente
     * @return breakdown coerente con la prima esecuzione
     */
    public static DynamicityBreakdown firstRun(
            String currentSnapshotId,
            double currentSnapshotTimeSeconds
    ) {
        return new DynamicityBreakdown(
                null,
                currentSnapshotId,
                currentSnapshotTimeSeconds,
                currentSnapshotTimeSeconds,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                DynamicityLevel.UNKNOWN,
                PopulationReuseMode.FIRST_RUN
        );
    }

    /**
     * @return identificativo dello snapshot precedente, o {@code null} alla prima esecuzione
     */
    public String getPreviousSnapshotId() {
        return previousSnapshotId;
    }

    /**
     * @return identificativo dello snapshot corrente
     */
    public String getCurrentSnapshotId() {
        return currentSnapshotId;
    }

    /**
     * @return tempo simulato dello snapshot precedente
     */
    public double getPreviousSnapshotTimeSeconds() {
        return previousSnapshotTimeSeconds;
    }

    /**
     * @return tempo simulato dello snapshot corrente
     */
    public double getCurrentSnapshotTimeSeconds() {
        return currentSnapshotTimeSeconds;
    }

    /**
     * @return variazione dei veicoli in {@code [0, 1]}
     */
    public double getVehicleVariation() {
        return vehicleVariation;
    }

    /**
     * @return variazione dei task in {@code [0, 1]}
     */
    public double getTaskVariation() {
        return taskVariation;
    }

    /**
     * @return variazione delle risorse in {@code [0, 1]}
     */
    public double getResourceVariation() {
        return resourceVariation;
    }

    /**
     * @return variazione dei link/candidati in {@code [0, 1]}
     */
    public double getLinkVariation() {
        return linkVariation;
    }

    /**
     * @return indice globale di dinamicità in {@code [0, 1]}
     */
    public double getGlobalDynamicity() {
        return globalDynamicity;
    }

    /**
     * @return livello qualitativo della dinamicità
     */
    public DynamicityLevel getDynamicityLevel() {
        return dynamicityLevel;
    }

    /**
     * @return modalità di riuso della popolazione suggerita
     */
    public PopulationReuseMode getSuggestedReuseMode() {
        return suggestedReuseMode;
    }

    /**
     * Indica se il breakdown è stato calcolato confrontando due snapshot reali.
     *
     * @return {@code true} se esiste uno snapshot precedente
     */
    public boolean hasPreviousSnapshot() {
        return previousSnapshotId != null;
    }

    /**
     * Indica se questo breakdown rappresenta la prima finestra temporale.
     *
     * @return {@code true} se la modalità suggerita è {@link PopulationReuseMode#FIRST_RUN}
     */
    public boolean isFirstRun() {
        return suggestedReuseMode == PopulationReuseMode.FIRST_RUN;
    }

    /**
     * @return {@code true} se il breakdown suggerisce un warm start
     */
    public boolean suggestsWarmStart() {
        return suggestedReuseMode == PopulationReuseMode.WARM_START;
    }

    /**
     * @return {@code true} se il breakdown suggerisce un partial restart
     */
    public boolean suggestsPartialRestart() {
        return suggestedReuseMode == PopulationReuseMode.PARTIAL_RESTART;
    }

    /**
     * @return {@code true} se il breakdown suggerisce un cold start
     */
    public boolean suggestsColdStart() {
        return suggestedReuseMode == PopulationReuseMode.COLD_START;
    }

    /**
     * Valida che un valore numerico sia finito.
     *
     * <p>Questa validazione è usata per campi temporali, che devono essere
     * numeri reali ma non sono metriche normalizzate.</p>
     */
    private static void validateFinite(String fieldName, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite.");
        }
    }

    /**
     * Valida una metrica normalizzata.
     *
     * <p>Le componenti di dinamicità e l'indice globale devono essere sempre
     * valori finiti nell'intervallo {@code [0, 1]}.</p>
     */
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
        return "DynamicityBreakdown{"
                + "previousSnapshotId='" + previousSnapshotId + '\''
                + ", currentSnapshotId='" + currentSnapshotId + '\''
                + ", previousSnapshotTimeSeconds=" + previousSnapshotTimeSeconds
                + ", currentSnapshotTimeSeconds=" + currentSnapshotTimeSeconds
                + ", vehicleVariation=" + vehicleVariation
                + ", taskVariation=" + taskVariation
                + ", resourceVariation=" + resourceVariation
                + ", linkVariation=" + linkVariation
                + ", globalDynamicity=" + globalDynamicity
                + ", dynamicityLevel=" + dynamicityLevel
                + ", suggestedReuseMode=" + suggestedReuseMode
                + '}';
    }
}



