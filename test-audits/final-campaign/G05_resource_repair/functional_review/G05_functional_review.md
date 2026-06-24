# G05 - Revisione funzionale del batch risorse e repair

Stato: **G05_FUNCTIONAL_REVIEW_COMPLETE_RECOVERY_DECISION_REQUIRED**

Batch sorgente: `G05-RESOURCE-REPAIR-20260624-120030`

## Metodo

La revisione distingue le strategie realmente applicate dai risultati scartati come stale. I conteggi LOCAL, VEHICLE, EDGE e CLOUD sono cumulativi sulle finestre applicate e non rappresentano task unici.

## Risultati per run

| Run | Stato funzionale | L/V/E/C applicati | Stale % | Ultimo apply | Recupero |
|---|---|---:|---:|---:|---|
| R-LOCALCPU-01 | EFFECT_OBSERVED_WITH_TEMPORAL_LIMIT | 4/2/0/0 | 25 | 7,7 s | YES |
| R-EDGECPU-01 | TARGET_RESOURCE_NOT_EXERCISED | 10831/0/0/0 | 15,019763 | 180 s | NO |
| R-CLOUDCPU-01 | TARGET_RESOURCE_NOT_EXERCISED | 8142/0/0/0 | 21,702128 | 179,8 s | NO |
| R-CELLBW-01 | TARGET_LINK_NOT_EXERCISED | 9408/0/0/0 | 18,292683 | 179,7 s | NO |
| R-RTT-01 | STABLE_RUN_TARGET_LINK_NOT_EXERCISED | 10389/1/0/0 | 0,306748 | 180 s | NO |
| R-V2VBW-01 | STRESS_LIMIT_TARGET_NOT_EXERCISED | 2291/0/0/0 | 46,808511 | 39,4 s | NO |

## Conclusioni

- Tutte le sei run sono tecnicamente valide: validator PASS, snapshot lag zero e nessuna violazione runtime.
- R-LOCALCPU-01 mostra un effetto reale della CPU locale dimezzata, con decisioni VEHICLE applicate, ma perde continuita dopo 7.7 s.
- R-EDGECPU-01, R-CLOUDCPU-01 e R-CELLBW-01 evitano la risorsa remota stressata; questo e un risultato utilizzabile, ma non dimostra repair o saturazione.
- R-RTT-01 e stabile, ma nessuna scelta EDGE o CLOUD attraversa il link CELL ad RTT elevato.
- R-V2VBW-01 documenta un limite dello stress profile high_density: alto stale ratio e interruzione delle applicazioni strategiche.
- Repair/fallback observability: `NOT_EXPOSED_IN_CURRENT_G05_TRACES`.

## Test residui G04

| Test | Stato | Evidenza applicata |
|---|---|---:|
| T-091 | NOT_OBSERVED | 0 |
| T-093 | PASS_RECOVERED | 3 |
| T-094 | NOT_OBSERVED_IN_APPLIED_STRATEGIES | 0 |

## Decisione

Non va ripetuto l intero batch. E consigliato un recupero selettivo di R-LOCALCPU-01 per verificare la riproducibilita del limite temporale. T-091 e T-094 restano evidenze residue da rivalutare dopo G06, prima di eventuali run G04-R e prima di G02B.

Le matrici non vengono aggiornate in questa sottofase.
