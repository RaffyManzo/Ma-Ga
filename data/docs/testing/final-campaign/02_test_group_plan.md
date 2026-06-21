# Test Group Plan

## Group definitions

| Group | Name | Config_ID count | Run_ID count | Primary Test_ID count |
| --- | --- | --- | --- | --- |
| G00 | scenario preparation and generation | 2 | 2 | 2 |
| G01 | pipeline validation | 1 | 1 | 5 |
| G02 | main factorial experiments | 9 | 45 | 5 |
| G03 | reproducibility and duration | 4 | 11 | 3 |
| G04 | mobility and connectivity | 5 | 5 | 23 |
| G05 | resource policies and repair | 6 | 6 | 12 |
| G06 | runtime and temporal policies | 3 | 3 | 19 |
| G07 | final cross-group audit | 0 | 0 | 18 |

## Proposed campaign structure

The user-proposed data/mosaic-scenarios/final-test-campaign tree is suitable for future lightweight overlays or manifests, but it is not the best target for concrete generated scenarios in this repository. Existing documentation and scripts treat data/mosaic-scenarios/MaGaLiteratureBasedUrbanStudy as versioned template/scaffold, while concrete materializations live under tmp/materialized-literature-scenarios.

Recommended structure:

~~~text
data/docs/testing/final-campaign/
  README.md
  01_scenario_compatibility_audit.md
  02_test_group_plan.md
  scenario_configuration_mapping.csv
  scenario_instance_plan.csv
  test_id_group_mapping.csv
  audit_bundle_schema.md

tmp/materialized-literature-scenarios/final-test-campaign/
  G02_main_factorial_experiments/<CONFIG_ID>/<SEED>/
  G03_reproducibility_duration/<CONFIG_ID>/<SEED>/<replica>/
  G04_mobility_connectivity/<CONFIG_ID>/<SEED>/
  G05_resource_policies_repair/<CONFIG_ID>/<SEED>/
  G06_runtime_temporal_policies/<CONFIG_ID>/<SEED>/

test-results/final-campaign/<Gxx_name>/<Run_ID>/
test-audits/final-campaign/<Gxx_name>/
test-manifests/final-campaign/<Gxx_name>/~~~

This keeps generated scenario instances and raw run output out of the versioned scenario templates and gives each Config_ID + seed + duration + replica a collision-free directory.

## Config_ID to group - Stato iniziale prima dell'esecuzione di G00

