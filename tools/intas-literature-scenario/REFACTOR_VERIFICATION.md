# Refactor verification report

## Static checks completed

```text
Python syntax compilation: PASSED
JSON parsing: PASSED
candidate_0045 edge-id count: 155
```

## Preserved local runtime dependencies

```text
tmp/mosaic-25.2
tmp/external-tools/scenario-convert-25.2
```

## Checks that must run on Windows

The refactor container cannot execute the local Windows SUMO and MOSAIC binaries. Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\intas-literature-scenario\quick_literature_workflow.ps1 `
  -ForceRebuild `
  -PrintDetailedLiveReport `
  -PrintSummary
```

The materializer will reject the generated scenario if SUMO reports invalid density, missing gateway switches, errors, teleports or emergency braking.
