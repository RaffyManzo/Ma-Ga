# Indice delle evidenze G06 per il capitolo 7

1. `test-audits/final-campaign-v2-local-contention/G06_runtime_temporal_policies/G06_functional_test_results.csv`
2. `test-audits/final-campaign-v2-local-contention/G06_runtime_temporal_policies/G06_supplementary_test_results.csv`
3. `test-audits/final-campaign-v2-local-contention/G06_runtime_temporal_policies/G06_causal_job_analysis.csv`
4. `test-audits/final-campaign-v2-local-contention/G06_runtime_temporal_policies/G06_causal_summary.json`
5. `test-audits/final-campaign-v2-local-contention/G06_runtime_temporal_policies/G06_delay_dose_response.csv`
6. `test-audits/final-campaign-v2-local-contention/G06_runtime_temporal_policies/G06_cross_phase_temporal_summary_final.json`
7. `test-audits/final-campaign-v2-local-contention/G06_runtime_temporal_policies/G06_anomalies_and_limits.csv`
8. `test-results/final-campaign-v2-local-contention/G06_runtime_temporal_policies/runs/`

## Regola interpretativa

Il tempo simulato avanza più rapidamente del calcolo wall-clock. Quando il
runtime GA supera deltaTMax, il risultato viene scartato come stale. La
simulazione può quindi concludersi senza mantenere strategie aggiornate.
