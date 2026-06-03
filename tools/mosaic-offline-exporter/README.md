# MOSAIC Offline Exporter - Fase 10A

Questo folder contiene il primo frammento dell'exporter offline per lo studio Eclipse MOSAIC -> MA-GA.

La Fase 10A implementa solo l'aggregazione del workload diagnostico generato da:

```text
org.eclipse.mosaic.app.maga.workload.MaGaWorkloadDiagnosticApp
```

Lo script legge i log applicativi MOSAIC e produce uno stream CSV di task attivati, utile per il futuro exporter di snapshot.

## Script

```text
export_task_stream.py
```

Lo script usa solo la standard library Python.

## Input

Root dei log applicativi della run diagnostica:

```text
tmp/mosaic-25.2/logs/log-20260603-123856-MaGaWorkloadStudy/apps/
```

Lo script cerca ricorsivamente file chiamati:

```text
MaGaWorkloadDiagnosticApp.log
```

ed estrae i payload:

```text
TASK_ACTIVATION|...
```

I log MOSAIC possono avere prefissi di timestamp/logger prima del payload; lo script valida il payload `TASK_ACTIVATION` estratto dalla riga.

## Output

CSV generato:

```text
data/mosaic-study/task_stream.csv
```

Colonne, in ordine:

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

La cartella di output viene creata automaticamente se non esiste.

## Esempio PowerShell

Dalla root della repository `maga-core/`:

```powershell
python .\tools\mosaic-offline-exporter\export_task_stream.py `
  --workload-log-root ".\tmp\mosaic-25.2\logs\log-20260603-123856-MaGaWorkloadStudy\apps" `
  --out-file ".\data\mosaic-study\task_stream.csv"
```

## Validazioni

Lo script fallisce con un messaggio leggibile se:

```text
la cartella input non esiste
non trova alcun MaGaWorkloadDiagnosticApp.log
non trova alcuna riga TASK_ACTIVATION
una riga TASK_ACTIVATION e' malformata
manca un campo obbligatorio
un campo appare due volte nella stessa riga
un valore numerico non e' valido
taskId e' vuoto
sourceVehicleId e' vuoto
profileId e' vuoto
esistono taskId duplicati
activationTimeNs < 0
activationTimeMs < 0
inputSizeBits <= 0
outputSizeBits < 0
cpuCycles <= 0
deadlineSeconds <= 0
```

Verifica inoltre:

```text
activationTimeNs == activationTimeMs * 1_000_000
taskId == <profileId>__<sourceVehicleId>__t_<activationTimeMs>
sourceVehicleId == nome della cartella veicolo che contiene il log
```

Esempio:

```text
apps/veh_0/MaGaWorkloadDiagnosticApp.log
    -> sourceVehicleId deve essere veh_0
```

Prima di scrivere il CSV, i record vengono ordinati deterministicamente per:

```text
activationTimeNs
sourceVehicleId
profileId
taskId
```

La scrittura e' sicura: lo script scrive prima un file temporaneo nella cartella di output e sostituisce il CSV finale solo dopo che tutte le validazioni sono state superate.

## Output diagnostico

A fine esecuzione lo script stampa:

```text
numero di file analizzati
numero di TASK_ACTIVATION trovati
numero di task esportati
numero di duplicati
distribuzione per profileId
distribuzione per sourceVehicleId
primo activationTimeNs
ultimo activationTimeNs
percorso del CSV generato
```

Per la run diagnostica `log-20260603-123856-MaGaWorkloadStudy` il risultato atteso e' `682` task. Questo valore non e' hard-coded nello script.

## Limiti della Fase 10A

Questa fase:

```text
non genera ancora SystemSnapshot
non assegna task alle finestre MA-GA
non invoca il core Java
non implementa il bridge live
non consuma i task dopo una decisione MA-GA
non legge dinamicamente la configurazione ma_ga_workload_config.json
```

Il CSV prodotto e' un artefatto intermedio per la futura Fase 10B dell'exporter offline.
