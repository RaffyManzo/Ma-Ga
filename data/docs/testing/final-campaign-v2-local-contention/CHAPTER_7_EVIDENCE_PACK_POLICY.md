# Politica del pacchetto evidenze per il capitolo 7

Alla chiusura di ogni gruppo GXX devono essere conservati:

- audit narrativo `GXX_AUDIT_SUMMARY_FOR_RESULTS.md`;
- file di chiusura `GXX_CLOSURE.md`;
- metriche per run in CSV/JSON;
- aggregati necessari alle tabelle;
- criteri di validita' e gate;
- anomalie, risultati inattesi e limiti;
- mapping config-seed-scenario;
- copertura dei test ID;
- inventario con SHA-256;
- commit e stato Git;
- indice che collega ogni affermazione ai file sorgente.

Il solo Markdown non e' sufficiente. Il solo ZIP con log grezzi non e'
sufficiente. La combinazione corretta e':

1. sintesi narrativa;
2. dati strutturati;
3. contesto sperimentale;
4. tracciabilita';
5. limiti;
6. stato Git.

Alla chiusura di G07 verra' creato un pacchetto cumulativo
`CAPITOLO_7_EVIDENCE_PACK` con gli audit G00-G07, G02B, matrici finali,
aggregati cross-group, confronto legacy/V2 e handoff editoriale.
