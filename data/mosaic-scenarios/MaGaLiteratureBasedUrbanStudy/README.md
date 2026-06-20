# MaGaLiteratureBasedUrbanStudy template

Questa cartella e' lo scaffold versionato dello scenario finale
`MaGaLiteratureBasedUrbanStudy`.

Non e' la copia concreta eseguita da MOSAIC. Contiene solo template e file di
forma generale, usati dal materializer per generare scenari concreti e
riproducibili.

## Cosa contiene

```text
scenario_config.template.json
application/*.template.json
cell/*.template.json
mapping/mapping_config.template.json
sns/sns_config.template.json
sumo/sumo_config.template.json
```

Questi file descrivono la forma dello scenario: federati, applicazioni,
mapping, Cell, SNS, SUMO e configurazioni MA-GA. I valori concreti dipendono
da densita, durata, seed e sottorete selezionata.

## Cosa non contiene

Questa cartella non contiene gli artefatti concreti della run finale:

```text
SUMO .net.xml
SUMO .rou.xml
SUMO .sumocfg
SQLite database di Scenario-Convert
JAR runtime generati
log MOSAIC
report di run
```

Questi file sono generati o copiati localmente sotto `tmp/`.

## Dove nascono gli scenari concreti

Il tool principale e':

```text
tools/intas-literature-scenario/
```

La materializzazione crea varianti concrete sotto:

```text
tmp/materialized-literature-scenarios/MaGaLiteratureBasedUrbanStudy/<density>-<duration>-seed-<seed>/
```

Il deploy copia una variante concreta nel runtime MOSAIC locale:

```text
tmp/mosaic-25.2/scenarios/MaGaLiteratureBasedUrbanStudy/
```

Le run scrivono evidenza locale sotto:

```text
tmp/mosaic-25.2/logs/log-<timestamp>-MaGaLiteratureBasedUrbanStudy/
```

## Profili

Density:

```text
low_density
nominal
high_density
```

Duration:

```text
smoke    = 180 s, controllo tecnico end-to-end
nominal  = 300 s, test operativo ordinario
extended = 600 s, verifica piu' lunga
```

`high_density` e' uno stress profile documentato, non la baseline operativa.

## Nota operativa

Non copiare manualmente file concreti in questa cartella. Se serve rigenerare
lo scenario, usare gli script in `tools/intas-literature-scenario/`.
