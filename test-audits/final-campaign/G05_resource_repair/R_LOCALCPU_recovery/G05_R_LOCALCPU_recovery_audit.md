# G05 - Recupero selettivo R-LOCALCPU-01

Stato: **PASS_WITH_REPRODUCIBLE_TEMPORAL_LIMIT**

## Scopo

La run R-LOCALCPU-01 del batch originale ha mostrato due assegnazioni VEHICLE applicate, ma ha smesso di applicare strategie dopo 7.7 secondi. Sono state eseguite due repliche isolate con la stessa materializzazione, lo stesso seed e lo stesso JAR.

## Invarianza sperimentale

- Materializzazione: `MAT-CFG-R-LOCALCPU-104729`
- Seed: ``104729``
- Durata: ``180 s``
- Densita: ``nominal``
- Workload: ``WL-S``
- CPU locale: ``500000000 cicli/s``
- Runtime JAR SHA-256: `3a5ae6111f251b97e3940fc57f5df5ab960adcb0af1bd985a9a0a7f680893575`

## Confronto

| Osservazione | L/V/E/C | Stale % | Ultimo apply | Gap finale | Classe temporale |
|---|---:|---:|---:|---:|---|
| ORIGINAL_BATCH | 4/2/0/0 | 25 | 7,7 s | 172,3 s | LONG_FINAL_GAP |
| RECOVERY_A | 9/23/1/2 | 25,641026 | 24,9 s | 155,1 s | LONG_FINAL_GAP |
| RECOVERY_B | 10/9/3/2 | 25,641026 | 12,1 s | 167,9 s | LONG_FINAL_GAP |

## Decisione

- Continuita temporale: `TEMPORAL_LIMIT_REPRODUCED`
- Effetto di offloading: `OFFLOADING_EFFECT_REPRODUCED_TWICE`
- Verdetto finale R-LOCALCPU: `PASS_WITH_REPRODUCIBLE_TEMPORAL_LIMIT`

Le tre osservazioni usano lo stesso scenario logico. I task generati e attivati devono coincidere; i tempi del GA e il numero di risultati stale possono variare per la natura asincrona del runtime.

## Uso nella tesi

Il risultato permette di distinguere due aspetti: la reazione del MA-GA alla CPU locale ridotta e la capacita del runtime di applicare strategie lungo l intera simulazione. I due aspetti non vanno fusi in un unico indicatore.

## Passo successivo

Preparare la sintesi G05 per la tesi, confrontare le sei configurazioni con una baseline compatibile e mantenere T-091 e T-094 come evidenze residue da rivalutare dopo G06.

Le matrici non vengono aggiornate in questa sottofase.
