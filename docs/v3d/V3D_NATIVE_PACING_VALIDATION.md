# Validazione del pacing nativo MOSAIC V3-D

**Stato:** `PASS_V3D_NATIVE_PACING_VALIDATED_SINGLE_PILOT`

- Branch: `experiment/v3d-simulation-pacing`
- Commit tooling: `9de6ea7f190bf0100c8f4b793f8c9e71b79586bc`
- Evidenza: `v3d-paced-pilot-20260629-020307.zip`
- SHA-256 evidenza: `C35E6C43A66A7006186DB29F51BAEC55B9C60EB29A5F4C4E95E3C04B32B92E62`
- Fattore MOSAIC: `-b 1`
- Modifiche Java o core MA-GA: nessuna

## Risultato

Il pacing nativo MOSAIC ha mantenuto l'avanzamento della simulazione entro
il tempo reale:

- tempo simulato: `300 s`;
- durata MOSAIC: `326.524 s`;
- rapporto simulato/wall-clock MOSAIC:
  `0.918769`;
- durata end-to-end del runner:
  `345.150 s`.

## Esiti MA-GA

- job completati: `154`;
- strategie applicate: `140`;
- risultati stale: `14`;
- stale ratio: `9.090909%`;
- stale solo wall-clock: `7`;
- stale solo simulation-age: `0`;
- stale per entrambe le cause:
  `7`;
- sequenza stale massima:
  `4`;
- ultima strategia applicata:
  `299.1 s`;
- intervallo finale senza nuova strategia:
  `0.89999999999997726 s`.

Non sono state osservate violazioni strutturali o processi residui.

## Interpretazione

Il pacing elimina la distorsione prodotta dall'avanzamento simulato molto
più rapido dell'esecuzione wall-clock del GA. Gli stale residui sono
associati a reali superamenti del budget wall-clock; una parte supera
anche il freshness cap.

Questa prova valida funzionalmente il pacing e rende possibili esperimenti
end-to-end temporalmente interpretabili. Non costituisce da sola una
valutazione statistica generalizzabile delle prestazioni dell'algoritmo.
