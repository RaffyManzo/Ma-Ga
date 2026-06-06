# Phase 14C.2 - Literature-Based Radio, Cell, Gateway and Compute Configuration

## Objective

Phase 14C.2 prepares the text configuration layer for the future
`MaGaLiteratureBasedUrbanStudy` calibration scenario. It extends the InTAS
materializer so the selected urban window can produce coherent MOSAIC
configuration files under `tmp/`, without committing derived SUMO assets,
databases, logs, JARs or Python caches.

This phase does not modify the MA-GA Java core, does not execute MOSAIC
end-to-end and does not introduce the final Poisson workload generator or the
40-replicate calibration runner.

## Inputs

The external InTAS checkout used for the real dry-run was:

```text
C:\Users\raffa\IdeaProjects\external\InTAS
```

The InTAS commit reported by the source validator was:

```text
0f7951ba01dda8483f0a852f2c3e4ff0d8a1c0ee
```

The selected candidate remains:

```text
candidate_0045
```

with the Cartesian extraction window:

```text
minX = 213734.86
minY = 449911.51
maxX = 214634.86
maxY = 450811.51
```

## Projection

`pyproj` is now installed in the local Python environment and the dry-run uses
SUMO/sumolib coordinate conversion instead of the preliminary fallback.

```text
projection.method = SUMOLIB_CONVERT_XY2LONLAT
projection.valid = true
projection.fallback = null
projection.plausibleIngolstadtCoordinate = true
```

The generated scenario center is:

```text
latitude = 48.756083988143494
longitude = 11.427116263918785
cartesianOffset.x = -464198.88
cartesianOffset.y = -4952821.58
```

The generated Cell region covers the selected InTAS window with:

```text
NW = (lat 48.760257309679446, lon 11.421193697867638)
SE = (lat 48.75191037521218, lon 11.433037849634832)
```

## RSU and Gateway Coordinates

The two RSUs are derived from the selected candidate and converted with
`SUMOLIB_CONVERT_XY2LONLAT`.

```text
rsu_0:
  projected = (214429.45, 450749.91)
  latitude = 48.75950477431788
  longitude = 11.430609582198231

rsu_1:
  projected = (214047.75, 450634.54)
  latitude = 48.758577222800135
  longitude = 11.425370673756245

rsu distance = 398.7544694420463 m
coverage radius = 250 m
```

Each RSU is materialized as a gateway in the live-state configuration:

```text
rsu_0 -> pool_rsu_0 -> edge_rsu_0
rsu_1 -> pool_rsu_1 -> edge_rsu_1
```

Both gateways bind to the same nominal Cell region:

```text
region_cell_5g_aveiro_p50
```

## Versioned Files

Phase 14C.2 adds the literature calibration catalog:

```text
tools/intas-literature-scenario/config/literature_calibration_catalog.json
```

and versioned templates for the future scenario:

```text
data/mosaic-scenarios/MaGaLiteratureBasedUrbanStudy/sns/sns_config.template.json
data/mosaic-scenarios/MaGaLiteratureBasedUrbanStudy/cell/cell_config.template.json
data/mosaic-scenarios/MaGaLiteratureBasedUrbanStudy/cell/network.template.json
data/mosaic-scenarios/MaGaLiteratureBasedUrbanStudy/cell/regions.template.json
data/mosaic-scenarios/MaGaLiteratureBasedUrbanStudy/application/application_config.template.json
data/mosaic-scenarios/MaGaLiteratureBasedUrbanStudy/application/ma_ga_live_state_config.template.json
data/mosaic-scenarios/MaGaLiteratureBasedUrbanStudy/application/ma_ga_live_runtime_config.template.json
```

These templates intentionally contain explicit tokens such as
`${RSU_0_LONGITUDE}` and `${CELL_REGION_NW_LATITUDE}`. Concrete files are
generated only under `tmp/`.

## Generated Output Under tmp

The required dry-run command generated:

