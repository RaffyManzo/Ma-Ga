# Telemetria e chiusura della correzione della CPU locale

## Obiettivo

La correzione del core rende cumulativo il completion time delle porzioni
locali. Prima del freeze finale, il runtime live deve rendere questa dinamica
misurabile senza ricostruzioni esterne.

## Metriche per gene

Il record JSONL di ogni finestra applicata espone:

- tempo locale indipendente;
- completion time locale dopo la contesa EDF;
- ritardo prodotto dalla contesa;
- completion time complessivo e rispetto della deadline.

## Metriche per finestra

Per ogni finestra vengono registrati:

- porzioni locali;
- veicoli con workload locale;
- veicoli con contesa;
- veicoli con overflow CPU;
- violazioni locali delle deadline;
- massimo tempo locale indipendente;
- massimo completion time locale conteso;
- massimo ritardo da contesa;
- massimo rapporto di domanda;
- massimo overflow locale.

## Aggregazione della run

Il report finale somma i conteggi sulle finestre applicate e mantiene i massimi
osservati. I nomi `vehicle-window` indicano che lo stesso veicolo può essere
conteggiato in più finestre.

## Validazione prima del freeze

Il freeze richiede:

1. build pulita del runtime;
2. harness del core e del repair;
3. harness della telemetria;
4. regressioni G02B, live-state e reporting;
5. quattro run live mirate;
6. invariante `LOCAL_ONLY`;
7. presenza di offloading nelle configurazioni non forzate;
8. valori finiti e relazione
   `contendedLocalCompletionTimeSeconds >= independentLocalExecutionTimeSeconds`;
9. working tree pulito dopo i commit;
10. remoto e tag di freeze allineati.

La preparazione della nuova campagna sperimentale non fa parte di questa fase.
