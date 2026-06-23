# Report umano G04 — mobilità e connettività

## Risultato sintetico

Le cinque simulazioni G04 sono terminate correttamente e hanno superato il validator. La pipeline è rimasta causale: snapshot lag massimo zero e nessuna violazione runtime. Questo consente di chiudere il gruppo senza ripetere le run.

La chiusura non equivale però a dichiarare osservate tutte le funzioni pianificate. Il caso locale e la rimozione del vecchio placeholder cloud sono pienamente verificati. Il cloud gateway-aware è comparso in due decisioni applicate. Le metriche V2V, phiLink e Pmob sono finite e coerenti, ma le assegnazioni applicate sono rimaste quasi interamente locali. Non è stato osservato alcun gateway switch e non è comparso un caso di copertura insufficiente.

## Cosa dimostra G04

- Il MA-GA riceve candidati costruiti a partire dalla mobilità live.
- Il caso LOCAL non introduce penalità mobility-aware artificiali.
- Il cloud selezionato viene valutato attraverso il gateway radio e non mediante il placeholder storico.
- Le metriche di velocità relativa V2V sono finite e riproducibili.
- `phiLink`, `phiHo` e `Pmob` vengono calcolati con valori coerenti e con la somma pesata congelata.
- I risultati stale vengono scartati senza violare la causalità.

## Cosa non dimostra G04

- Non dimostra che il MA-GA scelga regolarmente VEHICLE o EDGE.
- Non dimostra un handover reale tra `rsu_0` e `rsu_1`.
- Non dimostra direttamente la penalizzazione di una copertura insufficiente.
- Non misura il completamento reale dei task, perché `taskCompletionModel` è `NOT_IMPLEMENTED`.

## Caso M-V2V-01

`M-V2V-01` ha prodotto 193 job GA, 100 strategie applicate e 93 risultati stale, con stale ratio `48,186528%`. Il runtime medio è `0,367724152 s`, il P95 `1,1884472 s` e il massimo `1,3606641 s`.

Questo è un risultato sperimentale utile: la configurazione ad alta densità con opportunità V2V aumenta il costo dell’ottimizzazione rispetto alla finestra temporale disponibile. Poiché lag e violazioni restano a zero, il coordinatore ha protetto correttamente il sistema scartando le soluzioni obsolete. Il dato va presentato come limite operativo e non come errore automatico del core.

## Chiusura

Stato complessivo: `PASS_WITH_OBSERVABILITY_LIMITS`.

Verdetti funzionali:

- PASS: T-090, T-099;
- PASS_CON_LIMITAZIONE: T-092, T-093, T-095, T-097;
- EVIDENZA_PARZIALE: T-091, T-096;
- NON_OSSERVATO: T-094, T-098;
- FAIL: nessuno.

Prossimo gruppo: G05 — risorse, saturazione, repair e fallback.