```text
tmp/intas-literature-config-dryrun/MaGaLiteratureBasedUrbanStudy/
```

with concrete text configurations:

```text
scenario_config.json
sumo/intas_literature_urban.net.xml
sumo/intas_literature_urban_low_density.rou.xml
sumo/intas_literature_urban_nominal.rou.xml
sumo/intas_literature_urban_high_density.rou.xml
sumo/intas_literature_urban.sumocfg
sumo/sumo_config.json
mapping/mapping_config.json
sns/sns_config.json
cell/cell_config.json
cell/network.json
cell/regions.json
application/application_config.json
application/ma_ga_calibration_metadata.json
application/ma_ga_live_state_config.json
application/ma_ga_live_runtime_config.json
reports/intas_literature_materialization_report.json
reports/intas_literature_materialization_report.md
reports/literature_configuration_validation.json
```

No MOSAIC database was generated because Scenario-Convert is not available.

## Route Profiles

The dry-run preserves the InTAS route topology and samples route subsets
deterministically from real routes. It does not select a fixed number of XML
rows; it measures active vehicles over time.

```text
low_density:
  target mean = 15
  observed mean = 15.498338870431894
  max active = 18
  vehicles = 21
  seed = 104729

nominal:
  target mean = 30
  observed mean = 30.292358803986712
  max active = 36
  vehicles = 49
  seed = 130363

high_density:
  target mean = 50
  observed mean = 49.647840531561464
  max active = 55
  vehicles = 76
  seed = 155921
```

The selected routes use the preserved SUMO vehicle types:

```text
default_001
random_001
```

The generated mapping uses one MOSAIC prototype per used SUMO `vType`, with
`weight = 1.0`, and does not recreate imported SUMO vehicles in the `vehicles`
section.

## V2V Radio

The nominal SNS configuration is:

```json
{
  "maximumTtl": 10,
  "singlehopRadius": 250,
  "adhocTransmissionModel": {
    "type": "SophisticatedAdhocTransmissionModel"
  },
  "singlehopDelay": {
    "type": "SimpleRandomDelay",
    "steps": 10,
    "minDelay": "1 ms",
    "maxDelay": "10 ms"
  },
  "singleHopTransmission": {
    "lossProbability": 0.0,
    "maxRetries": 0
  }
}
```

The MA-GA live-state abstraction uses the same V2V radius:

```text
singlehopRadiusMeters = 250
v2vNominalBandwidthBitsPerSecond = 4700000
v2vPropagationDelaySeconds = 0.002
v2vBandwidthSource = LITERATURE_BASED_CALIBRATED_ABSTRACTION
```

The metadata classifies the SNS/V2V profile as
`CALIBRATED_ABSTRACTION`. Carrier frequency, channel bandwidth, transmit
power, CAM payload and PRR remain `DOCUMENTATION_ONLY`.

## Cell 5G Nominal Profile

The nominal scenario uses only:

```text
CELL_5G_AVEIRO_P50
```

It creates one Cell region:

```text
region_cell_5g_aveiro_p50
```

with:

```text
uplink capacity = 49.2 Mbps
downlink capacity = 49.2 Mbps
uplink delay = 26.1 ms
downlink unicast delay = 26.1 ms
downlink multicast delay = 26.1 ms
lossProbability = 0.0
maxRetries = 0
```

The server backhaul delay is configured as:

```text
server_0 uplink = 50 ms
server_0 downlink = 50 ms
classification = CONTROLLED_ASSUMPTION
```

LTE and degraded 5G remain separate profiles in the catalog and are not mixed
into the nominal scenario.

## Cell Diagnostic Accounting Boundary

The current Java live-state layer still uses:

```text
DIAGNOSTIC_RUNTIME_ACCOUNTING_FROM_CONTROLLED_CELL_MESSAGES
```

Phase 14C.2 keeps a compatible `cellDiagnosticAccounting` section but marks the
relationship explicitly:

