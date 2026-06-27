# Dati G07 per l'analisi condivisa dei risultati

Questo documento non è ancora il capitolo 7. È l'insieme ordinato dei dati
che verranno discussi prima della riscrittura.

## 1. Quadro generale

| Indicatore | Valore |
|---|---:|
| Run valide | 117 |
| Tempo simulato | 34860 s |
| Tempo simulato in ore | 9.68 |
| Task generati | 1223256 |
| Job GA inviati | 12718 |
| Strategie applicate | 12363 |
| Risultati stale | 267 |
| Job in-flight allo shutdown | 88 |

## 2. Run per gruppo

| Gruppo | Run | Secondi simulati | Task |
|---|---:|---:|---:|
| G01 | 1 | 180 | 101 |
| G02 | 45 | 13500 | 451449 |
| G03 | 9 | 4500 | 155854 |
| G04 | 5 | 900 | 32036 |
| G05 | 6 | 1080 | 37596 |
| G06 | 6 | 1200 | 37840 |
| G02B | 45 | 13500 | 508380 |

## 3. Causalità e snapshot

- righe bridge controllate: 211800;
- snapshot risolti: 210528;
- snapshot futuri: 0;
- lag massimo osservato: 0.0 s;
- future pool violations: 0;
- invalid pool bandwidth violations:
  0.

## 4. Lifecycle

### Veicoli

- snapshot pubblicati controllati:
  544;
- JOIN osservati: 55;
- UPDATE osservati: 1338;
- LEFT osservati: 0;
- cambi posizione: 1336;
- cambi velocità: 1337.

### Task

- generated: 1223256;
- activated: 1223256;
- removed at deadline:
  1214390;
- pending at end: 8866.

## 5. Fitness e reporting

- record temporali con fitness:
  10639;
- record con componenti mancanti:
  0;
- file JSON/JSONL/CSV parsati: 2133;
- errori di parsing: 0.

## 6. Risultati da discutere insieme

1. Il significato del limite di cadenza osservato in G03 e G06.
2. La differenza tra correttezza del sistema e tempestività della decisione.
3. Il forte effetto LOCAL_ONLY e la necessità di usare indicatori
   normalizzati.
4. L'effetto non conclusivo della penalità mobility-aware.
5. L'effetto medio ma non uniforme del riuso della popolazione.
6. Il valore scientifico delle assegnazioni in assenza del completion model.
7. Il modo corretto di presentare JOIN/UPDATE senza sostenere LEFT.
8. Quali grafici usare e quali conteggi assoluti evitare.

## 7. Regola per la futura scrittura

Per ogni risultato il capitolo dovrà distinguere:

`dato osservato -> interpretazione -> evidenza -> limite -> conclusione`
