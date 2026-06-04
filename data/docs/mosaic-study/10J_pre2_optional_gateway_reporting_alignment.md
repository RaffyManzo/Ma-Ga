# Fase 10J-pre2 - Riallineamento reporting gateway opzionale

## Contesto

Le fasi precedenti hanno reso valido il contratto snapshot in cui un veicolo
puo' essere presente senza access link infrastrutturale attivo. LOCAL e
VEHICLE/V2V restano validi senza gateway, mentre EDGE e CLOUD richiedono un
gateway attivo e risolvibile. La Fase 10J-pre ha inoltre permesso agli snapshot
vuoti di raggiungere il ramo `EMPTY_TASK_SET` e ha avviato `JSON_TIME` dal
primo timestamp JSON realmente disponibile.

La baseline canonica usata e':

```text
log-20260604-220216-MaGaIntegratedStudy
```

Gli snapshot validati dalla 10I sono in:

```text
data/snapshots/mosaic-generated/
```

## Replay iniziali

Prima del riallineamento, sia `JSON_SEQUENCE CONFIGURED_RUNTIME` sia
`JSON_TIME OBSERVED_RUNTIME` superavano il bootstrap e raggiungevano il report
finale. La prima finestra vuota era gestita come `EMPTY_TASK_SET`, e `JSON_TIME`
non stampava piu' `No temporal step available`.

Il crash avveniva solo durante il reporting finale.

## Crash nel reporting

Eccezione osservata:

```text
IllegalArgumentException: Vehicle veh_0 has no active access link.
```

Stack rilevante:

```text
AccessLinkMetricsEstimator.estimateActiveLink(...)
AccessLinkDynamicityDiagnosticPrinter.changes(...)
AccessLinkDynamicityDiagnosticPrinter.printSummary(...)
AccessLinkDynamicityDiagnosticPrinter.print(...)
AdaptiveWindowReportPrinter.print(...)
AdaptiveWindowMain.main(...)
```

La causa era che `AccessLinkDynamicityDiagnosticPrinter` usava ancora l'API
strict `estimateActiveLink(...)` su ogni veicolo comune tra due finestre. Questa
API deve restare strict per i consumer che richiedono realmente un gateway, ma
non e' adatta a un report descrittivo che attraversa veicoli LOCAL-only o
V2V-only.

## Distinzione strict/reporting

Il riallineamento non cambia il core algoritmico:

```text
EDGE/CLOUD
    -> gateway attivo richiesto

LOCAL e VEHICLE/V2V
    -> gateway infrastrutturale non richiesto

AccessLinkMetricsEstimator.estimateActiveLink(...)
    -> resta strict

AccessLinkResolver.requireActiveAccessLink(...)
    -> resta strict
```

Il solo reporting descrittivo usa l'API opzionale
`estimateActiveLinkIfPresent(...)`.

## AccessLinkDynamicityDiagnosticPrinter

Il printer ora costruisce uno stato diagnostico interno per ogni veicolo:

```text
gatewayId
available
distanceMeters
phiLink
quality
hasActiveAccessLink
```

Semantica:

```text
link attivo e disponibile
    -> q_v(k) = clamp01(1 - phiLink)

link attivo ma indisponibile
    -> q_v(k) = 0

nessun link attivo
    -> q_v(k) = 0
    -> gateway, distanza e phiLink assenti
```

Le metriche assenti sono renderizzate come `-`. La qualita' assente e' invece
stampata come `0,000000`, perche' nel modello diagnostico
`q_v(k)=0` senza accesso infrastrutturale attivo.

`Dl(k)` non cambia: resta la media delle variazioni assolute della qualita' sui
veicoli comuni tra due finestre.

## Transizioni gateway

Il report distingue:

```text
UNCHANGED
COVERAGE_GAIN
COVERAGE_LOSS
HANDOVER
```

Regole:

```text
- -> -             = UNCHANGED
- -> gateway       = COVERAGE_GAIN
gateway -> -       = COVERAGE_LOSS
gateway -> stesso  = UNCHANGED
gateway -> diverso = HANDOVER
```

Perdita e recupero della copertura non sono classificati come handover.

## CloudGatewayDiagnosticPrinter

