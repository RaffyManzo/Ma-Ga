# Cleanup freeze - Stage 7 recovery: smoke test post-cleanup validato

Data: 2026-06-09 01:22:30
Branch: MOSAIC/SUMO-integration
HEAD: dba3da7057c4811aff0ff02b94079a86ecbb1689

## Motivo della recovery

La simulazione MOSAIC era già terminata correttamente. Il primo wrapper si era
fermato dopo il summarizer perché controllava $LASTEXITCODE, non impostato
dallo script PowerShell completato normalmente.

## Run preservato integralmente

`tmp\mosaic-25.2\logs\log-20260609-011846-MaGaLiteratureBasedUrbanStudy`

## Risultato

- Status: `LITERATURE_SMOKE_TEST_PASSED`
- simulationCompleted: `True`
- gaParameterScalingMode: `STATIC`
- taskCompletionModel: `NOT_IMPLEMENTED`
- tasksGeneratedCumulative: `96`
- tasksRemovedAtDeadlineCumulative: `96`
- tasksPendingAtEnd: `0`
- staleRatioPercent: `0`
- maximumAbsoluteSnapshotLagSeconds: `0`
- nonZeroLagWindowCount: `0`
- validatorErrors: `0`

## Verifiche

- Summarizer completato.
- Validator completato.
- I due JAR iniettati corrispondono ai JAR rigenerati.
- Nessuna modifica tracciata aggiuntiva introdotta.
- MOSAIC non è stato rieseguito durante la recovery.
