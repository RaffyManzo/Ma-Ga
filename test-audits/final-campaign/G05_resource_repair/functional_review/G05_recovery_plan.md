# G05 - Piano di recupero dopo la revisione funzionale

## Decisione principale

Eseguire una verifica selettiva di R-LOCALCPU-01 senza ripetere le altre cinque run.

## Obiettivi del recupero R-LOCALCPU

1. usare lo stesso scenario materializzato, lo stesso seed e lo stesso JAR;
2. eseguire R-LOCALCPU in isolamento rispetto al batch;
3. confrontare applicazioni strategiche, runtime GA, stale ratio e ultimo apply;
4. stabilire se il blocco dopo 7.7 s e riproducibile o legato all esecuzione iniziale;
5. mantenere separati risultato tecnico e risultato funzionale.

## Run che non si ripetono ora

- R-EDGECPU-01: l assenza di EDGE applicato e un risultato di evitamento della risorsa degradata;
- R-CLOUDCPU-01: l assenza di CLOUD applicato e un risultato di evitamento della risorsa degradata;
- R-CELLBW-01: la banda CELL non e esercitata da scelte remote applicate;
- R-RTT-01: la run e stabile ma non esercita EDGE/CLOUD;
- R-V2VBW-01: il limite high_density e gia chiaramente osservato.

## Dopo il recupero

Confrontare le run G05 con una baseline compatibile, produrre tabelle per la tesi e decidere se T-091 e T-094 richiedono run mirate soltanto dopo G06.

## Vincoli

- nessuna modifica Java;
- nessuna modifica della baseline congelata;
- nessun aggiornamento delle matrici prima della decisione finale G05;
- nessuna interpretazione dei risultati stale come strategie applicate.
