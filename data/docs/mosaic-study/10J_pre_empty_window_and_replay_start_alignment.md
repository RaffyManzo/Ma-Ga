# Fase 10J-pre - Finestre vuote e avvio del replay JSON_TIME

## 1. Contesto

La Fase 10I ha generato snapshot JSON finali sotto:

```text
data/snapshots/mosaic-generated/
```

Il primo file, `snapshot_000_t_005.json`, rappresenta intenzionalmente la prima finestra della timeline diagnostica:

```text
timeSeconds = 5
tasks = []
vehicles = []
candidateNodes = []
accessLinks = []
```

Questo snapshot e' valido: una finestra temporale puo' non contenere task da ottimizzare.

## 2. Problema JSON_SEQUENCE

Il replay sequenziale arrivava allo snapshot vuoto e falliva prima di raggiungere il ramo gia' esistente:

```text
StopReason.EMPTY_TASK_SET
```

La causa era in `MaGaOptimizer.validateSnapshot(...)`: il metodo richiedeva sempre almeno un candidato, anche quando `tasks` era vuoto.

## 3. Fix EMPTY_TASK_SET

La validazione e' stata riallineata cosi':

```text
tasks == null
    -> errore

candidateNodes == null
    -> errore

tasks vuoto
    -> candidateNodes puo' essere vuoto
    -> optimizeDetailed(...) restituisce EMPTY_TASK_SET

tasks non vuoto e candidateNodes vuoto
    -> errore
```

Non vengono inventati candidati, placeholder LOCAL o pool fittizi. Il GA non viene avviato per snapshot senza task.

Risultato atteso:

```text
StopReason.EMPTY_TASK_SET
fitness = 0.0
generationsExecuted = 0
```

## 4. Problema JSON_TIME

Il replay temporale partiva da `0.0 s`, mentre la cartella MOSAIC generata contiene come primo snapshot:

```text
5.0 s
```

`TimeIndexedSnapshotReplaySource` si comportava correttamente: per una richiesta a `0.0 s` non esponeva uno snapshot futuro e restituiva `Optional.empty()`.

Il problema era quindi nel bootstrap di `AdaptiveWindowMain`, non nella sorgente temporale.

## 5. Fix replayStartTimeSeconds

`AdaptiveWindowMain` ora valida che la lista caricata non sia vuota e avvia il manager dal primo timestamp realmente disponibile:

```text
replayStartTimeSeconds = snapshots.get(0).getTimeSeconds()
manager.run(replayStartTimeSeconds, maxSteps)
```

La semantica no-look-ahead resta invariata:

```text
richiesta 0.0 s
    -> Optional.empty()

richiesta 5.0 s
    -> snapshot 5.0 s
```

Non sono stati modificati `TimeIndexedSnapshotReplaySource`, `SequentialSnapshotReplaySource` o `TemporalWindowManager`.

## 6. File modificati

```text
src/ga/core/MaGaOptimizer.java
src/app/AdaptiveWindowMain.java
```

File ispezionati ma lasciati invariati:

```text
src/ga/core/MaGaResult.java
src/ga/core/StopReason.java
src/window/core/TemporalWindowManager.java
src/window/source/TimeIndexedSnapshotReplaySource.java
src/window/source/SequentialSnapshotReplaySource.java
src/window/source/SystemStateSourceFactory.java
src/io/snapshot/JsonSnapshotFolderLoader.java
```

## 7. Test introdotti

E' stato creato un harness esterno:

```text
tools/replay-bootstrap-validation/
```

Il tool compila il progetto intero e valida:

```text
A: optimizer con snapshot vuoto -> EMPTY_TASK_SET
B: task non vuoto senza candidati -> IllegalArgumentException
C: JSON_TIME richiesta 0 s prima dello snapshot 5 s -> Optional.empty()
D: JSON_TIME richiesta 5 s -> snapshot 5 s exactTimeMatch=true
E: JSON_SEQUENCE preserva la finestra vuota iniziale
F: smoke JSON_SEQUENCE con maxSteps=2
G: smoke JSON_TIME con maxSteps=2
```

## 8. Risultati

```text
testsExecuted = 7
testsPassed = 7
testsFailed = 0
jsonSequenceSmokeStepsExecuted = 2
jsonSequenceSmokeStatus = PASS
jsonTimeSmokeStepsExecuted = 2
jsonTimeSmokeStatus = PASS
futureLookAheadViolations = 0
phase10jPreStatus = COMPLETED
readyForPhase10J = true
```

Lo smoke `JSON_SEQUENCE` osserva:

```text
snapshot_000_t_005 -> EMPTY_TASK_SET
snapshot_001_t_010 -> prima finestra con task
```

Lo smoke `JSON_TIME` osserva:

```text
triggerTime = 5.0 s
sourceTime = 5.0 s
exactMatch = true
futureLookAhead = false
```

## 9. Limiti

Questa sottofase non valida ancora l'intera Fase 10J. Non viene modificato il numero totale di snapshot da processare, non viene modificata la semantica di `maxSteps` e non viene eseguito un replay completo su tutte le finestre.

## 10. Readiness per la Fase 10J

La Fase 10J puo' iniziare perche':

```text
lo snapshot vuoto iniziale e' gestito come EMPTY_TASK_SET
il replay sequenziale non si blocca sulla finestra vuota
il replay temporale parte dal primo timestamp JSON disponibile
la sorgente time-indexed non espone snapshot futuri
gli smoke test JSON_SEQUENCE e JSON_TIME passano
```

Attivita' escluse:

```text
replay completo JSON_SEQUENCE
replay completo JSON_TIME
validazione GA completa su tutte le finestre
bridge live
```
