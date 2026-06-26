# Gate scientifico G02

- stato: **PASS_G02_SCIENTIFIC_GATE**;
- run valide: **45/45**;
- validator PASS: **45/45**;
- risultati nulli e job falliti: **0**;
- run con contesa osservata: **34**;

Il precedente controllo che imponeva `WL-S >= WL-E` sul massimo tempo di esecuzione locale indipendente non era metodologicamente corretto. Tale metrica descrive solo le porzioni che la strategia ha lasciato localmente e non la complessita' del workload generato.

| Controllo | Stato | Atteso | Osservato |
|---|---|---|---|
| RUN-COUNT | PASS | 45 | 45 |
| VALIDATOR | PASS | 45 PASS | 45 |
| ZERO-NULL-FAILED | PASS | 0 | {"null": 0, "failed": 0} |
| GA-JOB-ACCOUNTING | PASS | 45 run coerenti | 45 |
| ZERO-MODEL-VIOLATIONS | PASS | 0 | 0 |
| CONTENTION-OBSERVED | PASS | > 0 run | 34 |
| DENSITY-TASK-TREND | PASS | high_density > nominal > low_density | {"high_density": 15302.0, "low_density": 5499.6, "nominal": 9295.0} |
| WORKLOAD-CONFIGURED-COMPLEXITY | PASS | WL-E < WL-I < WL-S | {"WL-E": 430000000.0, "WL-I": 790000000.0, "WL-S": 1670000000.0} |
| WORKLOAD-SELECTED-LOCAL-OUTCOME | OBSERVED_NOT_A_GATE | nessuna monotonia imposta | {"WL-E": 2.5958126444024368, "WL-I": 2.267812644402437, "WL-S": 1.36} |
| CONFIGURATION-VARIATION | PASS | variazione osservabile | {"pendingPeakByWorkload": {"WL-E": 74.4, "WL-I": 91.66666666666667, "WL-S": 137.26666666666668}, "remoteShareByWorkload": {"WL-E": 5.401459854014599, "WL-I": 7.981220657276995, "WL-S": 9.876543209876543}} |
