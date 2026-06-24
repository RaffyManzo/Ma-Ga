# G05 - Preflight risorse e repair

Stato: **G05_PREFLIGHT_REVIEW_REQUIRED**

Questa sottofase non avvia simulazioni e non modifica Java.
L inventario individua famiglie di parametri e sorgenti candidate, ma non prova da solo che una variante sia eseguibile.

## Configurazioni

| Run | Config | Core | Config | Docs | Stato preliminare |
|---|---|---:|---:|---:|---|
| R-LOCALCPU-01 | CFG-R-LOCALCPU | 150 | 13 | 45 | PARAMETER_FAMILY_PRESENT_VARIANT_NOT_PROVEN |
| R-EDGECPU-01 | CFG-R-EDGECPU | 164 | 15 | 412 | PARAMETER_FAMILY_PRESENT_VARIANT_NOT_PROVEN |
| R-CLOUDCPU-01 | CFG-R-CLOUDCPU | 184 | 16 | 401 | PARAMETER_FAMILY_PRESENT_VARIANT_NOT_PROVEN |
| R-CELLBW-01 | CFG-R-CELLBW | 66 | 59 | 86 | PARAMETER_FAMILY_PRESENT_VARIANT_NOT_PROVEN |
| R-V2VBW-01 | CFG-R-V2VBW | 126 | 26 | 292 | PARAMETER_FAMILY_PRESENT_VARIANT_NOT_PROVEN |
| R-RTT-01 | CFG-R-RTT | 126 | 0 | 63 | CORE_PRESENT_CONFIG_EXPOSURE_NOT_PROVEN |

## Regola decisionale

Nessuna run G05 deve partire sulla sola base dell inventario automatico.
Il passo successivo consiste nel leggere i match, identificare i file e i flag realmente operativi e approvare una configurazione alla volta.

## Oracoli minimi

### R-LOCALCPU-01

Verificare pressione CPU locale, fallback e assenza di over-allocation dopo il repair.

Controllo manuale: Verificare manualmente file e flag che materializzano la variante prima di avviare MOSAIC.

### R-EDGECPU-01

Verificare saturazione EDGE, repair CPU aggregato e assenza di violazioni residue.

Controllo manuale: Verificare manualmente file e flag che materializzano la variante prima di avviare MOSAIC.

### R-CLOUDCPU-01

Verificare vincolo CPU CLOUD, riduzione delle scelte non sostenibili e coerenza del repair.

Controllo manuale: Verificare manualmente file e flag che materializzano la variante prima di avviare MOSAIC.

### R-CELLBW-01

Verificare pressione dei link CELL, repair della banda e alternative EDGE/CLOUD.

Controllo manuale: Verificare manualmente file e flag che materializzano la variante prima di avviare MOSAIC.

### R-V2VBW-01

Verificare pressione DIRECT_V2V, scelte VEHICLE, repair e fallback.

Controllo manuale: Verificare manualmente file e flag che materializzano la variante prima di avviare MOSAIC.

### R-RTT-01

Verificare aumento del costo comunicativo e minore convenienza delle scelte remote.

Controllo manuale: La famiglia esiste nel core, ma manca una leva esterna verificata. Non modificare Java senza discussione.

## Metriche trasversali

- allocazione CPU e capacita disponibile per nodo;
- banda per candidato e per pool condiviso;
- conteggi e cause del repair;
- fallback LOCAL e DEGRADED_BEST_EFFORT;
- distribuzione LOCAL, VEHICLE, EDGE e CLOUD;
- runtime GA, stale ratio, snapshot lag e violazioni runtime;
- integrita dei vincoli dopo il repair;
- validator e diagnostiche della risorsa sotto pressione.
