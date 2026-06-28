# V3-B adaptive deltaTMax — implementation validation audit

## Identification

- Validation date: 2026-06-28 17:29:36 +02:00
- Repository: C:\Users\raffa\IdeaProjects\maga-core-v3b-deltatmax
- Branch: experiment/v3b-adaptive-deltatmax
- Base HEAD: c7bea739c112879f30188d8b0c4faf04966601ae
- Decision: PASS_READY_FOR_MICRO_PILOTS

## Scope

The validation covers the combined V3-A and V3-B implementation:

- optimized local CPU contention repair from V3-A;
- explicit opt-in LIVE_ADAPTIVE deltaTMax mode;
- immutable per-job deltaTMax at submission;
- propagation of the same deltaTMax to the temporal core;
- separated submission and post-completion telemetry;
- explicit temporal-bound conflict detection.

Replay and CONFIGURED_STATIC behavior remain unchanged.

## Build

- Java version: 17
- Core sources: 153
- Live-state-layer sources: 27
- Live runtime sources: 23
- Generated classes: 268
- Runtime JAR SHA-256: b291d29efd2e07140edd4c2b2042ff2e68b1c15e4ec3a010d7be21286f6585d2

The class count increased from 267 to 268 because the correction introduced the explicit nested BoundConflictException.

## V3-B harnesses

- Estimator harness: PASS, 47 assertions
- Integration harness: PASS, 19 assertions
- Core/live consistency harness: PASS, 16 assertions
- Bound-conflict harness: PASS, 15 assertions

## Regression checks

- Local CPU contention repair: PASS, 44 assertions
- G02B experimental variant: PASS
- Local CPU contention telemetry: PASS, 24 assertions
- Phase14C3R reporting: PASS

The compatible regression harnesses were executed with:

-Dmaga.repair.verifyLocalContentionDelta=true

## Corrected invariants

1. The deltaTMax assigned at submission is the value used by the temporal core for the same job.
2. The per-job submission value remains immutable while the job is in flight.
3. Post-completion estimator updates affect only later submissions.
4. Real core/live mismatches are measured rather than suppressed.
5. Incompatible minimum and maximum temporal bounds block submission explicitly.
6. Completed stale runtimes remain valid estimator samples.
7. Incomplete, invalid, non-positive, NaN and infinite samples remain excluded.

## Git state at validation

- Staged files: 0
- Commit created during validation: no
- Push performed during validation: no
- Working tree: expected V3-B source changes only

## Evidence

- Local evidence directory: C:\Users\raffa\IdeaProjects\maga-core-v3b-deltatmax\tmp\v3b-adaptive-deltatmax\local-correction-validation-20260628-171637
- Versioned evidence manifest: docs/testing/v3b-adaptive-deltatmax/V3B_ADAPTIVE_DELTATMAX_EVIDENCE_MANIFEST_SHA256.csv
- Evidence files recorded: 64

## Remaining limitations

- No MOSAIC/SUMO micro pilot was executed during implementation validation.
- Scientific pilot parameters have not yet been selected.
- The implementation is ready for controlled parameter definition and micro pilots, not yet for the full experimental campaign.

## Next step

Define controlled LIVE_ADAPTIVE parameter sets and execute the nominal-intermediate, high-intermediate and high-stress micro pilots before deciding whether to start the full campaign.
