# Sequenza temporale sintetica completa per MA-GA

Questa cartella contiene una singola sequenza temporale di snapshot JSON creati da zero.

L'obiettivo non è sostituire gli scenari realistici già presenti. La sequenza serve come scenario di regressione controllato: mentre il tempo scorre, il sistema attraversa progressivamente le principali condizioni gestite dal prototipo.

## Avvio consigliato

Main class:

```text
app.AdaptiveWindowMain
```

Program arguments:

```text
JSON_SEQUENCE CONFIGURED_RUNTIME data/snapshots/temporal/scenarios/comprehensive_dynamic_validation 27
```

La modalità `JSON_SEQUENCE` è quella principale per questa suite, perché consuma tutte le finestre nell'ordine stabilito.

Eseguire poi anche:

```text
JSON_TIME OBSERVED_RUNTIME data/snapshots/temporal/scenarios/comprehensive_dynamic_validation 27
```

In `JSON_TIME` è normale che alcuni file possano essere saltati o che venga riusato l'ultimo snapshot passato. Non deve mai comparire un look-ahead futuro.

## Copertura della sequenza

La timeline attraversa:

```text
baseline
stabilità
invarianza di T_coverage_ref rispetto ai candidati computazionali
prefilter strutturale
V2V fuori raggio e rientro con velocità scalari uguali
deadline riparabile
DEGRADED_BEST_EFFORT
full offloading diagnostico
semantica tau_n
contesa CPU aggregata remota
pressione banda
ingresso e uscita di veicoli
spike di task
degrado e recupero delle risorse
degrado e perdita dei link
spike severo combinato
separazione snapshot grezzo / snapshot filtrato
placeholder cloud
CPU locale condivisa
stabilità finale
```

## Lettura dei risultati

Consultare:

```text
TIMELINE.md
TIMELINE.csv
```

Ogni riga indica che cosa viene introdotto nello snapshot e quale invariante osservare nel report.

## Importante

Alcune finestre sono esplorative perché riguardano open issue non ancora risolte:

```text
banda aggregata e pool radio
gateway radio esplicito del cloud
CPU locale condivisa
```

Questi snapshot rendono osservabile il comportamento attuale, ma non definiscono da soli la soluzione progettuale.

## Validazione eseguita

Tutti i 27 JSON sono stati sottoposti a una validazione strutturale equivalente alle regole correnti di `SnapshotValidator`.
