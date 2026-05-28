package ga.core;

import ga.fitness.FitnessEvaluator;

import ga.fitness.breakdown.EvaluationBreakdown;
import model.genetic.Chromosome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Risultato completo di una esecuzione del MA-GA su uno snapshot.
 *
 * Questa classe separa:
 *
 * - ottimizzazione;
 * - valutazione;
 * - stampa;
 * - analisi sperimentale;
 * - riuso temporale della popolazione.
 *
 * Nel gestore temporale, ogni finestra k deve poter conservare:
 *
 * - C*_k, cioè il miglior cromosoma trovato;
 * - P_final_k, cioè la popolazione finale ottenuta al termine del GA.
 *
 */
public final class MaGaResult {

    private final String snapshotId;
    private final double snapshotTimeSeconds;

    private final Chromosome bestChromosome;
    private final EvaluationBreakdown bestEvaluation;

    private final int generationsExecuted;
    private final StopReason stopReason;

    private final double initialBestFitness;
    private final double finalBestFitness;

    private final List<GenerationStat> generationHistory;

    /**
     * Popolazione finale prodotta dall'esecuzione del MA-GA.
     *
     * Serve al gestore temporale per riutilizzare una parte o tutta la popolazione
     * nella finestra successiva.
     */
    private final List<Chromosome> finalPopulation;

    public MaGaResult(
            String snapshotId,
            double snapshotTimeSeconds,
            Chromosome bestChromosome,
            EvaluationBreakdown bestEvaluation,
            int generationsExecuted,
            StopReason stopReason,
            double initialBestFitness,
            double finalBestFitness,
            List<GenerationStat> generationHistory,
            List<Chromosome> finalPopulation
    ) {
        this.snapshotId = Objects.requireNonNull(snapshotId, "snapshotId must not be null.");
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
                new ArrayList<>(
                        Objects.requireNonNull(
                                generationHistory,
                                "generationHistory must not be null."
                        )
                )
        );

        this.finalPopulation = Collections.unmodifiableList(
                new ArrayList<>(
                        Objects.requireNonNull(
                                finalPopulation,
                                "finalPopulation must not be null."
                        )
                )
        );
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public double getSnapshotTimeSeconds() {
        return snapshotTimeSeconds;
    }

    public Chromosome getBestChromosome() {
        return bestChromosome;
    }

    public EvaluationBreakdown getBestEvaluation() {
        return bestEvaluation;
    }

    public int getGenerationsExecuted() {
        return generationsExecuted;
    }

    public StopReason getStopReason() {
        return stopReason;
    }

    public double getInitialBestFitness() {
        return initialBestFitness;
    }

    public double getFinalBestFitness() {
        return finalBestFitness;
    }

    public List<GenerationStat> getGenerationHistory() {
        return generationHistory;
    }

    /**
     * Restituisce la popolazione finale prodotta dal MA-GA.
     *
     * Questa lista è immutabile rispetto al riferimento esterno.
     */
    public List<Chromosome> getFinalPopulation() {
        return finalPopulation;
    }
}


