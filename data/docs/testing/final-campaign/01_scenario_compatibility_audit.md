# Scenario Compatibility Audit

## Freeze and workbook validation

- Branch di partenza verificato: MOSAIC/SUMO-integration
- Commit congelato verificato: 5a9477735a3d707a5f000a64653cd2a6fc7f2007
- Branch di lavoro preparato: testing/final-campaign
- Workbook analizzato: C:/Users/raffa/Downloads/Matrice_test_MA_GA_MOSAIC_SUMO_Fase0_completa.xlsx
- Stato iniziale prima dell'esecuzione di G00: pianificazione/audit soltanto; nessuno scenario generato, nessuna simulazione eseguita, nessun codice modificato.

| Oggetto | Atteso | Reale | Esito |
| --- | --- | --- | --- |
| Configurazioni totali | 30 | 30 | OK |
| Configurazioni materializzabili | 28 | 28 | OK |
| Operazioni/run pianificati | 73 | 73 | OK |
| Operazioni obbligatorie | 67 | 67 | OK |
| Test funzionali censiti | 87 | 87 | OK |

## Tooling inventory

| Area | File/cartella | Osservazione |
| --- | --- | --- |
| Synthetic InTAS materializer | tools/intas-literature-scenario/build_intas_literature_scenario.py; materialize_literature_scenario.ps1 | Generates reduced InTAS/SUMO scenario, route XML, MOSAIC JSON, DB and manifest. Current flags: Density, DurationProfile, Seed. |
| Template JSON | data/mosaic-scenarios/MaGaLiteratureBasedUrbanStudy/**/*.template.json | Versioned scaffold only; concrete scenarios are currently generated under tmp. |
| Mobility profile | tools/intas-literature-scenario/config/synthetic_mobility_profile.json | Density profiles, route family catalog, RSU positions and long-duration repetition. |
| Workload config | tools/intas-literature-scenario/config/literature_calibration_catalog.json -> workloadGeneration | Supported by runtime JSON, but not exposed as materializer CLI flags. |
| CPU config | literature_calibration_catalog.computeProfiles -> ma_ga_live_state_config.json | Supported by runtime JSON, but not exposed as materializer CLI flags. |
| CELL config | literature_calibration_catalog.cellProfiles; cell/network/regions JSON | Supported if capacity and delay fields are patched consistently. |
| V2V config | literature_calibration_catalog.v2vProfiles; sns_config.json; ma_ga_live_state_config.json | Supported if live-state and SNS fields remain coherent. |
| Runtime MA-GA config | ma_ga_live_runtime_config.json; MaGaLiveRuntimeConfig.java | Supports STATIC/ADAPTIVE and diagnostic delay; validators currently assume STATIC. |
| Deploy script | tools/intas-literature-scenario/deploy_materialized_literature_scenario.ps1 | Copies materialized scenario into tmp/mosaic-25.2/scenarios and injects JARs. |
| MOSAIC run script | tools/intas-literature-scenario/run_literature_scenario.ps1; quick_literature_workflow.ps1 | Builds runtime JAR, deploys, runs mosaic.bat, summarizes and smoke-validates. Not to run in this subphase. |
| Validators | validate_literature_configuration.py; validate_materialized_literature_scenario.py; validate_literature_smoke_run.ps1 | Validate textual config, materialized scenario, DB and smoke evidence. Some assumptions are canonical/static. |
| Parsers/aggregators | tools/mosaic-live-maga-runtime/summarize-run.ps1; show_latest_literature_report.ps1 | Run-level summarizer exists; no cross-campaign aggregator was found. |
| Scenario folders | data/mosaic-scenarios/*; tmp/materialized-literature-scenarios/*; tmp/mosaic-25.2/scenarios/*; archive/historical-scenarios/* | Versioned templates, local materializations, deployed runtime scenarios and historical archives are separated. |
| Log/archive folders | tmp/mosaic-25.2/logs; tmp/archive; archive/freeze-manifest; archive/generated-offline-artifacts | Existing log and archive conventions are compatible with separate campaign audit/result roots. |

## Real parameter support

| Parametro matrice | Campo/file reale | Valore baseline | Esito audit |
| --- | --- | --- | --- |
| density | materialize_literature_scenario.ps1 -Density / build_intas_literature_scenario.py --density | low_density, nominal, high_density | supported by existing tooling |
| durationProfile | -DurationProfile / --duration-profile, targets.durationsSeconds | smoke=180, nominal=300, extended=600 | supported by existing tooling |
| seed | -Seed / --seed; materialization_manifest.seed; scenario_config.simulation.randomSeed | route/materialization seed supported | workloadGeneration.randomSeed remains fixed at 104729 unless patched |
| arrivalRate | ma_ga_live_state_config.workloadGeneration.arrivalRateTasksPerSecondPerActiveVehicle | 1.0 baseline; smoke wrapper 0.02 | supported by JSON loader, no materializer flag |
| LIGHT/MEDIUM/HEAVY weight | workloadGeneration.profiles[].weight | 0.50/0.35/0.15 baseline | supported by JSON loader, no materializer flag |
| route-family focus | synthetic_mobility_profile.densityProfiles.*.families and generated route catalog | families exist: DUAL_RSU_SWITCH, BOTH_RSU_NO_SWITCH, RSU_0_ONLY, RSU_1_ONLY, BACKGROUND | requires decision/tooling for focus labels and concrete counts |
| local CPU | ma_ga_live_state_config.localCpuCyclesPerSecond | 1000000000 | supported by JSON loader |
| remote vehicle CPU | ma_ga_live_state_config.remoteVehicleCpuCyclesPerSecond | 1000000000 | supported by JSON loader |
| edge CPU | staticInfrastructure.edgeNodes[].availableCpuCyclesPerSecond | 5000000000 | supported by JSON loader |
| cloud CPU | staticInfrastructure.cloudNodes[].availableCpuCyclesPerSecond | 100000000000 | supported by JSON loader |
| CELL bandwidth | configuredCellProfile.capacityBitsPerSecond; cellDiagnosticAccounting.gatewayPools[].nominalCapacityBitsPerSecond; cell/network/regions capacity | 49200000 bps | supported if patched coherently across files |
| V2V bandwidth | v2vNominalBandwidthBitsPerSecond | 4700000 bps | supported by JSON loader |
| V2V range | ma_ga_live_state_config.singlehopRadiusMeters; sns_config.singlehopRadius | 250 m | supported if patched coherently across live-state and SNS |
| CELL RTT | configuredCellProfile.measuredRttSeconds; symmetricOneWayDelaySeconds; cell/network/regions delays | 0.0522 s RTT; 0.0261 s one-way | supported if patched coherently across files |
| gaParameterScalingMode | ma_ga_live_runtime_config.gaParameterScalingMode | STATIC baseline; loader accepts STATIC/ADAPTIVE | runtime supports it, current materializer/validator enforce STATIC for canonical materializations |
| diagnosticArtificialGaDelayMs | ma_ga_live_runtime_config.diagnosticArtificialGaDelayMs | 0 | supported by runtime config loader |

## Classification summary - Stato iniziale prima dell'esecuzione di G00

| Classificazione | Conteggio |
| --- | --- |
| NEEDS_TEST_TOOLING_EXTENSION | 1 |
| NON_MATERIALIZABLE | 2 |
| READY_CONFIG_ONLY | 14 |
| READY_EXISTING_TOOLING | 7 |
| REQUIRES_DECISION | 6 |

## Classification by Config_ID - Stato iniziale prima dell'esecuzione di G00

| Config_ID | Classification | Materializable | Instances | Supported parameters | Unsupported/risks | Decision required | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CFG-PRE-AUDIT | NON_MATERIALIZABLE | no | 0 | audit-only; no scenario parameters | none known for frozen core | no | lambda=; multipliers cpu=1/1/1/1, cell=1, v2v=1, range=1, rtt=1; ga=STATIC, delayMs=0 |
| CFG-PRE-VALIDATE | NON_MATERIALIZABLE | no | 0 | audit-only; no scenario parameters | none known for frozen core | no | lambda=1; weights=0.5/0.35/0.15; multipliers cpu=1/1/1/1, cell=1, v2v=1, range=1, rtt=1; ga=STATIC, delayMs=0 |
| CFG-SMOKE | READY_EXISTING_TOOLING | yes | 1 | density; durationProfile; mobility seed | none known for frozen core | no | lambda=; weights=0.5/0.35/0.15; multipliers cpu=1/1/1/1, cell=1, v2v=1, range=1, rtt=1; ga=STATIC, delayMs=0; existing smoke wrapper lowers workload rate to 0.02 and runtime tick to 500 ms |
| CFG-REPRO | READY_EXISTING_TOOLING | yes | 2 | density; durationProfile; mobility seed | same density-duration-seed path collides unless orchestration uses distinct target roots/materialization ids | no | lambda=1; weights=0.5/0.35/0.15; multipliers cpu=1/1/1/1, cell=1, v2v=1, range=1, rtt=1; ga=STATIC, delayMs=0 |
| CFG-RUNTIME-REPEAT | READY_EXISTING_TOOLING | yes | 1 | density; durationProfile; mobility seed | none known for frozen core | no | lambda=1; weights=0.5/0.35/0.15; multipliers cpu=1/1/1/1, cell=1, v2v=1, range=1, rtt=1; ga=STATIC, delayMs=0 |
| CFG-L-E | READY_CONFIG_ONLY | yes | 5 | density; durationProfile; mobility seed; workloadGeneration.arrivalRateTasksPerSecondPerActiveVehicle; workloadGeneration.profiles[].weight | workloadGeneration.randomSeed is not propagated from materializer --seed without a config patch | no | lambda=1; weights=0.75/0.2/0.05; multipliers cpu=1/1/1/1, cell=1, v2v=1, range=1, rtt=1; ga=STATIC, delayMs=0 |
| CFG-L-I | READY_EXISTING_TOOLING | yes | 5 | density; durationProfile; mobility seed | workloadGeneration.randomSeed is not propagated from materializer --seed without a config patch | no | lambda=1; weights=0.5/0.35/0.15; multipliers cpu=1/1/1/1, cell=1, v2v=1, range=1, rtt=1; ga=STATIC, delayMs=0 |
| CFG-L-S | READY_CONFIG_ONLY | yes | 5 | density; durationProfile; mobility seed; workloadGeneration.arrivalRateTasksPerSecondPerActiveVehicle; workloadGeneration.profiles[].weight | workloadGeneration.randomSeed is not propagated from materializer --seed without a config patch | no | lambda=1; weights=0.25/0.3/0.45; multipliers cpu=1/1/1/1, cell=1, v2v=1, range=1, rtt=1; ga=STATIC, delayMs=0 |
| CFG-N-E | READY_CONFIG_ONLY | yes | 5 | density; durationProfile; mobility seed; workloadGeneration.arrivalRateTasksPerSecondPerActiveVehicle; workloadGeneration.profiles[].weight | workloadGeneration.randomSeed is not propagated from materializer --seed without a config patch | no | lambda=1; weights=0.75/0.2/0.05; multipliers cpu=1/1/1/1, cell=1, v2v=1, range=1, rtt=1; ga=STATIC, delayMs=0 |
| CFG-N-I | READY_EXISTING_TOOLING | yes | 5 | density; durationProfile; mobility seed | workloadGeneration.randomSeed is not propagated from materializer --seed without a config patch | no | lambda=1; weights=0.5/0.35/0.15; multipliers cpu=1/1/1/1, cell=1, v2v=1, range=1, rtt=1; ga=STATIC, delayMs=0 |
| CFG-N-S | READY_CONFIG_ONLY | yes | 5 | density; durationProfile; mobility seed; workloadGeneration.arrivalRateTasksPerSecondPerActiveVehicle; workloadGeneration.profiles[].weight | workloadGeneration.randomSeed is not propagated from materializer --seed without a config patch | no | lambda=1; weights=0.25/0.3/0.45; multipliers cpu=1/1/1/1, cell=1, v2v=1, range=1, rtt=1; ga=STATIC, delayMs=0 |
| CFG-H-E | READY_CONFIG_ONLY | yes | 5 | density; durationProfile; mobility seed; workloadGeneration.arrivalRateTasksPerSecondPerActiveVehicle; workloadGeneration.profiles[].weight | workloadGeneration.randomSeed is not propagated from materializer --seed without a config patch | no | lambda=1; weights=0.75/0.2/0.05; multipliers cpu=1/1/1/1, cell=1, v2v=1, range=1, rtt=1; ga=STATIC, delayMs=0 |
| CFG-H-I | READY_EXISTING_TOOLING | yes | 5 | density; durationProfile; mobility seed | workloadGeneration.randomSeed is not propagated from materializer --seed without a config patch | no | lambda=1; weights=0.5/0.35/0.15; multipliers cpu=1/1/1/1, cell=1, v2v=1, range=1, rtt=1; ga=STATIC, delayMs=0 |
| CFG-H-S | READY_CONFIG_ONLY | yes | 5 | density; durationProfile; mobility seed; workloadGeneration.arrivalRateTasksPerSecondPerActiveVehicle; workloadGeneration.profiles[].weight | workloadGeneration.randomSeed is not propagated from materializer --seed without a config patch | no | lambda=1; weights=0.25/0.3/0.45; multipliers cpu=1/1/1/1, cell=1, v2v=1, range=1, rtt=1; ga=STATIC, delayMs=0 |
| CFG-N-I-EXT | READY_EXISTING_TOOLING | yes | 5 | density; durationProfile; mobility seed | workloadGeneration.randomSeed is not propagated from materializer --seed without a config patch | no | lambda=1; weights=0.5/0.35/0.15; multipliers cpu=1/1/1/1, cell=1, v2v=1, range=1, rtt=1; ga=STATIC, delayMs=0 |
| CFG-H-S-EXT | READY_CONFIG_ONLY | yes | 1 | density; durationProfile; mobility seed; workloadGeneration.arrivalRateTasksPerSecondPerActiveVehicle; workloadGeneration.profiles[].weight | none known for frozen core | no | lambda=1; weights=0.25/0.3/0.45; multipliers cpu=1/1/1/1, cell=1, v2v=1, range=1, rtt=1; ga=STATIC, delayMs=0 |
| CFG-M-BACKGROUND | REQUIRES_DECISION | yes | 1 | density; durationProfile; mobility seed; V2V range/BW JSON fields | route-family focus labels are not mapped to concrete route family vehicle/template counts | yes | lambda=1; weights=0.5/0.35/0.15; multipliers cpu=1/1/1/1, cell=1, v2v=1, range=0.25, rtt=1; ga=STATIC, delayMs=0; decision needed: numeric route-family mix/focus acceptance criteria |
| CFG-M-RSU0 | REQUIRES_DECISION | yes | 1 | density; durationProfile; mobility seed; V2V range/BW JSON fields | route-family focus labels are not mapped to concrete route family vehicle/template counts | yes | lambda=1; weights=0.5/0.35/0.15; multipliers cpu=1/1/1/1, cell=1, v2v=1, range=1, rtt=1; ga=STATIC, delayMs=0; decision needed: numeric route-family mix/focus acceptance criteria |
| CFG-M-RSU1 | REQUIRES_DECISION | yes | 1 | density; durationProfile; mobility seed; V2V range/BW JSON fields | route-family focus labels are not mapped to concrete route family vehicle/template counts | yes | lambda=1; weights=0.5/0.35/0.15; multipliers cpu=1/1/1/1, cell=1, v2v=1, range=1, rtt=1; ga=STATIC, delayMs=0; decision needed: numeric route-family mix/focus acceptance criteria |
| CFG-M-SWITCH | REQUIRES_DECISION | yes | 1 | density; durationProfile; mobility seed; V2V range/BW JSON fields | route-family focus labels are not mapped to concrete route family vehicle/template counts | yes | lambda=1; weights=0.5/0.35/0.15; multipliers cpu=1/1/1/1, cell=1, v2v=1, range=1, rtt=1; ga=STATIC, delayMs=0; decision needed: numeric route-family mix/focus acceptance criteria |
| CFG-M-V2V | REQUIRES_DECISION | yes | 1 | density; durationProfile; mobility seed; V2V range/BW JSON fields | route-family focus labels are not mapped to concrete route family vehicle/template counts | yes | lambda=1; weights=0.5/0.35/0.15; multipliers cpu=1/1/1/1, cell=1, v2v=1, range=1.5, rtt=1; ga=STATIC, delayMs=0; decision needed: numeric route-family mix/focus acceptance criteria |
| CFG-R-LOCALCPU | READY_CONFIG_ONLY | yes | 1 | density; durationProfile; mobility seed; workloadGeneration.arrivalRateTasksPerSecondPerActiveVehicle; workloadGeneration.profiles[].weight; CPU/BW/RTT JSON fields | none known for frozen core | no | lambda=1; weights=0.25/0.3/0.45; multipliers cpu=0.5/1/1/1, cell=1, v2v=1, range=1, rtt=1; ga=STATIC, delayMs=0 |
| CFG-R-EDGECPU | READY_CONFIG_ONLY | yes | 1 | density; durationProfile; mobility seed; workloadGeneration.arrivalRateTasksPerSecondPerActiveVehicle; workloadGeneration.profiles[].weight; CPU/BW/RTT JSON fields | none known for frozen core | no | lambda=1; weights=0.25/0.3/0.45; multipliers cpu=1/1/0.25/1, cell=1, v2v=1, range=1, rtt=1; ga=STATIC, delayMs=0 |
| CFG-R-CLOUDCPU | READY_CONFIG_ONLY | yes | 1 | density; durationProfile; mobility seed; workloadGeneration.arrivalRateTasksPerSecondPerActiveVehicle; workloadGeneration.profiles[].weight; CPU/BW/RTT JSON fields | none known for frozen core | no | lambda=1; weights=0.25/0.3/0.45; multipliers cpu=1/1/1/0.02, cell=1, v2v=1, range=1, rtt=1; ga=STATIC, delayMs=0 |
| CFG-R-CELLBW | READY_CONFIG_ONLY | yes | 1 | density; durationProfile; mobility seed; workloadGeneration.arrivalRateTasksPerSecondPerActiveVehicle; workloadGeneration.profiles[].weight; CPU/BW/RTT JSON fields | none known for frozen core | no | lambda=1; weights=0.25/0.3/0.45; multipliers cpu=1/1/1/1, cell=0.25, v2v=1, range=1, rtt=1; ga=STATIC, delayMs=0 |
| CFG-R-V2VBW | REQUIRES_DECISION | yes | 1 | density; durationProfile; mobility seed; CPU/BW/RTT JSON fields; V2V range/BW JSON fields | route-family focus labels are not mapped to concrete route family vehicle/template counts | yes | lambda=1; weights=0.25/0.3/0.45; multipliers cpu=1/1/1/1, cell=1, v2v=0.25, range=1.5, rtt=1; ga=STATIC, delayMs=0; decision needed: numeric route-family mix/focus acceptance criteria |
| CFG-R-RTT | READY_CONFIG_ONLY | yes | 1 | density; durationProfile; mobility seed; workloadGeneration.arrivalRateTasksPerSecondPerActiveVehicle; workloadGeneration.profiles[].weight; CPU/BW/RTT JSON fields | none known for frozen core | no | lambda=1; weights=0.5/0.35/0.15; multipliers cpu=1/1/1/1, cell=1, v2v=1, range=1, rtt=3; ga=STATIC, delayMs=0 |
| CFG-G-SPARSE | READY_CONFIG_ONLY | yes | 1 | density; durationProfile; mobility seed; workloadGeneration.arrivalRateTasksPerSecondPerActiveVehicle; workloadGeneration.profiles[].weight | none known for frozen core | no | lambda=0.05; weights=0.75/0.2/0.05; multipliers cpu=1/1/1/1, cell=1, v2v=1, range=1, rtt=1; ga=STATIC, delayMs=0; EMPTY_TASK_SET is an expected observation target, not guaranteed by the live runtime |
| CFG-G-ADAPTIVE | NEEDS_TEST_TOOLING_EXTENSION | yes | 1 | density; durationProfile; mobility seed; runtime gaParameterScalingMode supported by loader | current materializer/validators enforce gaParameterScalingMode=STATIC for canonical scenarios | no | lambda=1; weights=0.25/0.3/0.45; multipliers cpu=1/1/1/1, cell=1, v2v=1, range=1, rtt=1; ga=ADAPTIVE, delayMs=0 |
| CFG-G-STALE | READY_CONFIG_ONLY | yes | 1 | density; durationProfile; mobility seed; workloadGeneration.arrivalRateTasksPerSecondPerActiveVehicle; workloadGeneration.profiles[].weight; diagnosticArtificialGaDelayMs | none known for frozen core | no | lambda=1; weights=0.5/0.35/0.15; multipliers cpu=1/1/1/1, cell=1, v2v=1, range=1, rtt=1; ga=STATIC, delayMs=300 |

## Key risks and decisions - Stato iniziale prima dell'esecuzione di G00

- workloadGeneration.randomSeed is a real runtime field, but current materializer --seed does not propagate into it. Multi-seed campaign tooling should patch it or explicitly document that only mobility demand varies by seed.
- Route focus labels such as BACKGROUND_DOMINANT, RSU_0_ONLY, DUAL_RSU_SWITCH_DOMINANT and BACKGROUND_AND_DUAL do not yet map to concrete route family counts. These configurations are marked REQUIRES_DECISION.
- CELL bandwidth and RTT variants touch multiple files and must be patched coherently: live-state configured cell profile, cell diagnostic pools, network and regions.
- CFG-G-ADAPTIVE is supported by the runtime loader but conflicts with validators/materializer checks that currently enforce STATIC for the literature scenario. This is a test tooling extension, not a frozen-core change.
- Current scripts produce materializations under a density/duration/seed naming scheme. The final campaign needs Config_ID-aware target roots to avoid collisions between workload/resource variants sharing the same density, duration and seed.

## No-code-change boundary

No core MA-GA file, Java MOSAIC runtime class, formalization, existing script behavior or .gitignore is modified by this subphase. This audit only records what future tooling may need to do on testing/final-campaign.

## G00 Campaign Tooling Resolution

Stato iniziale prima dell'esecuzione di G00: le configurazioni route dirette e `CFG-G-ADAPTIVE` richiedevano tooling di campagna dedicato.

Stato corrente post-G00/G00F: le decisioni route-family sono chiuse tramite profili diretti nel campaign spec; `CFG-G-ADAPTIVE` resta non canonica ed e accettata solo dal validator di campagna. Le classificazioni correnti sono: NON_MATERIALIZABLE=2, READY_CONFIG_ONLY=20, READY_EXISTING_TOOLING=8, REQUIRES_DECISION=0, NEEDS_TEST_TOOLING_EXTENSION=0. Sono stati materializzati 69 scenari; MOSAIC non e stato eseguito; G00 e tecnicamente completata e l'audit e stato normalizzato in G00F.
