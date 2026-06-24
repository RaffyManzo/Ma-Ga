# G05 - Sintesi sperimentale utilizzabile nella tesi

## Obiettivo

G05 valuta la sensibilita del MA-GA a variazioni controllate delle risorse computazionali e di comunicazione. Le sei varianti modificano una famiglia di parametri alla volta, mantenendo il core Java congelato.

## Metodo di confronto

Le baseline provengono da G02 e non vengono rieseguite. Per ogni variante e stata scelta una baseline con stessa densita, stesso workload e seed 104729:

- CFG-N-S per LOCAL CPU, EDGE CPU, CLOUD CPU e CELL bandwidth;
- CFG-N-I per RTT CELL;
- CFG-H-S per V2V bandwidth in high_density.

Le baseline G02 durano 300 secondi, mentre le run G05 durano 180 secondi. Per questo motivo vengono confrontati soltanto tassi normalizzati, percentuali e rapporti dei tempi runtime. I conteggi grezzi non vengono confrontati direttamente.

## Risultati principali

### CPU locale

La CPU locale ridotta produce offloading remoto in tutte le tre osservazioni. Il limite temporale e anch esso riprodotto in tutte le osservazioni. Il runtime medio della run originale e circa 8.58 volte quello della baseline matched, mentre il P95 e circa 25.01 volte superiore.

Il risultato e quindi duplice: il MA-GA reagisce correttamente alla minore capacita locale, ma il costo della ricerca aumenta e le strategie cessano di essere applicate molto prima della fine.

### CPU EDGE, CPU CLOUD e banda CELL

Le tre risorse degradate vengono caricate correttamente, ma non vengono selezionate nelle strategie applicate. Questo dimostra un comportamento di evitamento della risorsa meno conveniente, non una prova diretta di saturazione o repair.

### RTT CELL

La run RTT resta stabile: lo stale ratio e 0.307%, vicino alla baseline CFG-N-I. Tuttavia nessuna assegnazione EDGE o CLOUD applicata attraversa il link CELL, quindi l aumento di RTT non viene esercitato direttamente.

### Banda V2V in high_density

Lo stale ratio sale a 46.809%, contro 23.81% nella baseline matched. Il runtime medio e circa 3.97 volte superiore e il P95 circa 3.89 volte superiore.

Questa configurazione costituisce un limite di stress del sistema integrato. Non dimostra l uso della banda V2V ridotta, perche non vengono applicate assegnazioni VEHICLE.

## Evidenze residue

- T-093 e recuperato: sono presenti decisioni VEHICLE applicate con il modello V2V.
- T-091 resta non osservato nel batch principale, ma le recovery R-LOCALCPU hanno prodotto EDGE applicato. Questa evidenza va trattata come supporto aggiuntivo, non come validazione della variante EDGECPU.
- T-094 resta non osservato nelle strategie applicate.

## Conclusione

G05 produce risultati utilizzabili nella tesi per mostrare tre fenomeni distinti: reazione alle risorse locali ridotte, evitamento delle risorse remote degradate e limite temporale sotto pressione elevata. Non permette invece di dimostrare direttamente tutti i meccanismi di repair, perche i report correnti non espongono una causa strutturata di correzione del gene.
