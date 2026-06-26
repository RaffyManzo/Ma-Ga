# Audit riepilogativo G02 per il capitolo dei risultati

## Identificazione

- campagna: MA-GA V2 con contesa CPU locale;
- gruppo: G02 - esperimenti fattoriali principali;
- configurazioni: 9;
- seed per configurazione: 5;
- run valide: 45;
- durata per run: 300 s;
- variante: FULL_MA_GA;
- scaling GA: STATIC;
- JAR SHA-256: 3821c28a6a0898e94f4d5136b8039595d36bcbce0b6ecc3e9406910f9e083068.

## Validita'

- validator PASS: 45/45;
- risultati nulli: 0;
- job GA falliti: 0;
- contabilita' GA coerente: 45/45;
- violazioni strutturali o temporali: 0;
- run con contesa locale osservata: 34;
- assegnazioni LOCAL complessive: 917;
- assegnazioni remote complessive: 62;
- job SHUTDOWN_IN_FLIGHT complessivi: 36.

## Disegno sperimentale

Il gruppo combina tre densita' veicolari e tre profili di workload.
Ogni configurazione viene ripetuta con cinque seed.

I profili differiscono per i pesi light/medium/heavy:

- WL-E: 0.75 / 0.20 / 0.05;
- WL-I: 0.50 / 0.35 / 0.15;
- WL-S: 0.25 / 0.30 / 0.45.

La domanda computazionale configurata attesa cresce quindi da WL-E a WL-S.

## Risultato inatteso da interpretare

Il massimo tempo locale indipendente medio osservato e':

- WL-E: 2.595813 s;
- WL-I: 2.267813 s;
- WL-S: 1.360000 s.

Questa metrica riguarda soltanto le porzioni assegnate localmente e non
misura direttamente la complessita' dei task generati. Non deve quindi essere
usata come gate monotono del workload.

La pressione del workload e' invece visibile nel pending peak medio:

- WL-E: 74.400;
- WL-I: 91.667;
- WL-S: 137.267.

La quota remota calcolata sui conteggi complessivi cresce:

- WL-E: 5.401460%;
- WL-I: 7.981221%;
- WL-S: 9.876543%.

## Sintesi per configurazione

| Config | Densita' | Workload | Run | Task medi | Pending peak medio | Stale medio (%) | Locale totale | Remoto totale | Contesa media |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|
| CFG-H-E | high_density | WL-E | 5 | 15302.000 | 108.800 | 7.144573 | 124 | 4 | 2.000 |
| CFG-H-I | high_density | WL-I | 5 | 15302.000 | 134.000 | 7.284772 | 33 | 1 | 0.600 |
| CFG-H-S | high_density | WL-S | 5 | 15302.000 | 205.000 | 5.380939 | 4 | 1 | 0.000 |
| CFG-L-E | low_density | WL-E | 5 | 5499.600 | 45.200 | 13.292552 | 407 | 26 | 6.400 |
| CFG-L-I | low_density | WL-I | 5 | 5499.600 | 56.600 | 10.512625 | 89 | 7 | 2.800 |
| CFG-L-S | low_density | WL-S | 5 | 5499.600 | 83.200 | 5.404080 | 39 | 3 | 1.600 |
| CFG-N-E | nominal | WL-E | 5 | 9295.000 | 69.200 | 14.736744 | 117 | 7 | 2.800 |
| CFG-N-I | nominal | WL-I | 5 | 9295.000 | 84.400 | 8.420332 | 74 | 9 | 2.400 |
| CFG-N-S | nominal | WL-S | 5 | 9295.000 | 123.600 | 5.081190 | 30 | 4 | 0.800 |

## Limiti

- `taskCompletionModel = NOT_IMPLEMENTED`: le assegnazioni non dimostrano
  il completamento applicativo reale;
- il seed del workload non e' propagato separatamente secondo il mapping;
- il conteggio diretto `localRepairApplied` non e' serializzato nel freeze;
- le analisi causali specifiche sul repair sono demandate a G05;
- G02 costituisce la baseline FULL_MA_GA per il confronto G02B.

## Decisione

**PASS_G02_COMPLETE_READY_FOR_G03_RUNS**