| Config_ID | Group | Group name | Classification | Materializzare | Planned materializations |
| --- | --- | --- | --- | --- | --- |
| CFG-PRE-AUDIT | G00 | scenario preparation and generation | NON_MATERIALIZABLE | No | 0 |
| CFG-PRE-VALIDATE | G00 | scenario preparation and generation | NON_MATERIALIZABLE | No | 0 |
| CFG-SMOKE | G01 | pipeline validation | READY_EXISTING_TOOLING | Si | 1 |
| CFG-REPRO | G03 | reproducibility and duration | READY_EXISTING_TOOLING | Si | 2 |
| CFG-RUNTIME-REPEAT | G03 | reproducibility and duration | READY_EXISTING_TOOLING | Si | 1 |
| CFG-L-E | G02 | main factorial experiments | READY_CONFIG_ONLY | Si | 5 |
| CFG-L-I | G02 | main factorial experiments | READY_EXISTING_TOOLING | Si | 5 |
| CFG-L-S | G02 | main factorial experiments | READY_CONFIG_ONLY | Si | 5 |
| CFG-N-E | G02 | main factorial experiments | READY_CONFIG_ONLY | Si | 5 |
| CFG-N-I | G02 | main factorial experiments | READY_EXISTING_TOOLING | Si | 5 |
| CFG-N-S | G02 | main factorial experiments | READY_CONFIG_ONLY | Si | 5 |
| CFG-H-E | G02 | main factorial experiments | READY_CONFIG_ONLY | Si | 5 |
| CFG-H-I | G02 | main factorial experiments | READY_EXISTING_TOOLING | Si | 5 |
| CFG-H-S | G02 | main factorial experiments | READY_CONFIG_ONLY | Si | 5 |
| CFG-N-I-EXT | G03 | reproducibility and duration | READY_EXISTING_TOOLING | Si | 5 |
| CFG-H-S-EXT | G03 | reproducibility and duration | READY_CONFIG_ONLY | Si | 1 |
| CFG-M-BACKGROUND | G04 | mobility and connectivity | REQUIRES_DECISION | Si | 1 |
| CFG-M-RSU0 | G04 | mobility and connectivity | REQUIRES_DECISION | Si | 1 |
| CFG-M-RSU1 | G04 | mobility and connectivity | REQUIRES_DECISION | Si | 1 |
| CFG-M-SWITCH | G04 | mobility and connectivity | REQUIRES_DECISION | Si | 1 |
| CFG-M-V2V | G04 | mobility and connectivity | REQUIRES_DECISION | Si | 1 |
| CFG-R-LOCALCPU | G05 | resource policies and repair | READY_CONFIG_ONLY | Si | 1 |
| CFG-R-EDGECPU | G05 | resource policies and repair | READY_CONFIG_ONLY | Si | 1 |
| CFG-R-CLOUDCPU | G05 | resource policies and repair | READY_CONFIG_ONLY | Si | 1 |
| CFG-R-CELLBW | G05 | resource policies and repair | READY_CONFIG_ONLY | Si | 1 |
| CFG-R-V2VBW | G05 | resource policies and repair | REQUIRES_DECISION | Si | 1 |
| CFG-R-RTT | G05 | resource policies and repair | READY_CONFIG_ONLY | Si | 1 |
| CFG-G-SPARSE | G06 | runtime and temporal policies | READY_CONFIG_ONLY | Si | 1 |
| CFG-G-ADAPTIVE | G06 | runtime and temporal policies | NEEDS_TEST_TOOLING_EXTENSION | Si | 1 |
| CFG-G-STALE | G06 | runtime and temporal policies | READY_CONFIG_ONLY | Si | 1 |

## Run_ID to group

