# Audit di fattibilità G02B — revisione finale

## Esito

- Stato: **G02B_DECISION_REQUIRED**.
- Run comparative avviate: **0**.
- Modifiche Java eseguite: **nessuna**.
- Baseline FULL MA-GA: disponibile dalle run G02 e da riutilizzare.
- Core congelato: invariato rispetto a `5a9477735a3d707a5f000a64653cd2a6fc7f2007`.

## Revisione delle capacità

### LOCAL-only

Classificazione finale: **NOT_EXPOSED**.

L'audit automatico aveva indicato `POTENTIAL_TOOLING_SUPPORT`, ma i due match non riguardano
una modalità di offloading locale:

1. `target_length` nella selezione delle route;
2. un campo generico di metadati dello scenario.

Non esiste quindi uno switch dimostrato che limiti i candidati a LOCAL.

### Penalità mobility-aware disattivata

Classificazione finale: **CORE_PRESENT_CONFIG_NOT_EXPOSED**.

La penalità e il relativo peso esistono nel core, ma i JSON runtime materializzati non espongono
una chiave per impostare il peso a zero o disattivare la componente durante una run live.

### Cold start senza population reuse

Classificazione finale: **CORE_PRESENT_CONFIG_NOT_EXPOSED**.

Le modalità FIRST_RUN, WARM_START, PARTIAL_RESTART e COLD_START esistono nel core. Tuttavia,
la configurazione runtime non consente di forzare COLD_START per ogni finestra.

### Random-feasible o greedy

Classificazione finale: **NO_EVIDENCE**.

Non è stata individuata una baseline già disponibile e configurabile. Essendo opzionale, non va
introdotta soltanto per completare G02B.

## Conseguenza

Le comparative G02B non possono essere eseguite correttamente rispettando contemporaneamente:

- core Java congelato;
- sole modifiche di configurazione o tooling esterno;
- assenza di simulazioni artificiali tramite post-processing.

## Decisione richiesta

Sono disponibili due percorsi:

1. **PRESERVE_FREEZE**: chiudere G02B come non eseguibile sotto il freeze, documentare il limite e
   passare a G03;
2. **CONTROLLED_EXPERIMENTAL_BRANCH**: progettare e approvare modifiche minime al core per esporre
   gli switch necessari, quindi ripetere smoke e avviare le comparative.

Nessuna delle due decisioni viene applicata automaticamente.
