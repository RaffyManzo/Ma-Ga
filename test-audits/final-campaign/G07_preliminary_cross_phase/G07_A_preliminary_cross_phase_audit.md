# G07-A - Audit trasversale preliminare

Stato: **PRELIMINARY_PASS_READY_FOR_FIRST_CHAPTER_DRAFT**

## Perimetro

L audit considera G00, G01, G02, G03, G04, G05, G06 e G04-R. G02B non Ã¨ stata eseguita e resta esclusa dalla prima bozza del capitolo.

Non sono state avviate nuove simulazioni. Il core Java e il JAR scientifico non sono stati modificati. Le matrici G04-R sono state verificate ma non aggiornate in questa sottofase.

## IntegritÃ  della repository

- Branch: $branch
- HEAD locale e remoto: $head
- Working tree iniziale: pulito
- Diff Java dal freeze $FrozenCoreCommit: vuoto
- Matrice completa: 18 fogli
- Matrice semplificata: 10 fogli

## Chiusure verificate

- G00: 69/69 materializzazioni concluse, con 63 validazioni e 6 warning metodologici.
- G01: pipeline end-to-end completata con recovery offline del solo post-processing.
- G02: 45/45 run fattoriali PASS.
- G03: riproducibilitÃ  e durata nominale estesa verificate; limite high-density a 464 s.
- G04: fase tecnica conclusa con limiti di osservabilitÃ .
- G05: sensibilitÃ  alle risorse conclusa con limite temporale riproducibile.
- G06: stale controllato e policy temporali auditati.
- G04-R: T-096 e T-098 recuperati con transizione controllata.

## Bundle finali

Sono stati verificati 7 bundle finali tramite nome, SHA-256 e lettura completa delle entry ZIP. Il dettaglio Ã¨ in G07_A_bundle_inventory.csv.

## IntegritÃ  del reporting

- File strutturati analizzati: 303
- Contenuti unici per SHA-256: 299
- Documenti JSON: 210
- Record JSONL: 982
- Righe CSV: 33283
- Errori di parsing: 0
- Valori NaN o Infinity: 0
- Insiemi di contatori verificati: 81
- Errori aritmetici hard: 0
- Dichiarazioni taskCompletionModel=NOT_IMPLEMENTED: 86

Gli eventuali casi in cui completed non Ã¨ interamente classificato come applied piÃ¹ stale sono conservati come warning interpretativi e non come contraddizioni, purchÃ© nessun conteggio superi submitted o completed.

## Verdetti reporting T-130--T-135

La G07 preliminare completa il controllo sul corpus autorevole corrente. T-131 e T-135 sono limitati al corpus versionato final-campaign; non includono output locali rigenerabili o futuri risultati G02B. T-134 Ã¨ sostenuto dal registro di provenienza e dovrÃ  essere ripetuto nella G07 definitiva dopo G02B.

## Conclusione

Il materiale corrente Ã¨ coerente e sufficiente per scrivere la prima bozza del capitolo sperimentale senza G02B. La bozza deve distinguere stabilitÃ  tecnica, evidenza funzionale, limiti di osservabilitÃ  e funzionalitÃ  non implementate.

La G07 definitiva resta differita fino al completamento e all integrazione di G02B.