| Run_ID | Config_ID | Group | Operation | Mandatory | Materialization_ID |
| --- | --- | --- | --- | --- | --- |
| PRE-01-AUDIT | CFG-PRE-AUDIT | G00 | Audit Git | Si | - |
| PRE-02-CONFIG | CFG-PRE-VALIDATE | G00 | Validator configurazione | Si | - |
| PRE-03-SMOKE | CFG-SMOKE | G01 | Materialize+Deploy+Run | Si | MAT-SMOKE-104729 |
| REP-01-A | CFG-REPRO | G03 | Materializzazione | Si | MAT-REPRO-A |
| REP-01-B | CFG-REPRO | G03 | Materializzazione | Si | MAT-REPRO-B |
| VAR-N-01 | CFG-RUNTIME-REPEAT | G03 | Run | Consigliata | MAT-RUNTIME-104729 |
| VAR-N-02 | CFG-RUNTIME-REPEAT | G03 | Run | Consigliata | MAT-RUNTIME-104729 |
| VAR-N-03 | CFG-RUNTIME-REPEAT | G03 | Run | Consigliata | MAT-RUNTIME-104729 |
| L-E-01 | CFG-L-E | G02 | Materialize+Deploy+Run | Si | MAT-CFG-L-E-104729 |
| L-E-02 | CFG-L-E | G02 | Materialize+Deploy+Run | Si | MAT-CFG-L-E-130363 |
| L-E-03 | CFG-L-E | G02 | Materialize+Deploy+Run | Si | MAT-CFG-L-E-155921 |
| L-E-04 | CFG-L-E | G02 | Materialize+Deploy+Run | Si | MAT-CFG-L-E-181081 |
| L-E-05 | CFG-L-E | G02 | Materialize+Deploy+Run | Si | MAT-CFG-L-E-207547 |
| L-I-01 | CFG-L-I | G02 | Materialize+Deploy+Run | Si | MAT-CFG-L-I-104729 |
| L-I-02 | CFG-L-I | G02 | Materialize+Deploy+Run | Si | MAT-CFG-L-I-130363 |
| L-I-03 | CFG-L-I | G02 | Materialize+Deploy+Run | Si | MAT-CFG-L-I-155921 |
| L-I-04 | CFG-L-I | G02 | Materialize+Deploy+Run | Si | MAT-CFG-L-I-181081 |
| L-I-05 | CFG-L-I | G02 | Materialize+Deploy+Run | Si | MAT-CFG-L-I-207547 |
| L-S-01 | CFG-L-S | G02 | Materialize+Deploy+Run | Si | MAT-CFG-L-S-104729 |
| L-S-02 | CFG-L-S | G02 | Materialize+Deploy+Run | Si | MAT-CFG-L-S-130363 |
| L-S-03 | CFG-L-S | G02 | Materialize+Deploy+Run | Si | MAT-CFG-L-S-155921 |
| L-S-04 | CFG-L-S | G02 | Materialize+Deploy+Run | Si | MAT-CFG-L-S-181081 |
| L-S-05 | CFG-L-S | G02 | Materialize+Deploy+Run | Si | MAT-CFG-L-S-207547 |
| N-E-01 | CFG-N-E | G02 | Materialize+Deploy+Run | Si | MAT-CFG-N-E-104729 |
| N-E-02 | CFG-N-E | G02 | Materialize+Deploy+Run | Si | MAT-CFG-N-E-130363 |
| N-E-03 | CFG-N-E | G02 | Materialize+Deploy+Run | Si | MAT-CFG-N-E-155921 |
| N-E-04 | CFG-N-E | G02 | Materialize+Deploy+Run | Si | MAT-CFG-N-E-181081 |
| N-E-05 | CFG-N-E | G02 | Materialize+Deploy+Run | Si | MAT-CFG-N-E-207547 |
| N-I-01 | CFG-N-I | G02 | Materialize+Deploy+Run | Si | MAT-CFG-N-I-104729 |
| N-I-02 | CFG-N-I | G02 | Materialize+Deploy+Run | Si | MAT-CFG-N-I-130363 |
| N-I-03 | CFG-N-I | G02 | Materialize+Deploy+Run | Si | MAT-CFG-N-I-155921 |
| N-I-04 | CFG-N-I | G02 | Materialize+Deploy+Run | Si | MAT-CFG-N-I-181081 |
| N-I-05 | CFG-N-I | G02 | Materialize+Deploy+Run | Si | MAT-CFG-N-I-207547 |
| N-S-01 | CFG-N-S | G02 | Materialize+Deploy+Run | Si | MAT-CFG-N-S-104729 |
| N-S-02 | CFG-N-S | G02 | Materialize+Deploy+Run | Si | MAT-CFG-N-S-130363 |
| N-S-03 | CFG-N-S | G02 | Materialize+Deploy+Run | Si | MAT-CFG-N-S-155921 |
| N-S-04 | CFG-N-S | G02 | Materialize+Deploy+Run | Si | MAT-CFG-N-S-181081 |
| N-S-05 | CFG-N-S | G02 | Materialize+Deploy+Run | Si | MAT-CFG-N-S-207547 |
| H-E-01 | CFG-H-E | G02 | Materialize+Deploy+Run | Si | MAT-CFG-H-E-104729 |
| H-E-02 | CFG-H-E | G02 | Materialize+Deploy+Run | Si | MAT-CFG-H-E-130363 |
| H-E-03 | CFG-H-E | G02 | Materialize+Deploy+Run | Si | MAT-CFG-H-E-155921 |
| H-E-04 | CFG-H-E | G02 | Materialize+Deploy+Run | Si | MAT-CFG-H-E-181081 |
| H-E-05 | CFG-H-E | G02 | Materialize+Deploy+Run | Si | MAT-CFG-H-E-207547 |
| H-I-01 | CFG-H-I | G02 | Materialize+Deploy+Run | Si | MAT-CFG-H-I-104729 |
| H-I-02 | CFG-H-I | G02 | Materialize+Deploy+Run | Si | MAT-CFG-H-I-130363 |
| H-I-03 | CFG-H-I | G02 | Materialize+Deploy+Run | Si | MAT-CFG-H-I-155921 |
| H-I-04 | CFG-H-I | G02 | Materialize+Deploy+Run | Si | MAT-CFG-H-I-181081 |
| H-I-05 | CFG-H-I | G02 | Materialize+Deploy+Run | Si | MAT-CFG-H-I-207547 |
| H-S-01 | CFG-H-S | G02 | Materialize+Deploy+Run | Si | MAT-CFG-H-S-104729 |
| H-S-02 | CFG-H-S | G02 | Materialize+Deploy+Run | Si | MAT-CFG-H-S-130363 |
| H-S-03 | CFG-H-S | G02 | Materialize+Deploy+Run | Si | MAT-CFG-H-S-155921 |
| H-S-04 | CFG-H-S | G02 | Materialize+Deploy+Run | Si | MAT-CFG-H-S-181081 |
| H-S-05 | CFG-H-S | G02 | Materialize+Deploy+Run | Si | MAT-CFG-H-S-207547 |
| N-I-EXT-01 | CFG-N-I-EXT | G03 | Materialize+Deploy+Run | Si | MAT-CFG-N-I-EXT-104729 |
| N-I-EXT-02 | CFG-N-I-EXT | G03 | Materialize+Deploy+Run | Si | MAT-CFG-N-I-EXT-130363 |
| N-I-EXT-03 | CFG-N-I-EXT | G03 | Materialize+Deploy+Run | Si | MAT-CFG-N-I-EXT-155921 |
| N-I-EXT-04 | CFG-N-I-EXT | G03 | Materialize+Deploy+Run | Si | MAT-CFG-N-I-EXT-181081 |
| N-I-EXT-05 | CFG-N-I-EXT | G03 | Materialize+Deploy+Run | Si | MAT-CFG-N-I-EXT-207547 |
| H-S-EXT-01 | CFG-H-S-EXT | G03 | Materialize+Deploy+Run | No | MAT-CFG-H-S-EXT-104729 |
| M-BACKGROUND-01 | CFG-M-BACKGROUND | G04 | Materialize+Deploy+Run | Si | MAT-CFG-M-BACKGROUND-104729 |
| M-RSU0-01 | CFG-M-RSU0 | G04 | Materialize+Deploy+Run | Si | MAT-CFG-M-RSU0-104729 |
| M-RSU1-01 | CFG-M-RSU1 | G04 | Materialize+Deploy+Run | Si | MAT-CFG-M-RSU1-104729 |
| M-SWITCH-01 | CFG-M-SWITCH | G04 | Materialize+Deploy+Run | Si | MAT-CFG-M-SWITCH-104729 |
| M-V2V-01 | CFG-M-V2V | G04 | Materialize+Deploy+Run | Si | MAT-CFG-M-V2V-104729 |
| R-LOCALCPU-01 | CFG-R-LOCALCPU | G05 | Materialize+Deploy+Run | Si | MAT-CFG-R-LOCALCPU-104729 |
| R-EDGECPU-01 | CFG-R-EDGECPU | G05 | Materialize+Deploy+Run | Si | MAT-CFG-R-EDGECPU-104729 |
| R-CLOUDCPU-01 | CFG-R-CLOUDCPU | G05 | Materialize+Deploy+Run | Si | MAT-CFG-R-CLOUDCPU-104729 |
| R-CELLBW-01 | CFG-R-CELLBW | G05 | Materialize+Deploy+Run | Si | MAT-CFG-R-CELLBW-104729 |
| R-V2VBW-01 | CFG-R-V2VBW | G05 | Materialize+Deploy+Run | Si | MAT-CFG-R-V2VBW-104729 |
| R-RTT-01 | CFG-R-RTT | G05 | Materialize+Deploy+Run | Si | MAT-CFG-R-RTT-104729 |
| G-SPARSE-01 | CFG-G-SPARSE | G06 | Materialize+Deploy+Run | No | MAT-CFG-G-SPARSE-104729 |
| G-ADAPTIVE-01 | CFG-G-ADAPTIVE | G06 | Materialize+Deploy+Run | No | MAT-CFG-G-ADAPTIVE-104729 |
| G-STALE-01 | CFG-G-STALE | G06 | Materialize+Deploy+Run | Si | MAT-CFG-G-STALE-104729 |

