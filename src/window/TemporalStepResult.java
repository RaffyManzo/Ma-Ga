package window;

import ga.MaGaResult;
import model.SystemSnapshot;

import java.util.Objects;

/**
 * Risultato immutabile di una singola riesecuzione temporale del MA-GA.
 *
 * <p>Ogni istanza rappresenta una finestra effettivamente eseguita da
 * {@link TemporalWindowManager}. Conserva insieme la causa della riesecuzione,
 * lo snapshot ottimizzato, il breakdown della dinamicità, la modalità di riuso
 * scelta per la popolazione e il risultato completo prodotto dal MA-GA.</p>
 *
 * <p>Questa classe è utile sia per aggiornare lo stato temporale successivo sia
 * per costruire report aggregati sulla sequenza di finestre.</p>
 */
public final class TemporalStepResult {

    /**
     * Indice progressivo della finestra eseguita.
     */
    private final int windowIndex;

    /**
     * Causa e tempo della riesecuzione.
     */
    private final ReoptimizationTrigger trigger;

    /**
     * Snapshot usato come input dell'ottimizzazione.
     */
    private final SystemSnapshot snapshot;

    /**
     * Dinamicità misurata rispetto allo snapshot precedente.
     */
    private final DynamicityBreakdown dynamicityBreakdown;

    /**
     * Modalità di riuso della popolazione scelta per questo step.
     */
    private final PopulationReuseMode reuseMode;

    /**
     * Numero di cromosomi passati al MA-GA come popolazione iniziale.
     */
    private final int initialPopulationSize;

    /**
     * Numero di cromosomi restituiti dal MA-GA come popolazione finale.
     */
    private final int finalPopulationSize;

    /**
     * Risultato dettagliato dell'esecuzione MA-GA.
     */
    private final MaGaResult maGaResult;

    /**
     * Crea il risultato di una finestra temporale.
     *
     * <p>Il costruttore valida anche due invarianti di consistenza:</p>
     *
     * <ul>
     *     <li>lo snapshot e il risultato MA-GA devono riferirsi allo stesso {@code snapshotId};</li>
     *     <li>la modalità di riuso deve coincidere con quella suggerita dal breakdown.</li>
     * </ul>
     *
     * @param windowIndex indice della finestra eseguita
     * @param trigger causa della riesecuzione del MA-GA
     * @param snapshot snapshot usato per l'ottimizzazione
     * @param dynamicityBreakdown risultato del confronto tra snapshot
     * @param reuseMode modalità di riuso scelta per la popolazione
     * @param initialPopulationSize dimensione della popolazione iniziale passata al MA-GA
     * @param finalPopulationSize dimensione della popolazione finale restituita dal MA-GA
     * @param maGaResult risultato completo del MA-GA
     */
    public TemporalStepResult(
            int windowIndex,
            ReoptimizationTrigger trigger,
            SystemSnapshot snapshot,
            DynamicityBreakdown dynamicityBreakdown,
            PopulationReuseMode reuseMode,
            int initialPopulationSize,
            int finalPopulationSize,
            MaGaResult maGaResult
    ) {
        // L'indice temporale è progressivo e parte da zero.
        if (windowIndex < 0) {
            throw new IllegalArgumentException("windowIndex must be >= 0.");
        }

        // Le dimensioni possono essere zero solo in casi limite o fallback futuri.
        if (initialPopulationSize < 0) {
            throw new IllegalArgumentException("initialPopulationSize must be >= 0.");
        }

        if (finalPopulationSize < 0) {
            throw new IllegalArgumentException("finalPopulationSize must be >= 0.");
        }

        this.windowIndex = windowIndex;

        this.trigger = Objects.requireNonNull(
                trigger,
                "trigger must not be null."
        );

        this.snapshot = Objects.requireNonNull(
                snapshot,
                "snapshot must not be null."
        );

        this.dynamicityBreakdown = Objects.requireNonNull(
                dynamicityBreakdown,
                "dynamicityBreakdown must not be null."
        );

        this.reuseMode = Objects.requireNonNull(
                reuseMode,
                "reuseMode must not be null."
        );

        this.maGaResult = Objects.requireNonNull(
                maGaResult,
                "maGaResult must not be null."
        );

        this.initialPopulationSize = initialPopulationSize;
        this.finalPopulationSize = finalPopulationSize;

        // Invarianti cross-object: impediscono di aggregare dati di step diversi.
        validateSnapshotConsistency(snapshot, maGaResult);
        validateReuseModeConsistency(dynamicityBreakdown, reuseMode);
    }

