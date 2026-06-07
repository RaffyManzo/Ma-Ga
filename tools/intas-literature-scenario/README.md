# MA-GA Literature Scenario: synthetic mobility calibrated on InTAS

This tool materializes and runs `MaGaLiteratureBasedUrbanStudy`, the literature-based MOSAIC/SUMO scenario used to validate the live MA-GA runtime.

The final design intentionally separates topology realism from traffic controllability:

- the road network is a real reduced InTAS subnetwork from Ingolstadt;
- the two RSUs are placed at trajectory-validated coordinates;
- vehicle demand is synthetic, deterministic and calibrated against an observed InTAS window;
- SUMO still simulates mobility live during each MOSAIC run;
- MOSAIC Scenario-Convert still creates the real SQLite database used by the application federate.

The final mobility mode is:

```text
SYNTHETIC_CALIBRATED_ON_INTAS_SUBNETWORK
```

## Why the design changed

The earlier implementation tried to replay a precise intermediate InTAS window. That required cutting full-day route files, handling vehicles already active at the beginning of the window and restoring SUMO save-states. The approach became fragile and added complexity unrelated to MA-GA validation.

The current implementation keeps the validated urban topology but generates a small controlled vehicle demand directly in the interval `0-180 s`. This preserves live mobility while making the scenario reproducible and easy to inspect.

## Validated nominal baseline

The nominal synthetic profile uses:

```text
InTAS subnetwork: candidate_0045
external edges: 155
external junctions: 88
traffic lights: 8

rsu_0: x=213980.44, y=450429.42
rsu_1: x=213897.79, y=450744.66
coverage radius: 250 m

vehicles: 50
DUAL_RSU_SWITCH: 21
BOTH_RSU_NO_SWITCH: 7
RSU_0_ONLY: 7
RSU_1_ONLY: 5
BACKGROUND: 10
```

The manually validated SUMO baseline produced:

```text
mean active vehicles: 31.86
maximum active vehicles: 46
vehicles visiting both RSUs: 20
vehicles with gateway switch: 17
gateway switch events: 17
SUMO errors: 0
teleports: 0
emergency braking: 0
```

The reference InTAS window had approximately `29.52` active vehicles on average. The synthetic baseline is intentionally close, not identical.

## Required local tools

Keep these local directories:

```text
tmp/mosaic-25.2
tmp/external-tools/scenario-convert-25.2
```

`tmp/mosaic-25.2` must not be deleted: it contains `mosaic.bat` and the local MOSAIC runtime.

The materializer also requires:

```text
Java
Python launcher: py
SUMO_HOME
sumo
netconvert
sumolib
external InTAS checkout
```

Default external InTAS path:

```text
C:\Users\raffa\IdeaProjects\external\InTAS
```

## Folder structure

```text
tools/intas-literature-scenario/
ÃƒÂ¢Ã¢â‚¬ÂÃ…â€œÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ README.md
ÃƒÂ¢Ã¢â‚¬ÂÃ…â€œÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ build_intas_literature_scenario.py
ÃƒÂ¢Ã¢â‚¬ÂÃ…â€œÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ materialize_literature_scenario.ps1
ÃƒÂ¢Ã¢â‚¬ÂÃ…â€œÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ deploy_materialized_literature_scenario.ps1
ÃƒÂ¢Ã¢â‚¬ÂÃ…â€œÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ run_literature_scenario.ps1
ÃƒÂ¢Ã¢â‚¬ÂÃ…â€œÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ quick_literature_workflow.ps1
ÃƒÂ¢Ã¢â‚¬ÂÃ…â€œÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ show_latest_literature_report.ps1
|-- validate_intas_source.py
|-- validate_literature_configuration.py
|-- validate_materialized_literature_scenario.py
|-- validate_literature_smoke_run.ps1
`-- config/
    |-- candidate_0045_edge_ids.txt
    |-- literature_calibration_catalog.json
    |-- literature_scenario_targets.json
    |-- reproducibility_seeds.json
    `-- synthetic_mobility_profile.json
```

## Fast commands

Run the complete workflow, rebuilding the materialized scenario if needed:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\intas-literature-scenario\quick_literature_workflow.ps1 `
  -ForceRebuild `
  -PrintDetailedLiveReport `
  -PrintSummary
```

Materialize only:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\intas-literature-scenario\materialize_literature_scenario.ps1 `
  -Density nominal `
  -DurationProfile smoke `
  -Seed 104729 `
  -ForceRebuild
```

Run a previously materialized scenario:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\intas-literature-scenario\run_literature_scenario.ps1 `
  -MaterializedScenarioRoot .\tmp\materialized-literature-scenarios\MaGaLiteratureBasedUrbanStudy\nominal-smoke-seed-104729 `
  -PrintDetailedLiveReport
```

Show the most recent MOSAIC reports:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\intas-literature-scenario\show_latest_literature_report.ps1 `
  -PrintSummary
```

## What materialization does

The rare materialization step:

1. validates the external InTAS checkout and SUMO installation;
2. extracts the fixed `candidate_0045` subnetwork with `netconvert`;
3. checks `155` external edges, `88` junctions and `8` traffic lights;
4. computes valid shortest-path route templates on the reduced network;
5. groups templates into coverage families;
6. generates deterministic passenger traffic;
7. runs a SUMO validation pass;
8. rejects scenarios with invalid density, missing gateway switches, errors, teleports or emergency braking;
9. writes MOSAIC text configuration;
10. creates the SQLite database with Scenario-Convert;
11. imports the generated SUMO route file into the database;
12. writes a manifest and validates the materialized scenario.

## What frequent execution does

The run step does not rebuild the InTAS subnetwork. It:

1. builds the live MA-GA runtime JAR;
2. deploys the already materialized scenario into `tmp/mosaic-25.2/scenarios`;
3. starts `mosaic.bat`;
4. summarizes the run;
5. validates the literature smoke run;
6. writes live and detailed reports under the MOSAIC log directory.

## Configuration files

`config/synthetic_mobility_profile.json` contains the mobility design: fixed topology, RSU coordinates, route families, vehicle counts and acceptance thresholds.

`config/literature_calibration_catalog.json` contains radio, Cell, compute and workload assumptions.

`config/literature_scenario_targets.json` contains durations and high-level scenario targets.

`config/candidate_0045_edge_ids.txt` contains the selected InTAS road-edge identifiers. It is versioned so that regeneration is deterministic.

## Known non-blocking warning

The extracted InTAS network can emit:

```text
Warning: Unsafe green phase 0 in tlLogic ...
```

The warning originates from the source network and is recorded but not treated as a blocking error. SUMO errors, teleports and emergency-braking warnings remain blocking conditions.

## Scope boundary

This folder builds and executes the scenario. It does not modify the MA-GA core, its fitness function, genetic operators, temporal-window logic or snapshot model.
