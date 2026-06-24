# G05 - Traccia di configurazione R-LOCALCPU-01

Stato: **READY_FOR_EXECUTION_PREPARATION**

Questa verifica non ha avviato MOSAIC e non ha modificato Java o la materializzazione.

## Identificazione

- Run: `R-LOCALCPU-01`
- Configurazione: `CFG-R-LOCALCPU`
- Materializzazione: `MAT-CFG-R-LOCALCPU-104729`
- Seed: `104729`
- Durata prevista: ``180 s``
- Densita: ``nominal``
- Workload: ``WL-S``

## Leva applicata

| Risorsa | Baseline | Moltiplicatore | Valore materializzato |
|---|---:|---:|---:|
| CPU locale | 1000000000 cicli/s | 0.5 | 500000000 cicli/s |
| CPU veicolo remoto | invariata | 1.0 | baseline |
| CPU EDGE | invariata | 1.0 | baseline |
| CPU CLOUD | invariata | 1.0 | baseline |
| Banda CELL | invariata | 1.0 | baseline |
| Banda V2V | invariata | 1.0 | baseline |
| RTT CELL | invariato | 1.0 | baseline |

## Effetto teorico sul solo calcolo locale

| Profilo | Cicli | Deadline | Tempo locale baseline | Tempo locale R-LOCALCPU |
|---|---:|---:|---:|---:|
| LIGHT | 200000000 | 0.5 s | 0.2 s | 0.4 s |
| MEDIUM | 600000000 | 1.0 s | 0.6 s | 1.2 s |
| HEAVY | 3200000000 | 4.0 s | 3.2 s | 6.4 s |

Con la CPU dimezzata, LIGHT resta teoricamente entro la deadline locale, mentre MEDIUM e HEAVY diventano piu difficili o non sostenibili localmente. Questo crea una pressione selettiva senza annullare completamente l opzione LOCAL.

## Verifiche concluse

- la configurazione e marcata ``READY_CONFIG_ONLY`` e non richiede decisioni Java;
- il moltiplicatore ``cpu=0.5/1/1/1`` e presente nel mapping;
- la materializzazione risulta ``MATERIALIZED_VALIDATED``;
- `localCpuCyclesPerSecond` vale realmente `500000000`;
- le altre famiglie di risorse restano ai valori baseline;
- validator: `MATERIALIZED_VALIDATED` con zero errori;
- occorrenze Java del parametro: `5`.

## Decisione

R-LOCALCPU-01 e pronta per la preparazione della singola esecuzione MOSAIC. La simulazione non viene avviata da questa sottofase.
