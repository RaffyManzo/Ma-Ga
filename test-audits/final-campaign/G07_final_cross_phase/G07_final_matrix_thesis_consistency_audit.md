# Audit di coerenza tra matrici, risultati e capitolo 7

## Matrici

Le matrici definitive derivano dai file post-G02B e conservano tutti i fogli storici. Sono stati aggiunti:

- foglio completo `20_Risultati_G07`;
- foglio semplificato `12_Risultati_G07`;
- checklist C-088–C-095;
- limiti L-048–L-052;
- fonti F-036–F-040;
- verdetti finali T-130–T-135.

Non sono state eliminate righe storiche. La scansione delle formule non ha rilevato errori.

## Capitolo 7

Il file `07_metodologia_risultati_G07_finale.tex`:

- aggiunge la domanda sperimentale di ablation;
- distingue la campagna stabile dal branch sperimentale;
- descrive pairing, configurazioni, seed e varianti;
- inserisce una tabella delle varianti e una tabella dei delta paired;
- aggiorna audit, discussione e conclusioni;
- mantiene `NOT_IMPLEMENTED`, high density e assenza di significatività tra i limiti;
- non confronta la fitness totale di `NO_MOBILITY_PENALTY`.

Il frammento è stato compilato in isolamento con `pdflatex`. Non sono presenti errori bloccanti, label duplicate o sbilanciamenti delle parentesi graffe.

## Decisione

`PASS_FINAL_MATRIX_THESIS_ALIGNMENT`.
