# Data layout

La cartella contiene solo input e documentazione di progetto. I risultati di esecuzione restano fuori da `data/` nelle cartelle `validation_reports/` e `validation_results/`.

## Struttura

- `docs/`: documentazione tecnica del sistema MA-GA.
- `snapshots/maga/examples/`: snapshot piccoli per prove manuali del MA-GA.
- `snapshots/maga/stress/`: snapshot statici grandi usati come scenario di stress MA-GA.
- `snapshots/window/examples/`: sequenze minime per il ciclo temporale.
- `snapshots/window/stress/static_baseline/`: stress test statico su finestre consecutive.
- `snapshots/window/stress/urban_moderate/`: stress test temporale completo su scenario urbano moderato.
- `snapshots/window/stress/realistic_scenarios/`: scenari realistici calibrati.
- `snapshots/window/validation/`: scenari comparativi per la validazione.

I path usati dal codice sono centralizzati in `src/io/snapshot/SnapshotPaths.java`.
