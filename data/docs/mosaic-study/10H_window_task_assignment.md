# Fase 10H - Assegnazione diagnostica dei task alle finestre MA-GA

## Scopo della fase

La Fase 10H aggiunge alla pipeline offline MOSAIC -> MA-GA un passaggio esplicito di assegnazione dei task alle finestre temporali MA-GA.

La fase non costruisce `SystemSnapshot` JSON, non invoca il core MA-GA e non modifica `TemporalWindowManager`. Produce soltanto artefatti CSV e JSON verificabili che saranno letti dalla futura Fase 10I.

## Relazione con la pipeline 10A-10G

La pipeline gia' validata produce:

```text
task_stream.csv
vehicle_state_stream.csv
infrastructure_snapshot.json
cell_handover_stream.csv
cell_bandwidth_stream.csv
access_link_preview.csv
remote_candidate_preview.csv
local_candidate_preview.csv
v2v_candidate_preview.csv
v2v_bandwidth_pool_preview.csv
```

La 10H usa soltanto:

```text
task_stream.csv
optimization_window_timeline.csv
integrated_baseline_metadata.json
```

e produce:

```text
window_task_assignment.csv
diagnostics/phase_10h_validation.json
```

## Baseline canonica

La baseline canonica usata e':

```text
tmp/mosaic-25.2/logs/log-20260604-220216-MaGaIntegratedStudy/
```

Il metadata letto da:

```text
data/mosaic-study/diagnostics/cell/integrated_baseline_metadata.json
```

contiene:

```text
sourceRun = log-20260604-220216-MaGaIntegratedStudy
```

L'exporter 10H confronta questo valore con `--expected-source-run` e interrompe l'esecuzione se non coincide.

## Input

Input della fase:

```text
data/mosaic-study/task_stream.csv
data/mosaic-study/optimization_window_timeline.csv
data/mosaic-study/diagnostics/cell/integrated_baseline_metadata.json
```

Il task stream reale contiene:

```text
taskId
sourceVehicleId
profileId
activationTimeNs
activationTimeMs
inputSizeBits
outputSizeBits
cpuCycles
deadlineSeconds
```

Conteggi osservati:

```text
tasksRead = 682
duplicateTaskIdsInInput = 0
activationTimeCoherenceViolations = 0
firstTaskActivationTimeNs = 7000000000
lastTaskActivationTimeNs = 180000000000
```

## Output

Output canonici:

```text
data/mosaic-study/optimization_window_timeline.csv
data/mosaic-study/window_task_assignment.csv
data/mosaic-study/diagnostics/phase_10h_validation.json
```

Questi file sono input diagnostici per la futura 10I. Non sono snapshot finali.

## Motivazione della timeline esplicita

La timeline viene generata in un CSV separato per evitare che la durata delle finestre sia nascosta nello script di assegnazione.

La separazione tra:

```text
generate_fixed_optimization_window_timeline.py
export_window_task_assignment.py
```

e' intenzionale. In futuro `optimization_window_timeline.csv` potra' essere sostituito da una timeline adattiva prodotta dal `TemporalWindowManager` senza riscrivere l'algoritmo che associa i task alle finestre.

## Timeline FIXED_INTERVAL_DIAGNOSTIC

Per questa prima integrazione offline e' stata usata:

```text
timelinePolicy = FIXED_INTERVAL_DIAGNOSTIC
simulationStartSeconds = 0
simulationEndSeconds = 180
windowIntervalSeconds = 5
```

La timeline risultante contiene:

```text
5, 10, 15, ..., 180
```

per:

```text
windowsGenerated = 36
```

La timeline fissa da 5 secondi e' una configurazione diagnostica. Non rappresenta ancora la durata adattiva delle finestre prevista dalla formalizzazione del MA-GA.

Stato di calibrazione:

```text
calibrationStatus =
    TO_BE_REPLACED_OR_DRIVEN_BY_TEMPORAL_WINDOW_MANAGER
```

## Policy di assegnazione

La policy implementata e':

```text
taskAssignmentPolicy =
    PENDING_TASKS_AT_NEXT_OPTIMIZATION_WINDOW
```

Un task attivato nell'intervallo:

```text
(t_previous, t_current]
```

viene assegnato alla finestra:

