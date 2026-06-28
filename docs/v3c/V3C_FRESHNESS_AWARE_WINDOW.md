# V3-C — Freshness-aware temporal window

## Scope

V3-C is the last implementation iteration before the final experimental gate. It keeps unchanged:

- fitness function;
- repair rules;
- chromosome and gene structure;
- selection, crossover, mutation and elitism;
- offloading decision variables and candidate types.

The changes are limited to temporal orchestration, cooperative termination and reporting.

## Temporal semantics

The implementation now keeps four quantities distinct:

1. `DeltaT_min`, computed as `T_s + T_GA_est + T_apply + epsilon`;
2. `DeltaT_max`, derived from `alpha_T * T_coverage_ref`, with the configured maximum used only when no coverage reference is available;
3. the GA wall-clock budget;
4. the maximum acceptable snapshot age in simulation time.

The same robust GA estimate feeds `T_GA_est` and the wall-clock budget, but the wall-clock value is never injected as `DeltaT_max`.

## Robust runtime estimate

The adaptive estimator:

- accepts completed, positive and finite runtimes with at least one task;
- includes both applied and stale completed jobs because both measure actual GA cost;
- excludes zero-task jobs, failures, null results and in-flight shutdown jobs;
- uses a rolling history of 20 valid samples and nearest-rank P95;
- applies the configured safety margin and bounded update steps.

The safety margin is also reserved before the hard wall-clock deadline so the optimizer can finalize, fully evaluate and publish the best-so-far result.

## Freshness and stale classification

At classification time the coordinator checks independently:

- wall-clock runtime against the hard GA budget;
- current simulation time minus snapshot simulation time against the freshness cap.

The possible classifications are:

- `NONE` (fresh and within budget);
- `WALL_CLOCK`;
- `SIMULATION_AGE`;
- `WALL_CLOCK_AND_SIMULATION_AGE`.

A stale result is never applied. The last valid strategy remains active and a fresh reoptimization is requested.

## Cooperative best-so-far

The optimizer checks a cooperative deadline only at safe boundaries. A partially built generation is discarded. The returned chromosome is always a previously complete, repaired and evaluated individual, followed by the existing final prudential evaluation. The new termination reason is `TIME_BUDGET_BEST_SO_FAR`.

This is an operational stop criterion; it does not alter the optimization objective or the genetic operators.

## Stale strategy evidence

For every stale result V3-C writes:

- `live_stale_assignment_decisions.csv`;
- `live_stale_assignment_distribution.csv`;
- `live_stale_strategy_summary.jsonl`;
- `live_stale_vs_active_strategy.csv`.

The evidence contains the complete assignment for every task, the LOCAL/VEHICLE/EDGE/CLOUD distribution, temporal data, fitness diagnostics and a per-task comparison with the active strategy. A task present in the stale current-state strategy but absent from the active strategy is explicitly classified as `UNASSIGNED_BY_ACTIVE_STRATEGY`.

## Initial parameters for the gate

- adaptive history: 20 valid samples;
- percentile: nearest-rank P95;
- warm-up: 3 samples;
- safety/finalization reserve: 0.10 s;
- maximum step up: 0.25 s;
- maximum step down: 0.10 s;
- initial simulation-age cap: 2.0 s.

The 2.0 s cap is an initial experimental gate value: it retains the ordinary applied ages observed in V3-B while rejecting the demonstrated 22 s obsolete application. It must be reported as a configured experimental parameter, not as a universal theoretical constant.

## Excluded changes

V3-C does not introduce simulation-speed-aware budgets, admission control, snapshot coalescence, complexity buckets, automatic LOCAL fallback, stale warm start or current-state gene revalidation.

## R2: reporting canonico e semantica del limite

La revisione R2 completa il perimetro V3-C senza modificare fitness, repair,
cromosoma o operatori genetici.

- Il reporting principale mantiene gli alias V3-B ma aggiunge
  `finalClassification`, `staleReason`,
  `gaWallClockBudgetAtSubmissionSeconds`,
  `temporalMaximumAtSubmissionSeconds`,
  `maxSnapshotAgeSimulationSeconds` e
  `snapshotAgeAtClassificationSeconds`.
- Le classificazioni terminali distinguono
  `APPLIED_WITHIN_BUDGET_AND_FRESH`, `STALE_WALL_CLOCK`,
  `STALE_SIMULATION_AGE` e
  `STALE_WALL_CLOCK_AND_SIMULATION_AGE`.
- Il confronto tra strategia stale e strategia attiva produce anche
  `live_stale_vs_active_transition_matrix.csv`, percentuali per tipo di nodo
  e il flag esplicito `activeStrategyPresent`.
- Il budget wall-clock è inclusivo: un completamento esattamente uguale al
  limite è ammissibile; soltanto il superamento effettivo è stale.
