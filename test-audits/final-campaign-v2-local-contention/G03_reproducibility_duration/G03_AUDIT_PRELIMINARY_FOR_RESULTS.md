# Audit preliminare G03 - riproducibilità e durata

## Stato

- materializzazioni ripetute: 2/2 verificate;
- confronto logico: LOGICALLY_IDENTICAL;
- run runtime repeatability: 3/3 PASS;
- run nominal extended da 600 s: 5/5 PASS;
- prova high-density/WL-S: PASS;
- modifiche Java/core: nessuna;
- simulazioni G04: 0.

## T-010 e T-014

Le due materializzazioni con configurazione e seed uguali sono
logicamente identiche dopo l'esclusione delle sole differenze documentali
e identificative previste.

## Ripetibilità runtime

I contatori funzionali principali sono identici nelle tre esecuzioni.
Il coefficiente di variazione delle medie del runtime GA è
66.333239%. Le differenze wall-clock e asincrone non alterano
la riproducibilità dello stato simulato.

## Durata extended

Le cinque run nominali da 600 secondi sono tutte validate, senza lag
degli snapshot o violazioni del runtime.

## Stress opzionale

Stato: **PASS**.

Il profilo high-density/WL-S non è una configurazione operativa stabile.
Un eventuale arresto dopo l'avvio viene registrato come limite endurance,
non come regressione delle configurazioni nominali.

## Limite

`taskCompletionModel = NOT_IMPLEMENTED`: le strategie osservate non
dimostrano il completamento applicativo reale dei task.

## Prossimo passo

Il bundle deve essere sottoposto ad audit prima della chiusura formale
e del commit G03.
