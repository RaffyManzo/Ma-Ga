# Recupero isolamento G00

## Incidente

Il primo macro-batch ha completato 69 materializzazioni ma le modalita
repair-canonical-metadata e repair-bandwidth-serialization della
copia V2 contenevano ancora componenti di percorso final-campaign.

## Correzione

- ripristino dei percorsi legacy dal tooling commit;
- rimozione del solo scenario-convert.log non tracciato;
- sostituzione di 14 componenti hardcoded nella copia V2;
- riesecuzione dei repair e dell'audit sui percorsi V2;
- nessuna nuova materializzazione;
- nessuna run MOSAIC;
- nessuna modifica Java o core.

## Evidenze

- materializzazioni riutilizzate: 69;
- validate: 63;
- warning ammessi: 6;
- fallite: 0;
- bloccate: 0.
