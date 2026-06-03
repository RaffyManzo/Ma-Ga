# MOSAIC Offline Exporter

Questo folder contiene i primi frammenti dell'exporter offline per lo studio Eclipse MOSAIC -> MA-GA.

Gli script presenti sono diagnostici e usano solo la standard library Python. Non invocano il core Java MA-GA e non modificano gli scenari MOSAIC.

## Fase 10A - Aggregazione del workload diagnostico

La Fase 10A aggrega il workload diagnostico generato da:

```text
org.eclipse.mosaic.app.maga.workload.MaGaWorkloadDiagnosticApp
```

Script:

```text
export_task_stream.py
```

Lo script legge i log applicativi MOSAIC e produce uno stream CSV di task attivati, utile per il futuro exporter di snapshot.

### Input 10A

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

### Output 10A

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

### Esempio PowerShell 10A

Dalla root della repository `maga-core/`:

```powershell
python .\tools\mosaic-offline-exporter\export_task_stream.py `
  --workload-log-root ".\tmp\mosaic-25.2\logs\log-20260603-123856-MaGaWorkloadStudy\apps" `
  --out-file ".\data\mosaic-study\task_stream.csv"
```

Se `python` non e' nel `PATH`, su Windows e' possibile usare il launcher:

```powershell
py .\tools\mosaic-offline-exporter\export_task_stream.py `
  --workload-log-root ".\tmp\mosaic-25.2\logs\log-20260603-123856-MaGaWorkloadStudy\apps" `
  --out-file ".\data\mosaic-study\task_stream.csv"
```

### Validazioni 10A

Lo script fallisce con un messaggio leggibile se:

```text
la cartella input non esiste
non trova alcun MaGaWorkloadDiagnosticApp.log
non trova alcuna riga TASK_ACTIVATION
una riga TASK_ACTIVATION e' malformata
manca un campo obbligatorio
un campo appare due volte nella stessa riga
un valore numerico non e' valido
un valore numerico richiesto non e' finito
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

### Output diagnostico 10A

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

### Limiti 10A

Questa fase:

```text
non genera ancora SystemSnapshot
non assegna task alle finestre MA-GA
non invoca il core Java
non implementa il bridge live
non consuma i task dopo una decisione MA-GA
non legge dinamicamente la configurazione ma_ga_workload_config.json
```

Il CSV prodotto e' un artefatto intermedio per le fasi successive dell'exporter offline.

## Fase 10B - Normalizzazione dello stato dei veicoli

La Fase 10B normalizza lo stato osservato dei veicoli a partire da `output.csv`.

Script:

```text
export_vehicle_state_stream.py
```

Lo script legge `VEHICLE_REGISTRATION` e `VEHICLE_UPDATES` dalla run diagnostica, valida il ciclo di vita minimo dei veicoli e produce un CSV con una riga per ogni aggiornamento veicolare valido.

### Input 10B

File MOSAIC:

```text
tmp/mosaic-25.2/logs/log-20260603-123856-MaGaWorkloadStudy/output.csv
```

Eventi usati:

```text
VEHICLE_REGISTRATION
VEHICLE_UPDATES
```

Gli altri eventi vengono ignorati.

### Campi letti da VEHICLE_UPDATES

Per la Fase 10B vengono letti solo i primi 8 campi:

```text
indice 0 -> event marker
indice 1 -> timeNs
indice 2 -> vehicleId
indice 3 -> speed
indice 4 -> heading
indice 5 -> latitude
indice 6 -> longitude
indice 7 -> altitude
```

Non vengono attribuiti significati non verificati agli altri campi o ai booleani presenti nelle righe.

### Output 10B

CSV generato:

```text
data/mosaic-study/vehicle_state_stream.csv
```

Colonne, in ordine:

```text
timeNs
timeSeconds
vehicleId
latitude
longitude
projectedX
projectedY
speed
heading
active
```

Regole di output:

```text
active viene impostato a true per ogni VEHICLE_UPDATES esportato
projectedX resta vuoto
projectedY resta vuoto
timeSeconds = timeNs / 1_000_000_000
```

La cartella di output viene creata automaticamente se non esiste.

### Esempio PowerShell 10B

Dalla root della repository `maga-core/`:

```powershell
python .\tools\mosaic-offline-exporter\export_vehicle_state_stream.py `
  --input-file ".\tmp\mosaic-25.2\logs\log-20260603-123856-MaGaWorkloadStudy\output.csv" `
  --out-file ".\data\mosaic-study\vehicle_state_stream.csv"
