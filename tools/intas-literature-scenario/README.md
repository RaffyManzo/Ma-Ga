# InTAS Literature Scenario Tool

This tool prepares the literature-based MOSAIC scenario
`MaGaLiteratureBasedUrbanStudy` from an external InTAS checkout.

It does not vendor InTAS into this repository. Generated SUMO networks, route
subsets and MOSAIC databases must be written to an explicit output directory
and reviewed before any generated asset is committed.

## External Source

- Repository: https://github.com/silaslobo/InTAS
- Expected source root: pass with `--intas-root <path>`
- Required files:
  - `scenario/ingolstadt.net.xml`
  - `scenario/InTAS_buildings.sumocfg`
  - `scenario/routes/`
- License: detected from the external checkout; InTAS upstream currently
  declares GPL-3.0. Redistribution of generated derivatives must be checked
  before pushing assets.

## Dependencies

- Python 3.10+.
- SUMO with `SUMO_HOME` configured.
- `sumo` and `netconvert` available on `PATH`.
- `pyproj` installed in the Python environment for concrete configuration
  generation. The materializer rejects fallback projection for concrete files.
- Optional MOSAIC Scenario-Convert, provided through one of:
  - `--scenario-convert <path>`
  - environment variable `SCENARIO_CONVERT`
  - `scenario-convert.sh`, `scenario-convert.bat`, or `scenario-convert` on `PATH`

If Scenario-Convert is not available, the materializer does not invent a
database and does not copy a database from another scenario. It writes reports
with status `PARTIAL_EXTERNAL_TOOL_REQUIRED`.

## Validate InTAS

```powershell
python tools/intas-literature-scenario/validate_intas_source.py `
  --intas-root <path-to-external-InTAS>
```

The validator checks required files, route-file references, `SUMO_HOME`, SUMO
executables, license metadata and Git commit/tag when available.

## Materialize or Dry-Run

```powershell
python tools/intas-literature-scenario/build_intas_literature_scenario.py `
  --intas-root <path-to-external-InTAS> `
  --output-root <generated-output-root> `
  --density all `
  --duration-profile nominal `
  --dry-run
```

Useful options:

```powershell
python tools/intas-literature-scenario/build_intas_literature_scenario.py --help
```

The output root receives:

```text
MaGaLiteratureBasedUrbanStudy/
|-- application/
|   |-- intas_literature_urban.db                # only if scenario-convert succeeds
|   |-- application_config.json
|   |-- ma_ga_calibration_metadata.json
|   |-- ma_ga_live_state_config.json
|   `-- ma_ga_live_runtime_config.json
|-- cell/
|   |-- cell_config.json
|   |-- network.json
|   `-- regions.json
|-- mapping/
|   `-- mapping_config.json
|-- sns/
|   `-- sns_config.json
|-- sumo/
|   |-- intas_literature_urban.net.xml
|   |-- intas_literature_urban_<density>.rou.xml
|   |-- intas_literature_urban.sumocfg
|   `-- sumo_config.json
|-- scenario_config.json
`-- reports/
    |-- intas_literature_materialization_report.json
    |-- intas_literature_materialization_report.md
    `-- literature_configuration_validation.json
```

## Literature Configuration Catalog

Phase 14C.2 adds:

```text
tools/intas-literature-scenario/config/literature_calibration_catalog.json
```

The catalog keeps V2V, Cell, compute and infrastructure assumptions separate
from generated scenario files. It distinguishes `MODELLED_DIRECTLY`,
`CALIBRATED_ABSTRACTION`, `DOCUMENTATION_ONLY`, `CONTROLLED_ASSUMPTION` and
`IMPLEMENTED_CONFIGURABLE_LITERATURE_BASED_PROFILE` metadata classes.

Phase 14C.3 adds executable text configuration for:

- `remoteVehicleCpuCyclesPerSecond` separate from local CPU;
- `workloadGeneration.mode = SEEDED_POISSON_PER_ACTIVE_VEHICLE`;
- light, medium and heavy task profiles;
- `configuredCellProfile` distinct from `cellDiagnosticAccounting`.

## Validate Generated Text Configuration

After a dry-run or full materialization, validate the generated text
configuration with:

```powershell
python tools/intas-literature-scenario/validate_literature_configuration.py `
  --scenario-root <generated-output-root>\MaGaLiteratureBasedUrbanStudy
```

Expected result for a valid dry-run without Scenario-Convert:

```text
status = VALID_TEXTUAL_CONFIGURATION
dbFiles = []
jarFiles = []
pycFiles = []
```

## Deterministic Selection

The materializer:

1. Reads the official InTAS SUMO network and route files referenced by
   `InTAS_buildings.sumocfg`.
2. Computes fixed-size urban candidate windows.
3. Scores candidates by traffic-light count, connectivity, active-vehicle
   target error, partial RSU overlap and potential gateway switching.
4. Selects the highest-scoring candidate deterministically.
5. Creates low, nominal and high-density route subsets using configured seeds.
6. Generates MOSAIC text configuration for mapping, SNS, Cell, live-state and
   live-runtime setup.

No RSU coordinate, extraction rectangle or route subset is hardcoded in the
repository scaffold. They are derived from the external InTAS assets.

## Current Phase Boundary

Phase 14C.2 prepares literature-based radio, Cell, RSU/gateway and compute
configuration. Phase 14C.3 adds the deterministic workload and remote VEHICLE
CPU configuration. It does not implement:

- 40-replicate runner;
- MOSAIC end-to-end execution for the literature scenario;
- scientific calibration.
