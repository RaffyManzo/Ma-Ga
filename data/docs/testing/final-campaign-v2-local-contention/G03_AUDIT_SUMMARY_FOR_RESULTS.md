# Audit riepilogativo G03 per il capitolo dei risultati

## Identificazione

- gruppo: G03 - riproducibilità e durata;
- parent: `0a0d82cbb62bcdb71d64fe5fd247cfa06d400582`;
- materializzazioni replicate: 2;
- run runtime repeatability: 3 da 300 s;
- run nominal extended: 5 da 600 s;
- run high-density stress: 1 da 600 s;
- JAR SHA-256: `3821c28a6a0898e94f4d5136b8039595d36bcbce0b6ecc3e9406910f9e083068`.

## Risultati validati

- confronto delle materializzazioni:
  `LOGICALLY_IDENTICAL`, differenze logiche 0;
- contatori funzionali identici nelle tre repliche runtime;
- 9/9 simulazioni completate;
- 9/9 validator `LITERATURE_SMOKE_TEST_PASSED`;
- lag massimo degli snapshot: 0 s;
- violazioni runtime: 0;
- contabilizzazione asincrona coerente in ogni run;
- prova high-density/WL-S completata per 600 s.

## Limite di cadenza del runtime

La durata del simulatore è stata completata, ma il MA-GA non ha continuato
ad applicare nuove strategie lungo tutta la timeline simulata.

Nelle tre run da 300 s l'ultima strategia è stata applicata tra
9.4 e
9.6 s.
Il tratto finale senza una nuova strategia è quindi compreso tra
290.4 e
290.6 s.

Nelle cinque run nominali da 600 s l'ultima strategia è stata applicata tra
9.8 e
28.0 s.
Il tratto finale senza nuovi aggiornamenti è compreso tra
572.0 e
590.2 s.

La prova stress completa i 600 s, ma l'ultima strategia è applicata a
6.9 s.

Questo risultato non invalida la materializzazione, il workload, la causalità
degli snapshot o la stabilità di MOSAIC. Mostra però che il worker asincrono
non mantiene la cadenza decisionale richiesta dopo l'introduzione della
contesa della CPU locale.

## Riproducibilità

I contatori del workload e degli snapshot sono identici nelle tre repliche.
Il runtime wall-clock non è invece deterministico: il coefficiente di variazione
delle medie del GA è pari a
66.333239%.

La riproducibilità deve quindi essere attribuita allo scenario e ai contatori
funzionali, non al tempo di esecuzione wall-clock.

## Limiti da mantenere nella tesi

- `taskCompletionModel = NOT_IMPLEMENTED`;
- completamento della simulazione non equivale a copertura decisionale;
- `SHUTDOWN_IN_FLIGHT` non è un fallimento, se la contabilità è coerente;
- la causa della perdita di cadenza deve essere approfondita in G06;
- nessuna correzione Java viene introdotta durante la campagna congelata.

## Decisione

**PASS_G03_COMPLETE_WITH_RUNTIME_CADENCE_LIMIT_READY_FOR_G04**
