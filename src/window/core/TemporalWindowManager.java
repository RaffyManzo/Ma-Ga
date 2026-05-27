package window.core;

import window.dynamicity.DynamicityBreakdown;
import window.dynamicity.DynamicityEvaluator;
import window.event.CriticalEvent;
import window.event.CriticalEventDetector;
import window.population.PopulationAdapter;
import window.population.PopulationReuseMode;
import window.provider.SystemStateProvider;
import window.state.TemporalStepResult;
import window.state.TemporalWindowResult;
import window.state.TemporalWindowState;
import window.trigger.ReoptimizationTrigger;
import window.trigger.TriggerReason;

import config.window.TemporalWindowConfig;
import ga.core.MaGaOptimizer;
import ga.core.MaGaResult;
import model.genetic.Chromosome;
import model.snapshot.SystemSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Orchestratore del ciclo temporale del MA-GA.
 *
 * <p>Questa classe coordina snapshot, eventi critici, valutazione della
 * dinamicità e riuso della popolazione per decidere quando rieseguire il
 * MA-GA e con quale popolazione iniziale.</p>
 *
 * <p>Il flusso di ogni step è:</p>
 *
 * <ol>
 *     <li>determinare il trigger della prossima esecuzione;</li>
 *     <li>recuperare lo snapshot corrispondente dal {@link SystemStateProvider};</li>
 *     <li>valutare la dinamicità rispetto allo snapshot precedente;</li>
 *     <li>preparare la popolazione iniziale tramite {@link PopulationAdapter};</li>
 *     <li>eseguire il MA-GA;</li>
 *     <li>restituire un {@link TemporalStepResult} con tutti i dati dello step.</li>
 * </ol>
 *
 * <p>Il manager non implementa direttamente la logica genetica, non decide da
 * solo come riparare cromosomi e non conosce il dettaglio della sorgente degli
 * snapshot o degli eventi: delega queste responsabilità alle dipendenze
 * ricevute nel costruttore.</p>
 */
public final class TemporalWindowManager {

    /**
     * Configurazione della finestra temporale.
     *
     * <p>In questa implementazione viene usato soprattutto
     * {@code fixedIntervalSeconds}, che determina la prossima scadenza
     * programmata dopo ogni step.</p>
     */
    private final TemporalWindowConfig windowConfig;

    /**
     * Ottimizzatore MA-GA eseguito a ogni finestra temporale.
     */
    private final MaGaOptimizer optimizer;

    /**
     * Valutatore della dinamicità tra snapshot consecutivi.
     */
    private final DynamicityEvaluator dynamicityEvaluator;

    /**
     * Adattatore che costruisce la popolazione iniziale a partire dalla
     * popolazione finale precedente e dalla modalità di riuso scelta.
     */
    private final PopulationAdapter populationAdapter;

    /**
     * Sorgente degli eventi critici usati per anticipare una finestra.
     */
    private final CriticalEventDetector criticalEventDetector;

    /**
     * Sorgente degli snapshot disponibili nel tempo simulato.
     */
    private final SystemStateProvider systemStateProvider;

    /**
     * Dimensione della popolazione iniziale richiesta per ogni esecuzione MA-GA.
     */
    private final int targetPopulationSize;

    /**
     * Crea un gestore temporale.
     *
     * <p>Tutte le dipendenze sono obbligatorie. Il manager è volutamente
     * composto da oggetti già specializzati, così la logica temporale resta
     * leggibile e testabile per parti.</p>
     *
     * @param windowConfig configurazione della finestra temporale
     * @param optimizer ottimizzatore MA-GA
     * @param dynamicityEvaluator valutatore della dinamicità tra snapshot
     * @param populationAdapter adattatore della popolazione iniziale
     * @param criticalEventDetector detector degli eventi critici
     * @param systemStateProvider provider degli snapshot
     * @param targetPopulationSize dimensione della popolazione richiesta dal GA
     */
    public TemporalWindowManager(
            TemporalWindowConfig windowConfig,
            MaGaOptimizer optimizer,
            DynamicityEvaluator dynamicityEvaluator,
            PopulationAdapter populationAdapter,
            CriticalEventDetector criticalEventDetector,
            SystemStateProvider systemStateProvider,
            int targetPopulationSize
    ) {
        // Configurazione e collaboratori sono obbligatori: il manager non ha fallback impliciti.
        this.windowConfig = Objects.requireNonNull(
                windowConfig,
                "windowConfig must not be null."
        );

        this.optimizer = Objects.requireNonNull(
                optimizer,
                "optimizer must not be null."
        );

        this.dynamicityEvaluator = Objects.requireNonNull(
                dynamicityEvaluator,
                "dynamicityEvaluator must not be null."
        );

        this.populationAdapter = Objects.requireNonNull(
                populationAdapter,
                "populationAdapter must not be null."
        );

        this.criticalEventDetector = Objects.requireNonNull(
                criticalEventDetector,
                "criticalEventDetector must not be null."
        );

        this.systemStateProvider = Objects.requireNonNull(
                systemStateProvider,
                "systemStateProvider must not be null."
        );

        // La popolazione passata al GA deve avere almeno un cromosoma.
        if (targetPopulationSize < 1) {
            throw new IllegalArgumentException(
                    "targetPopulationSize must be >= 1."
            );
        }

        this.targetPopulationSize = targetPopulationSize;
    }