```text
configured Cell profile = LITERATURE_BASED_CONFIGURED_CELL_PROFILE
runtime accounting = DIAGNOSTIC_RUNTIME_ACCOUNTING_FROM_CONTROLLED_CELL_MESSAGES
relationship = distinct concepts
```

The full source-mode separation remains scheduled for Phase 14C.3.

## Compute Profiles

The catalog and generated live-state configuration use:

```text
LOCAL CPU = 1,000,000,000 cycles/s
EDGE CPU = 5,000,000,000 cycles/s
CLOUD CPU = 100,000,000,000 cycles/s
```

The target remote vehicle CPU is recorded in the catalog as:

```text
remoteVehicleCpuCyclesPerSecondTarget = 1,000,000,000 cycles/s
classification = PENDING_LIVE_STATE_EXTENSION
```

It is not injected into the executable live-state configuration yet because
the current live-state Java layer has no separate remote-vehicle CPU field.

## Temporary Bootstrap Task

The generated live-state configuration includes exactly one compatibility task:

```text
profileId = bootstrap_medium_until_14C3
activationTimeMs = 7000
sourceVehicleId = veh_0
inputSizeBits = 800000
outputSizeBits = 8000
cpuCycles = 600000000
deadlineSeconds = 1.0
metadataMarker = TEMPORARY_COMPATIBILITY_TASK_NOT_FINAL_WORKLOAD
```

This is not the final workload. The definitive workload generator remains the
boundary of Phase 14C.3.

## Validator

The new validator is:

```text
tools/intas-literature-scenario/validate_literature_configuration.py
```

It checks JSON validity, unresolved template tokens, projection method, RSU and
gateway consistency, radius consistency, Cell profile separation, compute
values, temporary workload marking, absence of fake DB files, and absence of
JAR/PYC files.

The generated validation report returned:

```text
status = VALID_TEXTUAL_CONFIGURATION
projectionMethod = SUMOLIB_CONVERT_XY2LONLAT
projectionFallback = null
rsuCount = 2
gatewayCount = 2
snsSinglehopRadius = 250
liveStateSinglehopRadiusMeters = 250
dbFiles = []
jarFiles = []
pycFiles = []
errors = []
```

## Scenario-Convert

Scenario-Convert was searched but not found:

```text
available = false
path = null
requiredAction = Install/configure MOSAIC Extended Scenario-Convert and pass --scenario-convert or SCENARIO_CONVERT.
```

Therefore:

```text
status = PARTIAL_EXTERNAL_TOOL_REQUIRED
dryRunStatus = DRY_RUN_COMPLETED
textualConfigurationStatus = GENERATED_AND_VALIDATED
```

No `.db` file was invented or copied.

## Commands

Source validation:

```powershell
py -3 -B tools\intas-literature-scenario\validate_intas_source.py `
  --intas-root C:\Users\raffa\IdeaProjects\external\InTAS
```

Dry-run generation:

```powershell
py -3 -B tools\intas-literature-scenario\build_intas_literature_scenario.py `
  --intas-root C:\Users\raffa\IdeaProjects\external\InTAS `
  --output-root C:\Users\raffa\IdeaProjects\maga-core\tmp\intas-literature-config-dryrun `
  --density all `
  --duration-profile nominal `
  --dry-run
```

Configuration validation:

```powershell
py -3 -B tools\intas-literature-scenario\validate_literature_configuration.py `
  --scenario-root C:\Users\raffa\IdeaProjects\maga-core\tmp\intas-literature-config-dryrun\MaGaLiteratureBasedUrbanStudy
```

## Limits and Next Phase

The following remain intentionally open:

- no Scenario-Convert database yet;
- no final task workload generator;
- no separate remote VEHICLE CPU support in the live-state Java layer;
- no 40-replicate runner;
- no MOSAIC end-to-end execution for this literature scenario;
- no scientific calibration or tuning.

Phase 14C.3 should implement the workload generator boundary, complete the
live-state source-mode separation for configured Cell versus diagnostic
accounting, and prepare the replicate matrix without changing the MA-GA core.
