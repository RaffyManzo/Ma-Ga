# Chiusura formale G02 e handoff decisionale G02B

## G02 principale

- 45/45 run PASS;
- nove configurazioni, cinque seed ciascuna;
- validator errors e warnings pari a zero;
- snapshot lag massimo pari a zero;
- violazioni runtime pari a zero;
- nessuna modifica Java rispetto al freeze;
- `taskCompletionModel = NOT_IMPLEMENTED`.

## Artefatti ufficiali

- audit e manifest G02;
- metriche per run e per configurazione;
- 45 riepiloghi JSON compatti;
- matrice completa aggiornata foglio per foglio;
- matrice semplificata aggiornata;
- audit G02B revisionato.

## G02B

Stato: **G02B_DECISION_REQUIRED**.

Le comparative non sono state avviate. Nessuna variante obbligatoria è esposta completamente
tramite configurazione o tooling esterno.

La baseline FULL MA-GA esistente deve essere riutilizzata qualora venga autorizzato un futuro
branch sperimentale.

## Decisione successiva

- preservare il freeze e registrare G02B come non eseguibile sotto tale vincolo; oppure
- autorizzare la progettazione di modifiche Java minime su un branch sperimentale dedicato.

Nessuna modifica al core deve essere applicata senza una fase di confronto sulle classi coinvolte,
sul comportamento degli switch e sugli effetti metodologici.