## Cross-group Test_ID notes

The full mapping is in test_id_group_mapping.csv. The following Test_ID entries intentionally span multiple groups and should be indexed by the primary group while referencing evidence from all listed groups.

| Test_ID | Primary group | All groups | Workbook configurations | Notes |
| --- | --- | --- | --- | --- |
| T-001 | G00 | G00;G07 | CFG-PRE-AUDIT | cross-group coverage; primary group owns final evidence index |
| T-002 | G00 | G00;G01;G07 | CFG-SMOKE | cross-group coverage; primary group owns final evidence index |
| T-010 | G03 | G01;G03;G07 | CFG-SMOKE; CFG-REPRO | cross-group coverage; primary group owns final evidence index |
| T-011 | G01 | G01;G07 | CFG-SMOKE; tutte | cross-group coverage; primary group owns final evidence index |
| T-012 | G02 | G01;G02;G07 | CFG-SMOKE; MAIN | cross-group coverage; primary group owns final evidence index |
| T-013 | G02 | G01;G02;G07 | CFG-L-*; CFG-N-*; CFG-H-* | cross-group coverage; primary group owns final evidence index |
| T-014 | G03 | G01;G03;G07 | CFG-REPRO | cross-group coverage; primary group owns final evidence index |
| T-015 | G01 | G01;G07 | CFG-SMOKE; tutte | cross-group coverage; primary group owns final evidence index |
| T-016 | G07 | G01;G07 | tutte materializzabili | cross-group coverage; primary group owns final evidence index |
| T-017 | G01 | G01;G07 | CFG-SMOKE; tutte | cross-group coverage; primary group owns final evidence index |
| T-018 | G03 | G01;G02;G03;G07 | CFG-N-I-EXT; CFG-H-S-EXT | cross-group coverage; primary group owns final evidence index |
| T-020 | G01 | G01;G07 | CFG-SMOKE; tutte | cross-group coverage; primary group owns final evidence index |
| T-021 | G01 | G01;G07 | CFG-SMOKE | cross-group coverage; primary group owns final evidence index |
| T-034 | G06 | G06;G07 | CFG-G-SPARSE | cross-group coverage; primary group owns final evidence index |
| T-035 | G05 | G01;G05;G07 | CFG-SMOKE; CFG-R-CELLBW | cross-group coverage; primary group owns final evidence index |
| T-037 | G04 | G04;G07 | CFG-M-RSU0; CFG-M-RSU1; CFG-M-SWITCH | cross-group coverage; primary group owns final evidence index |
| T-038 | G04 | G04;G07 | CFG-M-BACKGROUND; CFG-M-RSU0; CFG-M-RSU1 | cross-group coverage; primary group owns final evidence index |
| T-039 | G07 | G04;G07 | tutte run | cross-group coverage; primary group owns final evidence index |
| T-040 | G04 | G04;G05;G07 | CFG-M-V2V; CFG-R-V2VBW | cross-group coverage; primary group owns final evidence index |
| T-041 | G04 | G04;G07 | CFG-M-RSU0; CFG-M-RSU1 | cross-group coverage; primary group owns final evidence index |
| T-042 | G04 | G04;G07 | CFG-M-RSU0; CFG-M-RSU1; CFG-M-SWITCH | cross-group coverage; primary group owns final evidence index |
| T-050 | G06 | G06;G07 | tutte run | cross-group coverage; primary group owns final evidence index |
| T-051 | G06 | G06;G07 | tutte run | cross-group coverage; primary group owns final evidence index |
| T-052 | G06 | G02;G06;G07 | CFG-G-STALE; CFG-H-* | cross-group coverage; primary group owns final evidence index |
| T-053 | G06 | G06;G07 | CFG-G-STALE | cross-group coverage; primary group owns final evidence index |
| T-054 | G06 | G06;G07 | CFG-G-STALE | cross-group coverage; primary group owns final evidence index |
| T-055 | G06 | G06;G07 | tutte run | cross-group coverage; primary group owns final evidence index |
| T-056 | G04 | G02;G04;G05;G06 | MAIN; MOB; RES | cross-group coverage; primary group owns final evidence index |
| T-060 | G06 | G06;G07 | CFG-G-SPARSE | cross-group coverage; primary group owns final evidence index |
| T-061 | G06 | G06;G07 | tutte run | cross-group coverage; primary group owns final evidence index |
| T-062 | G06 | G06;G07 | tutte run | cross-group coverage; primary group owns final evidence index |
| T-063 | G06 | G02;G06 | MAIN | cross-group coverage; primary group owns final evidence index |
| T-064 | G06 | G02;G06;G07 | CFG-H-S; CFG-G-ADAPTIVE | cross-group coverage; primary group owns final evidence index |
| T-065 | G04 | G02;G04;G05;G07 | CFG-M-BACKGROUND; MAIN | cross-group coverage; primary group owns final evidence index |
| T-066 | G05 | G02;G05 | MAIN; RES | cross-group coverage; primary group owns final evidence index |
| T-067 | G05 | G02;G05;G07 | MAIN; CFG-R-LOCALCPU | cross-group coverage; primary group owns final evidence index |
| T-068 | G05 | G02;G05 | MAIN; RES | cross-group coverage; primary group owns final evidence index |
| T-069 | G05 | G02;G05 | MAIN; RES | cross-group coverage; primary group owns final evidence index |
| T-071 | G05 | G05;G07 | CFG-R-EDGECPU; CFG-R-CLOUDCPU | cross-group coverage; primary group owns final evidence index |
| T-072 | G05 | G04;G05;G07 | CFG-R-CELLBW; CFG-R-V2VBW | cross-group coverage; primary group owns final evidence index |
| T-073 | G05 | G04;G05;G07 | CFG-R-CELLBW; CFG-R-V2VBW | cross-group coverage; primary group owns final evidence index |
| T-074 | G04 | G04;G05;G07 | CFG-M-BACKGROUND; RES | cross-group coverage; primary group owns final evidence index |
| T-075 | G02 | G02;G05;G07 | CFG-L-S; CFG-N-S; CFG-H-S | cross-group coverage; primary group owns final evidence index |
| T-076 | G04 | G04;G05;G06 | MOB; RES; SPARSE | cross-group coverage; primary group owns final evidence index |
| T-077 | G05 | G02;G05 | RES; MAIN | cross-group coverage; primary group owns final evidence index |
| T-078 | G07 | G05;G07 | tutte run | cross-group coverage; primary group owns final evidence index |
| T-079 | G04 | G02;G04 | MAIN; MOB | cross-group coverage; primary group owns final evidence index |
| T-080 | G04 | G02;G04;G05 | MAIN; RES; MOB | cross-group coverage; primary group owns final evidence index |
| T-090 | G04 | G04;G07 | CFG-M-BACKGROUND | cross-group coverage; primary group owns final evidence index |
| T-091 | G04 | G04;G07 | CFG-M-RSU0; CFG-M-RSU1 | cross-group coverage; primary group owns final evidence index |
| T-092 | G04 | G04;G07 | CFG-M-RSU0; CFG-M-SWITCH | cross-group coverage; primary group owns final evidence index |
| T-093 | G04 | G04;G07 | CFG-M-V2V | cross-group coverage; primary group owns final evidence index |
| T-094 | G04 | G04;G05;G07 | CFG-M-SWITCH; CFG-R-RTT; CFG-R-CELLBW | cross-group coverage; primary group owns final evidence index |
| T-096 | G04 | G04;G07 | CFG-M-SWITCH | cross-group coverage; primary group owns final evidence index |
| T-097 | G04 | G02;G04 | MOB; MAIN | cross-group coverage; primary group owns final evidence index |
| T-098 | G04 | G04;G07 | CFG-M-SWITCH | cross-group coverage; primary group owns final evidence index |
| T-099 | G07 | G04;G07 | tutte run | cross-group coverage; primary group owns final evidence index |
| T-110 | G04 | G02;G04;G05;G06 | MAIN; MOB; RES | cross-group coverage; primary group owns final evidence index |
| T-111 | G04 | G02;G04;G05;G06 | MAIN; MOB; RES | cross-group coverage; primary group owns final evidence index |
| T-112 | G04 | G02;G04;G05;G06 | MAIN; MOB; RES | cross-group coverage; primary group owns final evidence index |
| T-113 | G05 | G02;G05;G06 | MAIN; stress | cross-group coverage; primary group owns final evidence index |
| T-114 | G05 | G02;G05;G06 | MAIN; stress | cross-group coverage; primary group owns final evidence index |
| T-115 | G06 | G06;G07 | tutte run | cross-group coverage; primary group owns final evidence index |
| T-116 | G06 | G02;G06;G07 | CFG-G-STALE; CFG-H-S | cross-group coverage; primary group owns final evidence index |
| T-117 | G06 | G06;G07 | tutte run | cross-group coverage; primary group owns final evidence index |
| T-118 | G06 | G06;G07 | - | cross-group coverage; primary group owns final evidence index |
| T-119 | G06 | G06;G07 | tutte run | cross-group coverage; primary group owns final evidence index |
| T-120 | G06 | G02;G06 | MAIN | cross-group coverage; primary group owns final evidence index |
| T-121 | G06 | G06;G07 | CFG-G-ADAPTIVE | cross-group coverage; primary group owns final evidence index |