    /**
     * Esegue più finestre temporali a partire dal tempo indicato.
     *
     * <p>L'esecuzione termina quando:</p>
     *
     * <ul>
     *     <li>viene raggiunto {@code maxSteps};</li>
     *     <li>non esistono più snapshot disponibili per il prossimo trigger.</li>
     * </ul>
     *
     * <p>Dopo ogni step riuscito, lo stato temporale viene aggiornato e la
     * prossima scadenza programmata viene fissata a:</p>
     *
     * <pre>
     * triggerTime + fixedIntervalSeconds
     * </pre>
     *
     * @param startTimeSeconds tempo iniziale della simulazione
     * @param maxSteps numero massimo di riesecuzioni MA-GA
     * @return risultato aggregato delle finestre eseguite
     */
    public TemporalWindowResult run(
            double startTimeSeconds,
            int maxSteps
    ) {
        validateFiniteAndNonNegative("startTimeSeconds", startTimeSeconds);

        if (maxSteps < 1) {
            throw new IllegalArgumentException("maxSteps must be >= 1.");
        }

        TemporalWindowState state = TemporalWindowState.initial(
                startTimeSeconds,
                windowConfig.getFixedIntervalSeconds()
        );

        TemporalWindowResult result = TemporalWindowResult.empty();

        for (int i = 0; i < maxSteps; i++) {
            // Un risultato null indica assenza di snapshot disponibili: la sequenza termina.
            TemporalStepResult stepResult = executeNextStepOrNull(state);

            if (stepResult == null) {
                break;
            }

            result = result.append(stepResult);

            double nextScheduledTimeSeconds =
                    stepResult.getTrigger().getTriggerTimeSeconds()
                            + windowConfig.getFixedIntervalSeconds();

            // Lo stato successivo conserva snapshot, risultato e popolazione finale appena prodotti.
            state = TemporalWindowState.afterStep(
                    stepResult,
                    nextScheduledTimeSeconds
            );
        }

        return result;
    }

    /**
     * Esegue una singola finestra temporale.
     *
     * <p>Il metodo restituisce {@code null} quando il provider non riesce a
     * fornire uno snapshot per il tempo del trigger. Questo permette a
     * {@link #run(double, int)} di terminare naturalmente la sequenza senza
     * trattare la fine degli snapshot come errore.</p>
     *
     * @param state stato temporale corrente
     * @return risultato della finestra, oppure null se non ci sono snapshot disponibili
     */
    public TemporalStepResult executeNextStepOrNull(
            TemporalWindowState state
    ) {
        Objects.requireNonNull(state, "state must not be null.");

        ReoptimizationTrigger trigger = resolveTrigger(state);

        // Recupera lo snapshot da ottimizzare al tempo del trigger o subito dopo.
        SystemSnapshot currentSnapshot = systemStateProvider
                .findSnapshotAtOrAfter(trigger.getTriggerTimeSeconds())
                .orElse(null);

        if (currentSnapshot == null) {
            return null;
        }

        // Confronta lo snapshot corrente con l'ultimo snapshot ottimizzato.
        DynamicityBreakdown dynamicityBreakdown =
                dynamicityEvaluator.evaluate(
                        state.getLastSnapshot(),
                        currentSnapshot
                );

        PopulationReuseMode reuseMode =
                dynamicityBreakdown.getSuggestedReuseMode();

        // Prepara P_init(k) secondo FIRST_RUN, WARM_START, PARTIAL_RESTART o COLD_START.
        List<Chromosome> initialPopulation =
                populationAdapter.adaptPopulation(
                        state.getLastFinalPopulation(),
                        currentSnapshot,
                        reuseMode,
                        targetPopulationSize
                );

        // Esegue il MA-GA vero e proprio sullo snapshot corrente.
        MaGaResult maGaResult =
                optimizer.optimizeDetailed(
                        currentSnapshot,
                        initialPopulation
                );

        return new TemporalStepResult(
                state.getWindowIndex(),
                trigger,
                currentSnapshot,
                dynamicityBreakdown,
                reuseMode,
                initialPopulation.size(),
                maGaResult.getFinalPopulation().size(),
                maGaResult
        );
    }

    /**
     * Determina perché eseguire il prossimo MA-GA.
     *
     * <p>La prima esecuzione usa sempre {@link TriggerReason#FIRST_RUN}. Le
     * successive cercano un evento critico tra il tempo corrente e la prossima
     * scadenza programmata. Se esiste, la finestra viene anticipata; altrimenti
     * si usa la scadenza programmata.</p>
     */
    private ReoptimizationTrigger resolveTrigger(
            TemporalWindowState state
    ) {
        // Prima finestra: non c'è ancora storia temporale o popolazione precedente.
        if (!state.hasPreviousExecution()) {
            return ReoptimizationTrigger.firstRun(
                    state.getCurrentTimeSeconds()
            );
        }

        double currentTimeSeconds = state.getCurrentTimeSeconds();
        double scheduledTimeSeconds = state.getNextScheduledTimeSeconds();

        // Eventi critici hanno priorità sulla scadenza naturale della finestra.
        return criticalEventDetector
                .findNextCriticalEvent(
                        currentTimeSeconds,
                        scheduledTimeSeconds
                )
                .map(ReoptimizationTrigger::criticalEvent)
                .orElseGet(
                        () -> ReoptimizationTrigger.scheduledExpiration(
                                scheduledTimeSeconds
                        )
                );
    }

    /**
     * @return configurazione temporale usata dal manager
     */
    public TemporalWindowConfig getWindowConfig() {
        return windowConfig;
    }

    /**
     * @return dimensione target della popolazione iniziale per ogni step
     */
    public int getTargetPopulationSize() {
        return targetPopulationSize;
    }

    /**
     * Valida che un tempo simulato sia finito e non negativo.
     */
    private static void validateFiniteAndNonNegative(
            String fieldName,
            double value
    ) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite.");
        }

        if (value < 0.0) {
            throw new IllegalArgumentException(fieldName + " must be >= 0.");
        }
    }
}



