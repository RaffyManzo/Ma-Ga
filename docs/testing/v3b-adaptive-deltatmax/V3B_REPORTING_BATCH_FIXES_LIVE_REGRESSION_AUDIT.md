# V3-B reporting and batch fixes - live regression audit

## Identification

- Branch: experiment/v3b-reporting-batch-fixes
- Base commit: 7c5733eb6ee840afc709c7631ce7de7d957f2b51
- Regression archive: V3B_micro_pilot_batch_20260628-193821.zip
- Regression archive SHA-256: 44cb529cba1a2139e0785a418d3a41fda8109315f3a85fb82ed344166f5ff3a5
- Runtime JAR SHA-256: 0cf9045abfd7ad7e12dc6e89529403f30d11b643eb4489d862b1f205b1cb9cd7
- Executions: 6
- Batch status: PASS_ALL_RUNS
- Corrected analysis status: ALL_EXECUTIONS_TECHNICALLY_VALID

## Scope

The regression validates only the reporting and post-processing changes:

- explicit terminal accounting of SHUTDOWN_IN_FLIGHT;
- accepted adaptive samples deduplicated by jobId;
- appliedSnapshotAgeSimulationSeconds persisted per applied job;
- count, minimum, mean, median, P95 and maximum age statistics;
- coherent JSON, JSONL, text and Markdown reporting.

Estimator, temporal bounds, fitness, repair and GA behavior were not changed.

## Execution results

| Configuration | Mode | Classification | Submitted | Applied | Stale | Shutdown | Unique adaptive samples | Max applied snapshot age (s) |
|---|---|---|---:|---:|---:|---:|---:|---:|
| 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
| 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
| 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
| 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
| 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
| 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |

## Validation

- Six runner exit codes are zero.
- Six simulations completed.
- Six validators report LITERATURE_SMOKE_TEST_PASSED.
- Validator errors: 0.
- Failed GA jobs: 0.
- Null GA results: 0.
- Total temporal and resource violations: 0.
- Terminal accounting is exact for all six runs.
- Applied snapshot age count equals applied job count for all six runs.

## Scientific interpretation

The patch is technically valid and ready for commit.

The current adaptive parameter set is not approved for the full campaign.
In CFG-N-I adaptive, one applied result used a snapshot with a simulation age of 22.0 seconds.
The new metric correctly exposes this trade-off, which was previously hidden.
Results also vary from the previous single-seed pilot because GA wall-clock execution is non-deterministic.

Parameter decision: NO_GO_FULL_CAMPAIGN.
Next scientific step: targeted recalibration followed by multi-seed replication.

## Final decision

- Reporting implementation: PASS.
- Batch classification: PASS.
- Live regression: PASS.
- Ready for commit and push: YES.
- Ready for full scientific campaign: NO.
