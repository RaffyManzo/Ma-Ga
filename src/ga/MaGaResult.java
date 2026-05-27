package ga;

import ga.FitnessEvaluator;
import ga.GenerationStat;
import ga.StopReason;
import model.Chromosome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Risultato completo di una esecuzione del MA-GA su uno snapshot.
 *
 * Questa classe è fondamentale perché separa:
 *
 * - ottimizzazione;
 * - valutazione;
 * - stampa;
 * - analisi sperimentale.
 *
 * In futuro, quando il MA-GA sarà integrato con MOSAIC, un MaGaResult potrà
 * essere prodotto per ogni snapshot o finestra temporale.
 */
public final class MaGaResult {

    private final String snapshotId;
    private final double snapshotTimeSeconds;

    private final Chromosome bestChromosome;
    private final FitnessEvaluator.EvaluationBreakdown bestEvaluation;

    private final int generationsExecuted;
    private final StopReason stopReason;

    private final double initialBestFitness;
    private final double finalBestFitness;

    private final List<GenerationStat> generationHistory;

    /**
     * Costruisce il risultato completo del MA-GA.
     *
     * Parametri in ingresso:
     * - snapshotId: identificativo dello snapshot ottimizzato;
     * - snapshotTimeSeconds: tempo simulato associato allo snapshot;
     * - bestChromosome: miglior cromosoma trovato;
     * - bestEvaluation: breakdown della fitness del miglior cromosoma;
     * - generationsExecuted: numero di generazioni effettivamente eseguite;
     * - stopReason: motivo di arresto;
     * - initialBestFitness: migliore fitness nella popolazione iniziale;
     * - finalBestFitness: migliore fitness finale;
     * - generationHistory: storico delle statistiche generazionali.
     *
     * Output:
     * - nuova istanza immutabile di MaGaResult.
     */
    public MaGaResult(
            String snapshotId,
            double snapshotTimeSeconds,
            Chromosome bestChromosome,
            FitnessEvaluator.EvaluationBreakdown bestEvaluation,
            int generationsExecuted,
            StopReason stopReason,
            double initialBestFitness,
            double finalBestFitness,
            List<GenerationStat> generationHistory
    ) {
        this.snapshotId = snapshotId;
        this.snapshotTimeSeconds = snapshotTimeSeconds;
        this.bestChromosome = Objects.requireNonNull(
                bestChromosome,
                "bestChromosome must not be null."
        );
        this.bestEvaluation = Objects.requireNonNull(
                bestEvaluation,
                "bestEvaluation must not be null."
        );
        this.generationsExecuted = generationsExecuted;
        this.stopReason = Objects.requireNonNull(
                stopReason,
                "stopReason must not be null."
        );
        this.initialBestFitness = initialBestFitness;
        this.finalBestFitness = finalBestFitness;
        this.generationHistory = Collections.unmodifiableList(
                new ArrayList<>(Objects.requireNonNull(
                        generationHistory,
                        "generationHistory must not be null."
                ))
        );
    }

    /**
     * Restituisce l'identificativo dello snapshot.
     *
     * Output:
     * - snapshotId.
     */
    public String getSnapshotId() {
        return snapshotId;
    }

    /**
     * Restituisce il tempo simulato dello snapshot.
     *
     * Output:
     * - tempo simulato in secondi.
     */
    public double getSnapshotTimeSeconds() {
        return snapshotTimeSeconds;
    }

    /**
     * Restituisce il miglior cromosoma trovato.
     *
     * Output:
     * - Chromosome migliore.
     */
    public Chromosome getBestChromosome() {
        return bestChromosome;
    }

    /**
     * Restituisce il breakdown della fitness del miglior cromosoma.
     *
     * Output:
     * - EvaluationBreakdown del miglior cromosoma.
     */
    public FitnessEvaluator.EvaluationBreakdown getBestEvaluation() {
        return bestEvaluation;
    }

    /**
     * Restituisce il numero di generazioni eseguite.
     *
     * Output:
     * - numero di generazioni completate.
     */
    public int getGenerationsExecuted() {
        return generationsExecuted;
    }

    /**
     * Restituisce il motivo di arresto del MA-GA.
     *
     * Output:
     * - StopReason.
     */
    public StopReason getStopReason() {
        return stopReason;
    }

    /**
     * Restituisce la migliore fitness iniziale.
     *
     * Output:
     * - migliore fitness nella popolazione iniziale.
     */
    public double getInitialBestFitness() {
        return initialBestFitness;
    }

    /**
     * Restituisce la fitness finale.
     *
     * Output:
     * - fitness del miglior cromosoma finale.
     */
    public double getFinalBestFitness() {
        return finalBestFitness;
    }

    /**
     * Restituisce lo storico delle generazioni.
     *
     * Output:
     * - lista immutabile di GenerationStat.
     */
    public List<GenerationStat> getGenerationHistory() {
        return generationHistory;
    }
}