```text
t_current
```

Per la prima finestra la regola e':

```text
[0, 5]
```

Per le successive:

```text
(5, 10]
(10, 15]
...
(175, 180]
```

Un task esattamente sul confine entra nella finestra con lo stesso timestamp.

## Policy di consumo

La policy di consumo diagnostica e':

```text
consumptionPolicy =
    REMOVE_AFTER_EXPORT_TO_WINDOW
```

Significa che ogni task viene esportato verso una sola finestra e non viene ripetuto nelle finestre successive. Questo non simula il completamento reale del task e non decide migrazioni o persistenza remota.

## Sorgente temporale primaria

L'assegnazione usa:

```text
activationTimeNs
```

come sorgente temporale primaria. Gli argomenti CLI in secondi e i secondi della timeline vengono convertiti in nanosecondi interi. I confronti operativi non usano floating point.

## Schema optimization_window_timeline.csv

Colonne:

```text
windowIndex
previousWindowTimeNs
previousWindowTimeSeconds
windowTimeNs
windowTimeSeconds
intervalStartPolicy
intervalEndPolicy
timelinePolicy
```

La prima riga usa:

```text
intervalStartPolicy = INCLUSIVE
```

Le righe successive usano:

```text
intervalStartPolicy = EXCLUSIVE
```

Tutte le righe usano:

```text
intervalEndPolicy = INCLUSIVE
timelinePolicy = FIXED_INTERVAL_DIAGNOSTIC
```

## Schema window_task_assignment.csv

Colonne:

```text
windowIndex
previousWindowTimeNs
previousWindowTimeSeconds
windowTimeNs
windowTimeSeconds
assignmentDelayNs
assignmentDelaySeconds
taskId
sourceVehicleId
profileId
activationTimeNs
activationTimeMs
inputSizeBits
outputSizeBits
cpuCycles
deadlineSeconds
taskAssignmentPolicy
consumptionPolicy
```

Le righe sono ordinate in modo deterministico per:

```text
windowIndex
activationTimeNs
sourceVehicleId
profileId
taskId
```

## Schema phase_10h_validation.json

Il JSON diagnostico include:

```text
sourceRun
timelinePolicy
taskAssignmentPolicy
consumptionPolicy
boundaryPolicy
simulationStartSeconds
simulationEndSeconds
windowIntervalSeconds
windowsGenerated
tasksRead
tasksAssigned
uniqueTaskIdsRead
uniqueTaskIdsAssigned
duplicateTaskIdsInInput
duplicateAssignments
tasksLost
tasksAssignedBeforeActivation
tasksAssignedToNonEarliestWindow
tasksAtExactBoundary
tasksAtZero
tasksAfterSimulationEnd
negativeActivationTimes
emptyWindows
emptyWindowDetails
firstTaskActivationTimeNs
lastTaskActivationTimeNs
minimumAssignmentDelayNs
maximumAssignmentDelayNs
averageAssignmentDelayNs
tasksPerWindow
warnings
errors
phase10hStatus
readyForPhase10I
```

Policy dei confini:

```text
boundaryPolicy =
    INITIAL_INTERVAL_CLOSED_THEN_LEFT_OPEN_RIGHT_CLOSED
```

## Comandi di esecuzione

Generazione timeline:

```powershell
py .\tools\mosaic-offline-exporter\generate_fixed_optimization_window_timeline.py `
  --simulation-start-seconds 0 `
  --simulation-end-seconds 180 `
  --window-interval-seconds 5 `
  --output-file ".\data\mosaic-study\optimization_window_timeline.csv"
```

Assegnazione task:

```powershell
py .\tools\mosaic-offline-exporter\export_window_task_assignment.py `
  --task-stream-file ".\data\mosaic-study\task_stream.csv" `
  --timeline-file ".\data\mosaic-study\optimization_window_timeline.csv" `
  --baseline-metadata-file ".\data\mosaic-study\diagnostics\cell\integrated_baseline_metadata.json" `
  --expected-source-run "log-20260604-220216-MaGaIntegratedStudy" `
  --output-file ".\data\mosaic-study\window_task_assignment.csv" `
  --validation-out-file ".\data\mosaic-study\diagnostics\phase_10h_validation.json"
```

## Conteggi osservati

