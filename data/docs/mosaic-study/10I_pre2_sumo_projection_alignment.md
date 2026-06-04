# Fase 10I-pre2 - Normalizzazione cartesiana MOSAIC/SUMO

## 1. Problema

Gli exporter 10A-10H producono stati veicolari e infrastruttura con coordinate geografiche `latitude` e `longitude`. Queste coordinate sono adatte a descrivere la posizione sulla Terra, ma non possono essere usate come coordinate cartesiane metriche nel core MA-GA.

Il core usa distanze, coperture e decadimenti come grandezze metriche. Inserire direttamente latitudine come `x` e longitudine come `y` avrebbe mescolato gradi geografici e metri, creando snapshot formalmente validi ma fisicamente incoerenti.

## 2. Effetti sul core

La Fase 10I deve generare `VehicleSnapshot`, `AccessGatewaySnapshot`, candidati EDGE e access link con coordinate metriche coerenti. Per questo la proiezione e' stata separata in una sottofase esplicita prima dell'assemblaggio dei `SystemSnapshot`.

La 10I-pre2 non modifica il core Java, non modifica gli exporter 10A-10H e non altera i CSV geografici originali. Produce soltanto copie proiettate e diagnostiche.

## 3. File SUMO ispezionati

Sono stati ispezionati:

```text
data/mosaic-scenarios/MaGaIntegratedStudy/sumo/Barnim.net.xml
data/mosaic-scenarios/MaGaIntegratedStudy/sumo/Barnim.sumocfg
data/mosaic-scenarios/MaGaIntegratedStudy/sumo/sumo_config.json
```

L'elemento autorevole e' `location` in `Barnim.net.xml`:

```xml
<location netOffset="-395635.35,-5826456.24"
          convBoundary="0.00,0.00,15681.22,10052.78"
          origBoundary="395635.35,5826456.24,411316.57,5836509.02"
          projParameter="+proj=utm +zone=33 +ellps=WGS84 +datum=WGS84 +units=m +no_defs"/>
```

## 4. Utility e algoritmo

E' stata ispezionata l'utility SUMO locale:

```text
C:/Program Files (x86)/Eclipse/sumo-1.25.0/tools/sumolib
```

`sumolib.net.readNet(...).convertLonLat2XY(...)` usa la proiezione geografica del network e poi applica il `netOffset`. Nell'ambiente locale, pero', l'import di `sumolib` fallisce in conversione per assenza di `pyproj`.

Per evitare dipendenze installate e restare riproducibili, l'exporter implementa in standard library la conversione UTM WGS84 derivata direttamente dal `projParameter` SUMO:

```text
projectionPolicy = SUMO_NET_XML_UTM_WGS84_WITH_NET_OFFSET
projectionUtility = STANDARD_LIBRARY_UTM_WGS84_FROM_SUMO_PROJ_PARAMETER
```

L'algoritmo operativo e':

```text
lat/lon
    -> UTM WGS84 zona 33N
    -> applicazione netOffset SUMO
    -> projectedX/projectedY metrici nel sistema della rete
```

## 5. Output generati

```text
data/mosaic-study/vehicle_state_stream_projected.csv
data/mosaic-study/infrastructure_snapshot_projected.json
data/mosaic-study/diagnostics/phase_10i_pre2_projection_validation.json
```

Il CSV veicolare preserva le colonne originali e valorizza:

```text
projectedX
projectedY
projectionPolicy
projectionSource
```

Lo snapshot infrastrutturale proiettato preserva i campi originali dei gateway e aggiunge coordinate proiettate e sorgente della proiezione.

## 6. Validazione

La validazione round-trip usa:

```text
lat/lon -> x/y -> lat/lon
```

Risultati osservati:

```text
vehicleStatesRead = 1824
vehicleStatesProjected = 1824
gatewaysRead = 2
gatewaysProjected = 2
roundTripValidationSamples = 1826
roundTripMaximumErrorMeters = 0.00037287069228226144
roundTripAverageErrorMeters = 0.0003720875108376517
```

E' stato eseguito anche il confronto diagnostico tra distanza cartesiana proiettata e Haversine:

```text
distanceComparisonSamples = 3648
maximumProjectedVsHaversineDifferenceMeters = 3.100796595092106
averageProjectedVsHaversineDifferenceMeters = 0.8336723832972232
medianProjectedVsHaversineDifferenceMeters = 0.6895303613425767
```

Il confronto Haversine non sostituisce la proiezione SUMO. Serve solo come controllo di coerenza sulle distanze.

## 7. Limiti

La proiezione e' autorevole perche' deriva dal `Barnim.net.xml`, ma resta una normalizzazione offline. Non corregge eventuali errori a monte nei dati MOSAIC e non modifica le policy di copertura o di candidatura.

## 8. Readiness per 10I

La 10I-pre2 e' completata quando:

```text
phase10iPre2Status = COMPLETED
readyForPhase10I = true
errors = []
```

Con questi output la Fase 10I puo' assemblare snapshot usando coordinate metriche, senza inventare gateway, link o pool e senza usare latitudine/longitudine come coordinate cartesiane.
