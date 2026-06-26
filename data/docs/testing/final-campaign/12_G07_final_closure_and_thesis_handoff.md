# 12 — Chiusura G07 finale e handoff alle conclusioni della tesi

## Stato finale

Il G07 finale consolida G00–G06, G04-R e G02B senza eseguire nuove simulazioni. Lo stato assegnato è `PASS_FINAL_READY_FOR_THESIS_CONCLUSIONS`.

Il branch operativo è `experiment/g02b-ablation`. Il checkpoint di partenza del G07 finale è `0d12707e0ba65194055d936c532a5615bf1169e2`; il branch stabile `testing/final-campaign` resta al commit `790c7cf29d72e6e9b9b2717a48f4f4e44a28ad57`.

## Materiale prodotto

- `Matrice_test_MA_GA_MOSAIC_SUMO_G07_finale.xlsx`;
- `Matrice_semplificata_G07_finale.xlsx`;
- `07_metodologia_risultati_G07_finale.tex`;
- audit formale G07;
- decisione finale T-130–T-135;
- registro dei limiti e degli artefatti.

## Scope verificato

Il corpus G07-A comprende 303 file strutturati, 210 documenti JSON, 982 record JSONL e 33.283 righe CSV. Il bundle canonico G02B aggiunge 254 file strutturati: 201 JSON e 53 CSV con 31.057 righe dati. Lo scope composito usato dal G07 finale contiene quindi 557 file strutturati, 411 JSON, 982 record JSONL e 64.340 righe CSV.

Gli otto bundle conclusivi considerati risultano leggibili e coerenti con i rispettivi manifest. Il G07 finale non modifica Java, non cambia risultati scientifici e non avvia MOSAIC/SUMO.

## Decisioni T-130–T-135

| Test | Stato finale |
| --- | --- |
| T-130 | `PASS_FINAL_CROSS_PHASE` |
| T-131 | `PASS_FINAL_AUTHORITATIVE_CORPUS` |
| T-132 | `PASS_FINAL_COUNTER_AND_PAIRING_COHERENCE` |
| T-133 | `PASS_FINAL_COMPLETION_MODEL_DECLARED` |
| T-134 | `PASS_FINAL_PROVENANCE_LEDGER` |
| T-135 | `PASS_FINAL_NUMERIC_AND_THESIS_INTEGRITY` |

## Regole per il capitolo conclusivo

Le conclusioni possono sostenere la correttezza tecnica della pipeline, la riproducibilità degli input, l'osservabilità della mobilità e dello stale handling e l'effetto dipendente dal carico delle componenti analizzate in G02B.

Non devono sostenere:

- ottimalità del MA-GA;
- superiorità universale rispetto alle varianti;
- significatività statistica;
- completamento applicativo dei task;
- stabilità generale del profilo `high_density` fino a 600 secondi.

## Passo successivo

Il file `.tex` deve essere integrato nel progetto LaTeX autorevole della tesi e ricompilato nel contesto completo. Dopo questa integrazione è possibile scrivere il capitolo delle conclusioni, mantenendo i limiti riportati nel G07 finale.
