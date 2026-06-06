# 14C.1 InTAS Literature Scenario Scaffold

## Objective

Phase 14C.1 prepares a versionable scaffold and deterministic materializer for
`MaGaLiteratureBasedUrbanStudy`. The scenario will be derived from the external
InTAS SUMO scenario and later used for calibration.

This phase does not modify the MA-GA core, does not change the diagnostic live
runtime scenario and does not start scientific calibration.

## Sources

- InTAS repository: https://github.com/silaslobo/InTAS
- MOSAIC tutorial for integrating existing SUMO scenarios:
  https://eclipse.dev/mosaic/tutorials/integrate_existing_sumo_scenarios/
- MOSAIC SUMO documentation:
  https://eclipse.dev/mosaic/docs/simulators/traffic_simulator_sumo/

The InTAS README describes the Ingolstadt SUMO scenario, real traffic
validation, 24-hour simulation, real traffic-light locations, buildings and bus
routes. The MOSAIC tutorial documents the `scenario-convert.sh --sumo2db`
workflow and the required `application`, `mapping`, `sumo` and
`scenario_config.json` structure for coupling SUMO scenarios with MOSAIC.

## Approved Constants

```text
scenarioName = MaGaLiteratureBasedUrbanStudy
mobilitySource = InTAS
sumoStepLengthSeconds = 0.1
smokeDurationSeconds = 180
nominalDurationSeconds = 300
extendedDurationSeconds = 600
activeVehicleTargets = 15, 30, 50
nominalRsuCount = 2
extendedRsuCount = 3
nominalRsuRadiusMeters = 250
nominalV2vRadiusMeters = 250
```

## Versioned Scaffold

Created files:

```text
tools/intas-literature-scenario/
data/mosaic-scenarios/MaGaLiteratureBasedUrbanStudy/
```

The scenario directory contains templates only. It deliberately does not
contain generated `.net.xml`, `.rou.xml` or `.db` files.

## Materializer

`build_intas_literature_scenario.py` accepts:

```text
--intas-root <path>
--output-root <path>
--scenario-convert <path> optional
```

It validates the external InTAS checkout, reads the SUMO network and route
files referenced by `InTAS_buildings.sumocfg`, evaluates deterministic urban
candidates, selects the best candidate and builds route subsets for
`low_density`, `nominal` and `high_density`.

If Scenario-Convert is unavailable, it does not invent a database and reports
`PARTIAL_EXTERNAL_TOOL_REQUIRED`.

## Candidate Metrics

The report records:

```text
candidateId
geographicBoundary
cartesianBoundary
areaKm2
drivableEdgeCount
junctionCount
trafficLightCount
connectedComponentCount
largestConnectedComponentShare
routeCountBeforeFiltering
routeCountAfterFiltering
activeVehicleCountBySecond
meanActiveVehicleCount
maxActiveVehicleCount
vehicleCountTargetError
speedSummary
candidateRsuPositions
rsuPairDistanceMeters
rsuCoverageOverlapEstimated
gatewaySwitchPotential
selectionScore
rejectionReasons
```

No extraction rectangle or RSU coordinate is hardcoded in the scaffold.

## Metadata

`ma_ga_calibration_metadata.template.json` prepares the distinction between:

```text
MODELLED_DIRECTLY
CALIBRATED_ABSTRACTION
DOCUMENTATION_ONLY
CONTROLLED_ASSUMPTION
```

InTAS mobility is classified as `MODELLED_DIRECTLY`. The 250 m V2V radius is a
`CALIBRATED_ABSTRACTION`. ITS-G5/IEEE 802.11p descriptors such as 5.9 GHz,
10 MHz, 23 dBm, CAM 300 byte and PRR >= 90% are `DOCUMENTATION_ONLY` until the
calibration phase defines how they map to runtime parameters.

## Validation Status

No local InTAS checkout was found in the working tree during this phase, so no
complete materialization was executed. The tool help and JSON/template
validations are executable without InTAS. Full validation requires:

```text
python tools/intas-literature-scenario/validate_intas_source.py --intas-root <path>
python tools/intas-literature-scenario/build_intas_literature_scenario.py --intas-root <path> --output-root <path>
```

## Next Phase

Subsequent 14C subphases can extend workload generation, VEHICLE CPU
calibration, Cell catalog definition and the calibrated replicate runner after
this scaffold is reviewed and committed.
