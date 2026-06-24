# G04-R-01 - Analisi controllata della mobilità

- Stato complessivo: **PASS**
- Validator canonico: `LITERATURE_SMOKE_TEST_FAILED`
- Stato tecnico mirato: `PASS_WITH_EXPECTED_STATIC_WORKLOAD_VALIDATOR_REJECTION`
- Violazioni runtime: `0`
- Veicolo probe: `veh_2`
- Snapshot con probe: `1548`
- Transizione osservata: `{'fromSnapshotId': 'live_runtime_snapshot_t_81700000000', 'fromTimeSeconds': 81.7, 'toSnapshotId': 'live_runtime_snapshot_t_81800000000', 'toTimeSeconds': 81.8, 'observedTransitionTimeSeconds': 81.8}`
- Geni remoti applicati al probe: `123`

## Verdetti
- T-096: **PASS**
- T-098: **PASS**
- T-091: **EVIDENZA_PARZIALE**
- T-094: **NON_OSSERVATO**
- T-093: non ripetuto, già PASS_RECOVERED.

La run è unica e non deve essere rilanciata automaticamente in caso di evidenza funzionale incompleta.
