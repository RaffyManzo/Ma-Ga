package window.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Risultato immutabile di una sequenza di finestre temporali.
 *
 * <p>Questa classe aggrega i {@link TemporalStepResult} prodotti da
 * {@link TemporalWindowManager#run(double, int)}. Non esegue calcoli genetici
 * e non modifica gli step: offre solo una vista compatta della sequenza e
 * alcuni helper utili per report e test.</p>
 *
 * <p>La lista interna è immutabile. Ogni operazione di aggiunta restituisce un
 * nuovo {@code TemporalWindowResult}, lasciando invariato l'oggetto originale.</p>
 */
public final class TemporalWindowResult {

    /**
     * Step temporali eseguiti, in ordine di esecuzione.
     */
    private final List<TemporalStepResult> steps;

    /**
     * Crea un risultato temporale aggregato.
     *
     * <p>La lista viene copiata e validata, poi esposta come lista
     * non modificabile. Gli oggetti {@link TemporalStepResult} non vengono
     * clonati perché sono già pensati come risultati immutabili.</p>
     *
     * @param steps risultati delle finestre eseguite
     */
    public TemporalWindowResult(List<TemporalStepResult> steps) {
        if (steps == null) {
            throw new IllegalArgumentException("steps must not be null.");
        }

        for (TemporalStepResult step : steps) {
            // Uno step nullo renderebbe ambigue statistiche e aggregazioni successive.
            if (step == null) {
                throw new IllegalArgumentException("steps must not contain null elements.");
            }
        }

        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
    }

    /**
     * Crea un risultato vuoto.
     *
     * <p>Usato come accumulatore iniziale quando il manager inizia a eseguire
     * una sequenza di finestre.</p>
     *
     * @return risultato senza step
     */
    public static TemporalWindowResult empty() {
        return new TemporalWindowResult(Collections.emptyList());
    }

    /**
     * Crea un risultato contenente un solo step.
     *
     * @param step risultato della singola finestra
     * @return risultato aggregato con un elemento
     */
    public static TemporalWindowResult single(TemporalStepResult step) {
        if (step == null) {
            throw new IllegalArgumentException("step must not be null.");
        }

        List<TemporalStepResult> steps = new ArrayList<>();
        steps.add(step);

        return new TemporalWindowResult(steps);
    }

    /**
     * @return lista immutabile degli step eseguiti
     */
    public List<TemporalStepResult> getSteps() {
        return steps;
    }

    /**
     * @return numero di step temporali aggregati
     */
    public int getStepCount() {
        return steps.size();
    }

    /**
     * @return {@code true} se non è stato eseguito nessuno step
     */
    public boolean isEmpty() {
        return steps.isEmpty();
    }

    /**
     * @return primo step, se presente
     */
    public Optional<TemporalStepResult> getFirstStep() {
        if (steps.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(steps.get(0));
    }

    /**
     * @return ultimo step, se presente
     */
    public Optional<TemporalStepResult> getLastStep() {
        if (steps.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(steps.get(steps.size() - 1));
    }

    /**
     * @return numero di finestre rieseguite per evento critico
     */
    public long countCriticalEventSteps() {
        return steps.stream()
                .filter(TemporalStepResult::wasTriggeredByCriticalEvent)
                .count();
    }

    /**
     * @return numero di finestre che hanno riutilizzato popolazione precedente
     */
    public long countPopulationReuseSteps() {
        return steps.stream()
                .filter(TemporalStepResult::reusedPreviousPopulation)
                .count();
    }

    /**
     * @return migliore fitness finale osservata nella sequenza
     */
    public Optional<Double> getBestFinalFitness() {
        return steps.stream()
                .map(step -> step.getMaGaResult().getFinalBestFitness())
                .min(Double::compareTo);
    }

    /**
     * Restituisce un nuovo risultato con uno step aggiunto.
     *
     * <p>L'oggetto corrente resta invariato. Questa scelta rende il risultato
     * aggregato semplice da passare tra metodi senza effetti collaterali.</p>
     *
     * @param step nuovo step da aggiungere
     * @return nuovo TemporalWindowResult immutabile
     */
    public TemporalWindowResult append(TemporalStepResult step) {
        if (step == null) {
            throw new IllegalArgumentException("step must not be null.");
        }

        List<TemporalStepResult> updatedSteps = new ArrayList<>(steps);
        updatedSteps.add(step);

        return new TemporalWindowResult(updatedSteps);
    }

    @Override
    public String toString() {
        return "TemporalWindowResult{"
                + "stepCount=" + getStepCount()
                + ", criticalEventSteps=" + countCriticalEventSteps()
                + ", populationReuseSteps=" + countPopulationReuseSteps()
                + ", bestFinalFitness=" + getBestFinalFitness().orElse(null)
                + '}';
    }
}



