# Snapshot Contract Validation

Harness diagnostico riutilizzabile per la sottofase 10I-pre.

Il tool resta fuori da `src/` e verifica il contratto snapshot aggiornato usando
loader, validator e model Java reali del core MA-GA. Non esegue il GA, non
genera snapshot MOSAIC finali e non modifica la pipeline offline.

## Scopo

La 10I-pre riallinea il contratto degli snapshot per rappresentare veicoli
presenti nella simulazione ma senza gateway infrastrutturale attivo.

Il contratto validato e':

- ogni veicolo puo' avere zero o un access link attivo;
- piu' di un access link attivo resta invalido;
- `LOCAL` e `VEHICLE`/V2V possono esistere senza access link infrastrutturale;
- `EDGE` e `CLOUD` richiedono ancora un access link attivo e un gateway
  risolvibile;
- `Dl(k)` assegna qualita' `0` ai veicoli senza link attivo;
- `T_coverage_ref` calcola la media solo sui veicoli con link attivo e usa `0`
  quando nessun link attivo esiste.

## Build

```powershell
Set-Location "C:\Users\raffa\IdeaProjects\maga-core"
.\tools\snapshot-contract-validation\build.ps1
```

Il build compila l'intero progetto Java piu' il harness con i JAR Jackson
presenti in:

```text
out/codex-lib/
```

## Esecuzione

```powershell
Set-Location "C:\Users\raffa\IdeaProjects\maga-core"
.\tools\snapshot-contract-validation\run.ps1
```

## Fixture

Le fixture coprono:

- LOCAL-only senza gateway attivo;
- V2V-only senza gateway attivo;
- scenario misto con un veicolo coperto e uno scoperto;
- piu' link attivi per lo stesso veicolo;
- CLOUD senza access link attivo;
- EDGE senza access link attivo;
- dinamicita' coperto/scoperto;
- riferimento di copertura misto;
- fallback quando nessun veicolo ha access link attivo.
