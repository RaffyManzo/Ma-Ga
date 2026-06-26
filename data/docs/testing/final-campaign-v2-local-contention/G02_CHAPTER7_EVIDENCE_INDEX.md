# Indice delle evidenze G02 per il capitolo 7

## Stato

- gruppo: G02;
- run valide: 45/45;
- configurazioni: 9;
- seed: 5 per configurazione;
- gate: PASS_G02_SCIENTIFIC_GATE;
- commit previsto: `test(campaign-v2): close G02 main factorial experiments`.

## File da usare per la scrittura

1. `test-audits/final-campaign-v2-local-contention/G02_main_factorial_experiments/G02_per_run_metrics.csv`
   - livello: singola run;
   - uso: controlli puntuali, seed, outlier e tracciabilita'.

2. `test-audits/final-campaign-v2-local-contention/G02_main_factorial_experiments/G02_configuration_aggregates.csv`
   - livello: configurazione;
   - uso: tabella principale dei nove casi.

3. `test-audits/final-campaign-v2-local-contention/G02_main_factorial_experiments/G02_density_aggregates.csv`
   - livello: densita';
   - uso: effetto low/nominal/high.

4. `test-audits/final-campaign-v2-local-contention/G02_main_factorial_experiments/G02_workload_aggregates.csv`
   - livello: workload;
   - uso: effetto WL-E/WL-I/WL-S.

5. `test-audits/final-campaign-v2-local-contention/G02_main_factorial_experiments/G02_scientific_gate.json`
   - uso: validita' e criteri di chiusura.

6. `test-audits/final-campaign-v2-local-contention/G02_main_factorial_experiments/G02_anomalies_and_limits.csv`
   - uso: risultati inattesi e limiti da non omettere.

7. `test-audits/final-campaign-v2-local-contention/G02_main_factorial_experiments/G02_workload_configuration_evidence.json`
   - uso: pesi dei profili e complessita' configurata.

8. `test-audits/final-campaign-v2-local-contention/G02_main_factorial_experiments/context_snapshots/`
   - uso: mapping, piano config-seed e test ID congelati al momento della chiusura.

9. `data/docs/testing/final-campaign-v2-local-contention/G02_AUDIT_SUMMARY_FOR_RESULTS.md`
   - uso: sintesi narrativa pronta per la tesi.

## Regola interpretativa centrale

Il profilo WL-S ha maggiore peso configurato sui task heavy. Il massimo
tempo locale indipendente osservato non deve pero' crescere obbligatoriamente:
riguarda solo le porzioni che la strategia assegna localmente. La maggiore
pressione del workload e' invece visibile nel pending peak medio e nella
variazione delle decisioni.