Risultati:

```text
windowsGenerated = 36
tasksRead = 682
tasksAssigned = 682
uniqueTaskIdsRead = 682
uniqueTaskIdsAssigned = 682
duplicateTaskIdsInInput = 0
duplicateAssignments = 0
tasksLost = 0
tasksAssignedBeforeActivation = 0
tasksAssignedToNonEarliestWindow = 0
tasksAfterSimulationEnd = 0
negativeActivationTimes = 0
```

## Distribuzione task per finestra

```text
1@5s=0
2@10s=3
3@15s=5
4@20s=8
5@25s=10
6@30s=12
7@35s=13
8@40s=19
9@45s=18
10@50s=21
11@55s=22
12@60s=23
13@65s=20
14@70s=24
15@75s=21
16@80s=22
17@85s=22
18@90s=23
19@95s=20
20@100s=24
21@105s=21
22@110s=22
23@115s=22
24@120s=23
25@125s=20
26@130s=24
27@135s=21
28@140s=22
29@145s=22
30@150s=23
31@155s=20
32@160s=24
33@165s=21
34@170s=22
35@175s=22
36@180s=23
```

## Task sui confini

Task attivati esattamente su un confine della timeline:

```text
tasksAtExactBoundary = 117
tasksAtZero = 0
```

I task a `180 s` sono assegnati alla finestra `180 s`.

## Finestre vuote

Finestre vuote:

```text
emptyWindows = 1
windowIndex = 1
windowTimeSeconds = 5
```

La finestra a 5 secondi e' vuota perche' il primo task MOSAIC osservato e' attivato a 7 secondi. Le finestre vuote non sono errori.

## Assignment delay

Ritardi diagnostici osservati:

```text
minimumAssignmentDelayNs = 0
maximumAssignmentDelayNs = 4000000000
averageAssignmentDelayNs = 2127565982.4046922
```

Il ritardo e':

```text
windowTimeNs - activationTimeNs
```

## Validazioni superate

La validazione conferma:

```text
ogni taskId compare una sola volta in input
ogni task viene assegnato esattamente una volta
nessun task viene perso
nessun task viene duplicato
nessun task viene assegnato prima della propria attivazione
ogni task viene assegnato alla prima finestra valida
activationTimeNs == activationTimeMs * 1_000_000 per tutti i task
la timeline e' strettamente crescente
la timeline non contiene duplicati
ogni finestra ha previousWindowTimeNs < windowTimeNs
nessun task oltre la fine della simulazione
nessun task con timestamp negativo
nessun errore bloccante
```

Esito:

```text
phase10hStatus = COMPLETED
readyForPhase10I = true
warnings = []
errors = []
```

## Limiti della fase

La 10H resta una fase diagnostica offline:

```text
non costruisce SystemSnapshot JSON
non invoca il core MA-GA
non modifica TemporalWindowManager
non implementa replay JSON_SEQUENCE
non implementa replay JSON_TIME
non implementa bridge live
non simula completamento o persistenza dei task remoti
non implementa migrazione task
```

## Compatibilita' futura con TemporalWindowManager

La timeline fissa da 5 secondi potra' essere sostituita da una timeline adattiva generata dal `TemporalWindowManager`. L'exporter di assegnazione lavora su `optimization_window_timeline.csv`, quindi non dipende dalla strategia che produce la timeline.

## Readiness per la Fase 10I

Il sistema e' pronto per la Fase 10I perche':

```text
sourceRun coincide con la baseline canonica
timeline esplicita generata
task_stream.csv letto e validato
tutti i task assegnati esattamente una volta
nessun task perso o duplicato
nessun future task oltre 180 s
nessuna assegnazione prima dell'attivazione
phase10hStatus = COMPLETED
readyForPhase10I = true
```

La 10I potra' usare `window_task_assignment.csv` insieme agli stream e alle preview 10B-10G per assemblare snapshot temporali completi.

## Attivita' escluse dalla 10H

Restano escluse:

```text
Fase 10I
SystemSnapshot JSON
Fase 10J
replay JSON_SEQUENCE
replay JSON_TIME
bridge live
interazioni custom MOSAIC
timeline adattiva live
migrazione task remoti
persistenza dell'esecuzione remota
modifiche al core MA-GA
```