Il riepilogo iniziale non usa piu' implicitamente solo il primo snapshot per
descrivere la run. Questo evita che la finestra vuota iniziale produca un
`accessLinkCount: 0` ambiguo.

Il report ora espone:

```text
mode: STRICT_GATEWAY
legacyPlaceholderEnabled: false
configuredGatewayCountAcrossRun
firstSnapshotAccessLinkCount
maximumAccessLinkCountAcrossWindows
maximumActiveAccessLinkCountAcrossWindows
windowsWithActiveAccessLinks
windowsWithoutActiveAccessLinks
```

Le transizioni gateway usano l'unione dei veicoli presenti nelle mappe
precedente e corrente e stampano solo cambiamenti reali:

```text
COVERAGE_GAIN
COVERAGE_LOSS
HANDOVER
```

Il gateway mancante e' rappresentato con `-`. Non vengono introdotti gateway,
link o pool fittizi.

## Audit printer

I consumer strict trovati sono:

```text
AccessLinkMetricsEstimator.estimateActiveLink(...)
AccessLinkResolver.requireActiveAccessLink(...)
CoverageEstimator per EDGE/CLOUD
CandidatePrefilter per EDGE/CLOUD
```

Sono rimasti invariati per preservare la semantica strict del core.

Il consumer descrittivo aggiornato e':

```text
AccessLinkDynamicityDiagnosticPrinter
```

Gli altri printer ispezionati non richiedono un gateway attivo per veicoli
generici LOCAL-only o V2V-only.

## Test introdotti

L'harness esterno:

```text
tools/replay-bootstrap-validation/
```

valida:

```text
A uncovered -> uncovered
B covered -> uncovered
C uncovered -> covered
D gateway_a -> gateway_b
E riepilogo CLOUD aggregato
F JSON_SEQUENCE CONFIGURED_RUNTIME con 36 step
G JSON_TIME OBSERVED_RUNTIME smoke con 36 step
```

Sono mantenuti anche i controlli della 10J-pre su snapshot vuoto, task non
vuoto senza candidati, sorgente temporale no-look-ahead e replay sequenziale.

## Risultati

Build:

```text
buildStatus=COMPLETED
```

Harness:

```text
testsExecuted=12
testsPassed=12
testsFailed=0
```

Replay sequenziale diretto:

```text
JSON_SEQUENCE CONFIGURED_RUNTIME data/snapshots/mosaic-generated 36
exit code = 0
Executed windows = 36
task evaluations = 682
```

Smoke temporale diretto:

```text
JSON_TIME OBSERVED_RUNTIME data/snapshots/mosaic-generated 36
exit code = 0
Executed windows = 36
futureLookAheadViolations = 0
```

La run `JSON_TIME` e' uno smoke test: con 36 step in `OBSERVED_RUNTIME` non
raggiunge necessariamente lo snapshot finale a 180 s.

## Warning residui

La baseline diagnostica corrente mostra decisioni LOCAL:

```text
WARNING_DIAGNOSTIC_BASELINE_NOT_STRESSING_OFFLOADING
```

Questo non viene corretto in questa sottofase. Non sono stati modificati
fitness, inizializzazione, mutation, repair, CPU sintetica, banda sintetica,
workload o `maxSteps`.

Lo smoke `JSON_TIME` non valida l'intero orizzonte temporale:

```text
WARNING_JSON_TIME_FULL_HORIZON_NOT_YET_VALIDATED
```

## Readiness per Fase 10J

La sottofase e' pronta per la validazione completa 10J perche':

```text
- il reporting tollera veicoli senza gateway attivo;
- le metriche assenti non sono inventate;
- q_v(k)=0 senza access link attivo;
- COVERAGE_GAIN, COVERAGE_LOSS e HANDOVER sono distinti;
- JSON_SEQUENCE completa 36 finestre con exit code 0;
- JSON_TIME smoke completa 36 step con exit code 0;
- non sono stati rilevati future look-ahead;
- il core strict EDGE/CLOUD resta invariato.
```

## Attivita escluse

Questa fase non implementa:

```text
Fase 10J completa
validazione JSON_TIME full horizon
modifiche a fitness, repair, mutation o crossover
modifiche a TemporalWindowManager
modifiche a TimeIndexedSnapshotReplaySource
modifiche agli snapshot JSON
replay scientifico completo
```
