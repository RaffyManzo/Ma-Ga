# 14C.1-fix - Hardening materializzatore InTAS e dry-run reale

## Obiettivo

Questa correzione stabilizza il materializzatore introdotto in 14C.1 prima di procedere alla configurazione radio, Cell e compute della 14C.2.

Il perimetro resta limitato allo scenario literature-based `MaGaLiteratureBasedUrbanStudy` e al tool `tools/intas-literature-scenario/`. Il core MA-GA e lo scenario diagnostico `MaGaLiveMagaRuntimeStudy` non sono stati modificati.

## Problemi corretti

1. Preservazione XML SUMO.
   Il materializzatore ora conserva integralmente gli attributi `vType` originali usati dai veicoli selezionati e preserva gli attributi veicolo presenti, inclusi `departPos`, `arrivalPos` e `arrivalSpeed`. Gli elementi annidati sono mantenuti nel route subset; eventuali tag non riconosciuti sono registrati come warning nel report.

2. Projection MOSAIC concreta.
   `scenario_config.json` generato non contiene piu campi projection vuoti. Il tool prova prima la conversione tramite `sumolib` da `SUMO_HOME/tools`; nel dry-run locale la conversione geografica ha richiesto `pyproj`, non presente, quindi il report registra il fallback esplicito `FALLBACK_LINEAR_BOUNDARY_INTERPOLATION`.

3. Classificazione scientifica SNS.
   La mobilita SUMO/InTAS resta `MODELLED_DIRECTLY`. Il profilo `ITS-G5 / IEEE 802.11p` e il raggio nominale V2V 250 m sono classificati come `CALIBRATED_ABSTRACTION`; frequenza 5.9 GHz, banda 10 MHz, potenza 23 dBm, payload CAM 300 byte e PRR >= 90% restano `DOCUMENTATION_ONLY`.

4. Dry-run utile.
   Il dry-run analizza gli asset reali InTAS, valuta candidati, produce report JSON/Markdown e non richiede `scenario-convert`. Nessun database `.db` fittizio viene creato.

## Asset InTAS usati

- Checkout esterno: `C:\Users\raffa\IdeaProjects\external\InTAS`
- Repository: `https://github.com/silaslobo/InTAS`
- Commit rilevato: `0f7951ba01dda8483f0a852f2c3e4ff0d8a1c0ee`
- Licenza rilevata: GPL-3.0, da `LICENSE`
- File sorgente principali:
  - `scenario/ingolstadt.net.xml`
  - `scenario/InTAS_buildings.sumocfg`
  - `scenario/routes/InTAS_001.rou.xml` ... `scenario/routes/InTAS_022.rou.xml`
  - `scenario/routes/BusRoutes.flow.xml`
  - `scenario/routes/ped.rou.xml`

## Comando di validazione

```powershell
py -3 -B tools\intas-literature-scenario\validate_intas_source.py `
  --intas-root C:\Users\raffa\IdeaProjects\external\InTAS
```

Esito: `VALID`.

## Comando dry-run

```powershell
py -3 -B tools\intas-literature-scenario\build_intas_literature_scenario.py `
  --intas-root C:\Users\raffa\IdeaProjects\external\InTAS `
  --output-root C:\Users\raffa\IdeaProjects\maga-core\tmp\intas-literature-dryrun `
  --density all `
  --duration-profile nominal `
  --dry-run
```

Esito: `DRY_RUN_COMPLETED`.

## Candidato selezionato

- Candidate ID: `candidate_0045`
- Candidati valutati: `64`
- Finestra cartesiana:
  - minX `213734.86`
  - minY `449911.51`
  - maxX `214634.86`
  - maxY `450811.51`
- Semafori: `8`
- Edge guidabili: `155`
- Junction: `86`
- Largest connected component share: `0.9431818181818182`
- Route passeggeri filtrate nel candidato: `158`
- Gateway switch potential: `true`
- RSU coverage overlap stimato: `true`
- Distanza RSU: `398.7544694420463` m

Coordinate RSU candidate:

```json
[
  {
    "x": 214429.45,
    "y": 450749.91
  },
  {
    "x": 214047.75,
    "y": 450634.54
  }
]
```

## Densita route subset

| Profilo | Target medio | Media osservata | Massimo osservato | Veicoli | Seed | Errore |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `low_density` | 15 | 15.498338870431894 | 18 | 21 | 104729 | 0.49833887043189407 |
| `nominal` | 30 | 30.292358803986712 | 36 | 49 | 130363 | 0.292358803986712 |
| `high_density` | 50 | 49.647840531561464 | 55 | 76 | 155921 | 0.3521594684385363 |

I route subset hanno preservato i `vType` `default_001` e `random_001`, gli attributi veicolo rilevanti e il tag annidato `routeDistribution`. Non sono stati registrati tag annidati non supportati.

## Projection generata

```json
{
  "method": "FALLBACK_LINEAR_BOUNDARY_INTERPOLATION",
  "centerCoordinates": {
    "latitude": 45.44781708103299,
    "longitude": 15.792465681126096
  },
  "cartesianOffset": {
    "x": -464198.88,
    "y": -4952821.58
  },
  "fallback": "SUMOLIB_CONVERT_XY2LONLAT_FAILED: No module named 'pyproj'",
  "valid": true
}
```

Il fallback e esplicito nel report. Per una materializzazione definitiva e preferibile installare la dipendenza Python `pyproj` usata da `sumolib` per la conversione geodetica piena.

## Stato scenario-convert

`scenario-convert` non e stato trovato in `PATH`, nella variabile `SCENARIO_CONVERT` o sotto `tmp/mosaic-25.2`.

Il dry-run non lo richiede e non ha creato database fittizi. Per la materializzazione completa sara necessario configurare MOSAIC Extended Scenario-Convert e rieseguire passando `--scenario-convert <path>` oppure impostando `SCENARIO_CONVERT`.

## Asset generati esclusi da Git

Gli output sono sotto:

```text
tmp/intas-literature-dryrun/MaGaLiteratureBasedUrbanStudy/
```

Contengono route subset, configurazioni concrete, report e metadata generati. Restano output locali derivati e non devono essere versionati in questa sottofase.

Non sono stati generati file `.db`.
