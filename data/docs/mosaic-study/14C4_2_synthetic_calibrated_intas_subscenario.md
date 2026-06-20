# Phase 14C.4.2 - Synthetic-calibrated mobility on a real InTAS subnetwork

## Goal

This phase defines the small, reproducible and inspectable MOSAIC/SUMO
scenario used by the final live MA-GA runtime.

The scenario must remain realistic enough to exercise local, V2V, edge and
cloud choices. At the same time, it must be easy to regenerate and inspect.

## Final architectural choice

The final mobility mode is:

```text
SYNTHETIC_CALIBRATED_ON_INTAS_SUBNETWORK
```

This means:

- the road topology is extracted from the real InTAS scenario for Ingolstadt;
- vehicle traffic is generated synthetically in a deterministic way;
- SUMO still simulates vehicle mobility live during MOSAIC execution;
- MA-GA still receives live snapshots from MOSAIC/SUMO;
- the MOSAIC SQLite database is still created through Scenario-Convert.

## Why exact replay was abandoned

The previous attempt tried to reproduce the exact InTAS interval
`14250-14430 s` as a compact `0-180 s` scenario.

The experiment identified several difficulties:

- vehicles were already active at the beginning of the interval;
- other vehicles were waiting for insertion;
- route times required careful shifting;
- SUMO save-state loading required `--xml-validation never` because of a
  `tlLogic active` schema inconsistency;
- corrected offset experiments still did not preserve equivalent traffic over
  the full interval.

Continuing along that path would have increased implementation complexity
without improving the validation of MA-GA itself.

## Reused evidence from InTAS

The final scenario keeps the useful results of the manual study:

```text
selected subnetwork: candidate_0045
external edges: 155
external junctions: 88
traffic lights: 8

reference interval: 14250-14430 s
reference mean active vehicles: 29.52
reference minimum active vehicles: 26
reference maximum active vehicles: 34
```

The RSUs were selected from real trajectories:

```text
rsu_0: x=213980.44, y=450429.42
rsu_1: x=213897.79, y=450744.66
radius: 250 m
```

## Synthetic route families

Routes are computed on the reduced network and grouped into five families:

```text
DUAL_RSU_SWITCH
BOTH_RSU_NO_SWITCH
RSU_0_ONLY
RSU_1_ONLY
BACKGROUND
```

The nominal baseline generates:

```text
DUAL_RSU_SWITCH: 21 vehicles
BOTH_RSU_NO_SWITCH: 7 vehicles
RSU_0_ONLY: 7 vehicles
RSU_1_ONLY: 5 vehicles
BACKGROUND: 10 vehicles
TOTAL: 50 vehicles
```

The seed is fixed to `104729` and requested departures are separated by
`1.80 s`.

## Validated SUMO baseline

The manually validated nominal synthetic baseline produced:

```text
simulation duration: 180 s
mean active vehicles: 31.86
minimum active vehicles: 1
maximum active vehicles: 46
vehicles visiting both RSUs: 20
vehicles with gateway switch: 17
gateway-switch events: 17
SUMO errors: 0
teleports: 0
emergency braking: 0
```

The density is intentionally close to, but not identical to, the observed InTAS
reference interval.

## Materialization flow

The materializer performs these steps:

```text
external InTAS network
-> fixed candidate_0045 edge list
-> netconvert reduced network
-> route-template generation
-> deterministic synthetic demand
-> SUMO validation
-> MOSAIC text configuration
-> Scenario-Convert SQLite database
-> route import
-> persistent manifest
```

## Local folders that must remain available

The following folders are local dependencies and must not be deleted:

```text
tmp/mosaic-25.2
tmp/external-tools/scenario-convert-25.2
```

The first contains the MOSAIC runtime. The second contains Scenario-Convert
used during materialization.

## Scope boundary

The phase does not alter the MA-GA core. It changes only scenario generation,
validation and documentation.
