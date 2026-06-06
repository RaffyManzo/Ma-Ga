# MaGaLiteratureBasedUrbanStudy

`MaGaLiteratureBasedUrbanStudy` is the planned literature-based urban scenario
for post-Phase-13 calibration.

This directory is intentionally a versionable scaffold. It does not contain a
generated InTAS network, route file or MOSAIC database yet because those assets
must be derived from an external InTAS checkout and reviewed for redistribution
before committing.

## Source

- Mobility source: InTAS
- Repository: https://github.com/silaslobo/InTAS
- Required external files:
  - `scenario/ingolstadt.net.xml`
  - `scenario/InTAS_buildings.sumocfg`
  - `scenario/routes/`

## Materialization

Use:

```powershell
python tools/intas-literature-scenario/build_intas_literature_scenario.py `
  --intas-root <path-to-external-InTAS> `
  --output-root <generated-output-root>
```

When all dependencies are available, the generated scenario contains concrete
SUMO files, MOSAIC mapping/config files, calibration metadata and a MOSAIC
database created through Scenario-Convert.

## Phase 14C.1 Scope

This scaffold records approved constants:

- SUMO step length: `0.1 s`
- smoke duration: `180 s`
- nominal duration: `300 s`
- extended duration: `600 s`
- active vehicle targets: `15`, `30`, `50`
- nominal RSU count: `2`
- extended RSU count: `3`
- nominal RSU radius: `250 m`
- nominal V2V radius: `250 m`

The extraction rectangle and RSU coordinates are intentionally unresolved in
the versioned templates. They must be computed by the materializer from real
InTAS assets.
