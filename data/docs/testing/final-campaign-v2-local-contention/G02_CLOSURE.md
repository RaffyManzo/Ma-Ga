# Chiusura G02 - Campagna MA-GA V2

- verdetto: **PASS_G02_COMPLETE_READY_FOR_G03_RUNS**;
- run valide: 45/45;
- configurazioni: 9;
- seed per configurazione: 5;
- validator PASS: 45/45;
- risultati nulli: 0;
- job GA falliti: 0;
- violazioni: 0;
- run con contesa osservata: 34;
- gate corretto: PASS_G02_SCIENTIFIC_GATE;
- run G03 eseguite: 0;
- JAR SHA-256: 3821c28a6a0898e94f4d5136b8039595d36bcbce0b6ecc3e9406910f9e083068;
- commit: `test(campaign-v2): close G02 main factorial experiments`.

Il precedente fallimento del gate era dovuto a un criterio non valido:
imponeva monotonia a una metrica condizionata dalle decisioni della strategia.
Le 45 run non sono state ripetute.
