# Fase 13D - Live MA-GA runtime bridge and overrun policy

## Obiettivo

La Fase 13D collega il layer runtime live validato nella Fase 13C al core
MA-GA esistente. Il flusso operativo dimostrato e':

```text
MOSAIC coordinator tick
    -> LiveStateCache + LiveSystemSnapshotAssembler 13C in memoria
    -> MaGaLiveMosaicSnapshotBridge
    -> MosaicSystemStateSource
    -> TemporalWindowManager.executeNextStepOrNull(state)
    -> MaGaOptimizer
    -> LiveStrategyApplier diagnostico
    -> trace runtime
```

Non vengono implementati task execution reale, migrazione live, checkpoint,
ripresa upload/download, persistenza remota o scenario finale realistico.

## Relazione con 13C

La 13D deriva dallo scenario isolato
`MaGaLiveInfrastructureSnapshotStudy` e crea
`MaGaLiveMagaRuntimeStudy`. Le classi 13C di cache, accounting Cell,
candidate builder e infrastruttura sono riusate compilando il source tree
13C nel nuovo JAR runtime. Un facade nel package `livestate` espone al runtime
13D uno snapshot core in memoria, senza leggere JSON o log durante la
simulazione.

## Bridge concreto

`MaGaLiveMosaicSnapshotBridge` implementa `window.source.MosaicSnapshotBridge`.
La descrizione e':

```text
LIVE_CAUSAL_MOSAIC_SNAPSHOT_BRIDGE
```

Il bridge conserva snapshot `SystemSnapshot` pubblicati dal coordinator e
risponde a `readSnapshot(observationTimeSeconds)` con lo snapshot piu' recente
con `snapshot.timeSeconds <= observationTimeSeconds`. Se non esiste uno
snapshot causale restituisce `Optional.empty()`.

## MosaicSystemStateSource

La sorgente live non e' duplicata: viene costruita l'istanza esistente
`MosaicSystemStateSource` usando il bridge concreto. Il mode resta:

```text
MOSAIC_LIVE
```

## Manager invariato

`TemporalWindowManager` non e' modificato. Il coordinator non chiama
`run()` in modo bloccante; usa `executeNextStepOrNull(state)` dentro un worker
single-thread e aggiorna lo stato temporale con:

```text
TemporalWindowState.afterStep(step)
```

## Worker single-thread

`LiveGaExecutionCoordinator` usa un `ExecutorService` single-thread. Il worker
non accede alle API MOSAIC: lavora solo con il manager, la source live e lo
snapshot gia' pubblicato nel bridge. La submission avviene solo se non esiste
un GA in flight.

## Thread safety

Le callback MOSAIC aggiornano il layer live e schedulano tick nel thread
coordinator. Il worker riceve uno snapshot immutabile gia' costruito e non
legge stato MOSAIC. Il trasferimento del risultato avviene tramite `Future`.

## Snapshot immutabili

Il facade `LiveStateLayerRuntimeFacade` converte la vista causale 13C in un
`SystemSnapshot` core validato con `SnapshotValidator` e
`LocalCandidateInvariantValidator`. Lo snapshot usa solo osservazioni live
`<= tickTime` e bucket Cell safe `availableFrom <= tickTime`.

## State machine

Stati implementati:

```text
IDLE
GA_RUNNING
RESULT_READY_WITHIN_BOUND
RESULT_APPLIED
WAIT_CAP_REACHED
STALE_RESULT_DISCARDED
FRESH_REOPTIMIZATION_REQUESTED
```

Transizioni principali:

```text
IDLE -> GA_RUNNING
GA_RUNNING -> RESULT_READY_WITHIN_BOUND -> RESULT_APPLIED -> IDLE
GA_RUNNING -> WAIT_CAP_REACHED -> STALE_RESULT_DISCARDED
STALE_RESULT_DISCARDED -> FRESH_REOPTIMIZATION_REQUESTED
FRESH_REOPTIMIZATION_REQUESTED -> GA_RUNNING
```

