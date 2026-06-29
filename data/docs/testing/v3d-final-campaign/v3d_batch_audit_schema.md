# V3-D batch audit schema

Each run produces:

- `runner-console.txt`;
- `run-result.json`;
- `cleanup.json`;
- copied `live-maga-runtime/` evidence when available;
- selected MOSAIC support files.

Terminal statuses:

- `PASSED`;
- `FAILED_PREFLIGHT`;
- `FAILED_TIMEOUT`;
- `FAILED_EXECUTION`;
- `FAILED_POSTPROCESS`;
- `FAILED_VALIDATION`;
- `FAILED_CLEANUP`.

A failed run does not stop the batch unless residual MOSAIC/SUMO/Java
processes cannot be removed safely. This exception prevents contamination of
later observations.

Per-group reports are written under:

```text
tmp/v3d-final-campaign-results/reports/
```

The same directory contains cumulative CSV, JSON and Markdown reports.

Preparation and execution safety:

- invalid partial materializations are moved under the campaign archive root;
- `campaign-ready.json` freezes all tooling inputs and complete materialization
  tree fingerprints;
- an initial residual-process check is mandatory before G01;
- a failed residual-process query is an unsafe-environment abort;
- stale lock recovery is recorded in `batch-lock-audit.json`.

## Runtime-policy evidence

Each materialization must contain a runtime configuration and final manifest
matching policy `V3D_LIVE_ADAPTIVE_FRESHNESS_AWARE`. Readiness fails if any
field differs, including `gaWallClockBudgetMode=LIVE_ADAPTIVE`, adaptive
history parameters, freshness cap or cooperative stop.

## Frozen execution dependencies

Readiness records the SHA-256 of the MA-GA runtime JAR, the diagnostic ad-hoc
radio JAR, the runner/deploy/reporting scripts and `mosaic.bat`. Any change
before batch execution blocks G01.

Failure classification follows the last completed phase and therefore
distinguishes setup, simulator execution, evidence collection, result
validation and residual-process cleanup.