```

Con il launcher Windows:

```powershell
py .\tools\mosaic-offline-exporter\export_vehicle_state_stream.py `
  --input-file ".\tmp\mosaic-25.2\logs\log-20260603-123856-MaGaWorkloadStudy\output.csv" `
  --out-file ".\data\mosaic-study\vehicle_state_stream.csv"
```

### Validazioni 10B

Lo script fallisce con un messaggio leggibile se:

```text
il file input non esiste
il percorso input non e' un file
non trova alcuna riga VEHICLE_REGISTRATION
non trova alcuna riga VEHICLE_UPDATES
una riga VEHICLE_REGISTRATION ha meno di 3 campi
una riga VEHICLE_UPDATES ha meno di 8 campi
timeNs non e' un intero valido
timeNs < 0
vehicleId e' vuoto
speed non e' numerico
heading non e' numerico
latitude non e' numerica
longitude non e' numerica
altitude non e' numerica
un valore numerico richiesto non e' finito
speed < 0
heading < 0
heading >= 360
latitude < -90
latitude > 90
longitude < -180
longitude > 180
un veicolo viene registrato piu' di una volta
un VEHICLE_UPDATES compare prima della registrazione del veicolo
lo stesso veicolo possiede piu' di uno stato allo stesso timeNs
il tempo degli eventi rilevanti diminuisce durante la lettura del file
```

Lo script non corregge automaticamente dati anomali, non normalizza l'heading e non elimina righe `VEHICLE_UPDATES` silenziosamente.

Prima di scrivere il CSV, gli stati vengono ordinati deterministicamente per:

```text
timeNs
vehicleId con ordinamento naturale
```

L'ordinamento naturale produce:

```text
veh_0
veh_1
veh_2
...
veh_9
veh_10
veh_11
```

La scrittura e' sicura: lo script scrive prima un file temporaneo nella cartella di output e sostituisce il CSV finale solo dopo che tutte le validazioni sono state superate.

### Riepilogo diagnostico 10B

A fine esecuzione lo script stampa:

```text
Vehicle state stream export completed
inputFile=<path>
registrationsFound=<count>
vehicleUpdatesFound=<count>
statesExported=<count>
registeredVehicles=<count>
updatedVehicles=<count>
duplicateRegistrations=0
duplicateVehicleStates=0
updatesBeforeRegistration=0
projectedCoordinatesPopulated=0
projectedCoordinatesMissing=<count>
vehicleUpdateFieldCountDistribution:
  <fieldCount>=<rowCount>
firstStateTimeNs=<value>
lastStateTimeNs=<value>
outFile=<path>
```

Per la run diagnostica corrente sono attesi:

```text
registrationsFound=12
vehicleUpdatesFound=1824
statesExported=1824
registeredVehicles=12
updatedVehicles=12
projectedCoordinatesPopulated=0
projectedCoordinatesMissing=1824
vehicleUpdateFieldCountDistribution:
  30=1824
firstStateTimeNs=7000000000
lastStateTimeNs=180000000000
```

Questi valori emergono dal file e non sono hard-coded nello script.

### Limiti 10B

Questa fase:

```text
non calcola ancora projectedX/projectedY
non calcola ancora distanze
non aggiunge ancora localCpu
non costruisce ancora VehicleSnapshot
non assembla ancora SystemSnapshot
non invoca il core Java
non implementa il bridge live
```

`VEHICLE_REGISTRATION` viene usato per validare il ciclo di vita minimo del veicolo. Il CSV contiene una riga per ogni `VEHICLE_UPDATES` valido.

Nella run diagnostica corrente non sono stati osservati eventi di rimozione dei veicoli. Non viene quindi inventata una rappresentazione delle uscite. La gestione delle uscite verra' estesa solo dopo avere osservato il relativo formato reale.

## Stato complessivo

Le fasi implementate producono artefatti intermedi:

```text
data/mosaic-study/task_stream.csv
data/mosaic-study/vehicle_state_stream.csv
```

Questi file saranno input per le fasi successive dell'exporter offline. Non costituiscono ancora snapshot MA-GA completi.
