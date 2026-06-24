# G05 - Piano di preflight ed esecuzione

Stato corrente: `G05_PREFLIGHT_REVIEW_REQUIRED`.

G05 verifica risorse, saturazione, repair e fallback senza modificare il core Java congelato.

## Sequenza controllata

1. inventario delle leve esterne versionate;
2. revisione manuale dei match e dei flag;
3. costruzione di una configurazione per volta;
4. dry-run e validazione senza MOSAIC, quando possibile;
5. esecuzione della singola run;
6. verifica del result JSON e delle diagnostiche;
7. decisione prima della configurazione successiva.

## Run previste

- R-LOCALCPU-01;
- R-EDGECPU-01;
- R-CLOUDCPU-01;
- R-CELLBW-01;
- R-V2VBW-01;
- R-RTT-01.

Seed comune: 104729. Durata: 180 s. High density resta uno stress profile.

## Vincoli

- nessuna modifica Java senza discussione preventiva;
- nessuna simulazione automatica in questa sottofase;
- nessun task rimosso alla deadline va interpretato come completato;
- nessun valore SUMO null va trasformato in zero;
- una run tecnica PASS non implica che il fenomeno atteso sia stato osservato.
