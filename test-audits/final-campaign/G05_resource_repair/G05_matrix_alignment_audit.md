# Audit di allineamento delle matrici dopo G05

## File verificati

- `Matrice_test_MA_GA_MOSAIC_SUMO_G05_allineata.xlsx`
- `Matrice_semplificata_G05_allineata.xlsx`

## Matrice completa

Fogli totali: 16.

Aggiornamenti principali:

- `00_Guida`: stato corrente G05, sequenza futura e riepilogo finale;
- `02_Configurazioni`: verdetti delle sei configurazioni G05;
- `03_Piano_run`: run, log, validator e risultati G05;
- `04_Catalogo_test`: T-066, T-067, T-071–T-077 e T-091/T-093/T-094;
- `07_Checklist`: controlli C-048–C-054;
- `08_Limiti`: limiti L-018–L-021;
- `10_Risultati_G00_G01`: stato G05 e prossimo gruppo G06;
- `12_Risultati_G02`, `13_Risultati_G03`, `14_Risultati_G04`: riferimenti correnti riallineati;
- nuovo foglio `15_Risultati_G05`.

## Matrice semplificata

Fogli totali: 8.

Aggiornamenti principali:

- dashboard portata allo stato G05;
- riepilogo configurazioni esteso con le sei run G05;
- storico aggiornato;
- legenda estesa con gli stati G05;
- fogli G03 e G04 riallineati allo stato corrente;
- nuovo foglio `07_Risultati_G05`.

## Regole applicate

- nessun risultato stale è trattato come strategia applicata;
- `T-093` è `PASS_RECOVERED`;
- `T-091` resta parziale;
- `T-094` resta non osservato;
- repair/fallback resta non esposto;
- confronto G02/G05 limitato a metriche normalizzate;
- nessun completion rate calcolato;
- nessun valore SUMO nullo trasformato in zero;
- G06 indicato come prossimo gruppo.

## Verifiche

- nessun errore formula rilevato;
- nessun riferimento operativo ambiguo a “G05 da avviare”;
- foglio G05 presente in entrambe le matrici;
- SHA-256 matrice completa: `916d62801d135b50b1760da7d86ed648a11b9d6176189958f9f5d274d7aabdd2`;
- SHA-256 matrice semplificata: `f4cead82aa57f3443f13ff618b4df0273039d4f1b99e404a804554412e1a257f`.