## DeltaT_max

La policy usa il limite reale prodotto dal core:

```text
step.getAdaptiveWindowDecision().getBounds().getMaximumWindowSeconds()
```

Il runtime wall-clock osservato del GA viene confrontato con quel valore. In
caso di superamento il risultato viene marcato stale.

## Strategia precedente

Durante `GA_RUNNING`, `WAIT_CAP_REACHED` e `STALE_RESULT_DISCARDED` resta
valida l'ultima strategia applicata. Questa scelta evita buchi decisionali ma
puo' mantenere temporaneamente una strategia non ottimale rispetto allo stato
corrente.

## Stale discard

Se il runtime wall-clock supera `DeltaT_max`, il risultato non viene applicato.
Il trace registra:

```text
WAIT_CAP_REACHED
STALE_RESULT_DISCARDED
FRESH_REOPTIMIZATION_REQUESTED
```

La successiva ottimizzazione richiede uno snapshot fresco appena il worker e'
libero.

## Strategy applier diagnostico

`LiveStrategyApplier` conserva `lastAppliedStrategy`, `appliedAtSimulationTime`,
snapshot sorgente, fitness e conteggi di assegnazioni `LOCAL`, `VEHICLE`,
`EDGE` e `CLOUD`. Non simula esecuzione task e non rimuove task dal workload.

## Trace

Output locale:

```text
tmp/mosaic-25.2/logs/<run>/live-maga-runtime/
```

File:

```text
live_ga_runtime_trace.csv
live_strategy_application_trace.csv
live_bridge_snapshot_trace.csv
live_overrun_trace.csv
```

## Run normale

La run normale validata e':

```text
log-20260606-101604-MaGaLiveMagaRuntimeStudy
```

Ha risolto snapshot live, eseguito GA e applicato strategie diagnostiche senza
GA paralleli e senza look-ahead.

## Run overrun

La run overrun diagnostica validata e':

```text
log-20260606-101627-MaGaLiveMagaRuntimeStudy
```

Usa `DIAGNOSTIC_ARTIFICIAL_GA_DELAY` configurato esplicitamente e osserva la
policy di timeout su `DeltaT_max`.

## Risultati

La diagnostica 13D e':

```text
data/mosaic-study/diagnostics/phase_13d_live_maga_runtime_validation.json
```

Esito:

```text
phase13dStatus = COMPLETED
readyForPhase13E = true
snapshotsResolved = 115
gaJobsSubmitted = 115
gaJobsApplied = 111
gaJobsDiscardedAsStale = 2
parallelGaViolations = 0
futureSnapshotViolations = 0
futurePoolViolations = 0
absolutePathsInVersionedDiagnostics = 0
```

## Warning

```text
WARNING_STRATEGY_APPLICATION_IS_DIAGNOSTIC_NOT_TASK_EXECUTION
WARNING_CELL_BANDWIDTH_IS_DIAGNOSTIC_RUNTIME_ACCOUNTING_NOT_FEDERATE_MEASUREMENT
WARNING_REUSED_STRATEGY_MAY_BECOME_STALE_DURING_GA_OVERRUN
WARNING_DIAGNOSTIC_ARTIFICIAL_GA_DELAY_USED_ONLY_FOR_OVERRUN_TEST
WARNING_DIAGNOSTIC_CPU_AND_V2V_BANDWIDTH_REQUIRE_FUTURE_CALIBRATION
```

## Limiti

La 13D non implementa task execution reale, live migration, checkpoint,
persistenza task remoti, calibrazione scientifica o strategy application
operativa. Il test overrun usa un ritardo artificiale diagnostico.

## Readiness 13E

La Fase 13D e' pronta per 13E perche' il bridge concreto e la source live sono
operativi, il manager e il core MA-GA restano invariati, le run normale e
overrun sono validate e non restano errori diagnostici.