## Materialization rules

- Config_ID is the logical experimental definition.
- Materialization_ID is the concrete scenario instance obtained from Config_ID + seed + duration + materialization-affecting parameters.
- Run_ID is one MOSAIC execution of a Materialization_ID.
- The nine main configurations CFG-L-E through CFG-H-S require five materializations each, one for seeds 104729, 130363, 155921, 181081, 207547.
- CFG-RUNTIME-REPEAT uses one materialization and three MOSAIC executions.
- CFG-REPRO uses two independent materializations with the same parameters and seed for hash comparison.

## G00 Execution Result

Stato iniziale prima dell'esecuzione di G00: documentazione e piano di campagna predisposti, con tooling finale ancora da creare e scenari non ancora materializzati.

Stato corrente post-G00/G00F: `COMPLETED`.

- planned materializations: `69`
- completed materializations: `69`
- validated materializations: `63`
- warning materializations: `6`
- failed materializations: `0`
- blocked materializations: `0`
- NON_MATERIALIZABLE: `2`
- READY_CONFIG_ONLY: `20`
- READY_EXISTING_TOOLING: `8`
- REQUIRES_DECISION: `0`
- NEEDS_TEST_TOOLING_EXTENSION: `0`

Tooling created under `tools/intas-literature-scenario/final_campaign/`. Concrete scenarios remain under `tmp/materialized-literature-scenarios/final-test-campaign/`; MOSAIC was not executed. G00 is technically complete and the audit bundle was normalized in G00F.
