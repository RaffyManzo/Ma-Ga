# Audit di allineamento matrici dopo G04-R

## Matrice completa

File: `Matrice_test_MA_GA_MOSAIC_SUMO_G04R_allineata.xlsx`

Aggiornamenti principali:

- titolo e stato campagna aggiornati a G04-R;
- sequenza operativa aggiornata: prima G07, bozza capitolo, handoff ridotto, G02B, G07 definitiva;
- aggiunta configurazione `CFG-G04-R-HANDOVER`;
- aggiunta run `G04-R-01`;
- T-096 e T-098 aggiornati a PASS;
- T-091 mantenuto `EVIDENZA_PARZIALE`;
- T-094 mantenuto `NON_OSSERVATO`;
- oracolo contestuale per il validator con workload statico;
- limite sul mapping `synthetic_* → veh_*`;
- checklist G04-R aggiunta;
- fonti e bundle G04-R aggiunti;
- foglio `17_Risultati_G04R` aggiunto;
- foglio G04 storico aggiornato senza riscrivere retroattivamente le run originali.

Numero atteso di fogli: 18.

## Matrice semplificata

File: `Matrice_semplificata_G04R_allineata.xlsx`

Aggiornamenti principali:

- dashboard aggiornata;
- sequenza G07/bozza/handoff/G02B/G07 finale aggiornata;
- riepilogo della configurazione G04-R aggiunto;
- storico aggiornato;
- nuovi stati di legenda aggiunti;
- riepilogo G04 aggiornato;
- foglio `09_Risultati_G04R` aggiunto.

Numero atteso di fogli: 10.

## Controlli

- nessun errore formula rilevato;
- nessun riferimento G04-R lasciato come `DA_ESEGUIRE`;
- nessuna modifica ai risultati storici G02, G03, G05 o G06;
- nessuna modifica Java;
- T-093 non ripetuto;
- il primo tentativo harness è distinto dalla run valida;
- il rigetto del validator è dichiarato come eccezione contestuale e non come PASS generico.
