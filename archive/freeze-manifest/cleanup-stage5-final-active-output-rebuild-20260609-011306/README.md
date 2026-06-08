# Cleanup freeze - Stage 5: rebuild finale degli output attivi

Data: 2026-06-09 01:13:23
Branch: MOSAIC/SUMO-integration
HEAD: dba3da7057c4811aff0ff02b94079a86ecbb1689

## Obiettivo

Questa sottofase verifica che i tre output compilati richiesti dal runtime
live siano rigenerabili dagli script versionati.

## Build eseguiti

- live-state-layer
- adhoc-radio-diagnostic
- live-maga-runtime

## Gestione della Execution Policy

Ogni script è stato eseguito in un processo PowerShell figlio con:

`powershell.exe -ExecutionPolicy Bypass`

La policy della sessione interattiva non è stata modificata.

## Fix StrictMode verificato

Il build del diagnostico radio ad-hoc gestisce correttamente il caso con
un solo file sorgente Java tramite un array esplicito.

## Backup reversibile

Gli output precedenti sono conservati localmente sotto:

`tmp\archive\freeze-local-evidence-20260609\pre-rebuild-active-output-backup-final-20260609-011306`

## Risultati

- Directory compilate sottoposte a backup: 3
- Build completati: 3
- JAR rigenerati e verificati: 3
- Modifiche tracciate residue: 1
- File tracciato modificato: tools/mosaic-adhoc-radio-diagnostic/build.ps1
- Deploy eseguito: no
- Smoke test eseguito: no