    /**
     * @return indice progressivo della finestra eseguita
     */
    public int getWindowIndex() {
        return windowIndex;
    }

    /**
     * @return trigger che ha causato la riesecuzione
     */
    public ReoptimizationTrigger getTrigger() {
        return trigger;
    }

    /**
     * @return snapshot ottimizzato in questo step
     */
    public SystemSnapshot getSnapshot() {
        return snapshot;
    }

    /**
     * @return breakdown della dinamicità associato allo step
     */
    public DynamicityBreakdown getDynamicityBreakdown() {
        return dynamicityBreakdown;
    }

    /**
     * @return modalità di riuso applicata alla popolazione iniziale
     */
    public PopulationReuseMode getReuseMode() {
        return reuseMode;
    }

    /**
     * @return dimensione della popolazione iniziale fornita al MA-GA
     */
    public int getInitialPopulationSize() {
        return initialPopulationSize;
    }

    /**
     * @return dimensione della popolazione finale prodotta dal MA-GA
     */
    public int getFinalPopulationSize() {
        return finalPopulationSize;
    }

    /**
     * @return risultato dettagliato del MA-GA
     */
    public MaGaResult getMaGaResult() {
        return maGaResult;
    }

    /**
     * @return tempo simulato in cui è stata eseguita la finestra
     */
    public double getExecutionTimeSeconds() {
        return trigger.getTriggerTimeSeconds();
    }

    /**
     * @return true se la finestra è stata eseguita per evento critico
     */
    public boolean wasTriggeredByCriticalEvent() {
        return trigger.isCriticalEventTrigger();
    }

    /**
     * @return true se la finestra ha riutilizzato popolazione precedente
     */
    public boolean reusedPreviousPopulation() {
        return reuseMode.usesPreviousPopulation();
    }

    /**
     * Verifica che snapshot e risultato MA-GA si riferiscano allo stesso snapshot.
     *
     * <p>Questa protezione evita di creare uno step che mescola lo snapshot di
     * una finestra con il risultato genetico di un'altra.</p>
     */
    private static void validateSnapshotConsistency(
            SystemSnapshot snapshot,
            MaGaResult maGaResult
    ) {
        if (!snapshot.getSnapshotId().equals(maGaResult.getSnapshotId())) {
            throw new IllegalArgumentException(
                    "snapshot and maGaResult must refer to the same snapshotId."
            );
        }
    }

    /**
     * Verifica che la modalità di riuso sia coerente con il breakdown calcolato.
     *
     * <p>La decisione operativa viene presa dal {@link DynamicityBreakdown}; lo
     * step non deve registrare una modalità divergente.</p>
     */
    private static void validateReuseModeConsistency(
            DynamicityBreakdown dynamicityBreakdown,
            PopulationReuseMode reuseMode
    ) {
        if (dynamicityBreakdown.getSuggestedReuseMode() != reuseMode) {
            throw new IllegalArgumentException(
                    "reuseMode must match dynamicityBreakdown.suggestedReuseMode."
            );
        }
    }

    @Override
    public String toString() {
        return "TemporalStepResult{"
                + "windowIndex=" + windowIndex
                + ", trigger=" + trigger
                + ", snapshotId='" + snapshot.getSnapshotId() + '\''
                + ", dynamicity=" + dynamicityBreakdown.getGlobalDynamicity()
                + ", dynamicityLevel=" + dynamicityBreakdown.getDynamicityLevel()
                + ", reuseMode=" + reuseMode
                + ", initialPopulationSize=" + initialPopulationSize
                + ", finalPopulationSize=" + finalPopulationSize
                + ", finalBestFitness=" + maGaResult.getFinalBestFitness()
                + '}';
    }
}
