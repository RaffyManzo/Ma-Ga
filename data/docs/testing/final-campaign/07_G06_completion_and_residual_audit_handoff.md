# Chiusura G06 e handoff all'audit delle evidenze residue

## Stato di G06

G06 è chiusa con stato `PASS_WITH_CONTROLLED_STALE_AND_DOCUMENTED_LIMITS`.

La fase ha verificato le politiche temporali del runtime attraverso:

1. preflight di configurazioni, sorgenti e reporting;
2. una run controlled-stale `G-STALE-01`;
3. recupero del post-processing senza rerun MOSAIC;
4. classificazione controllata del rigetto del validator canonico;
5. audit causale su 72 run e 36.486 record temporali.

Commit precedenti:

- G06-A: `e5730ae149e72c6f0510f293471e9ed466c8332d`;
- G06-B: `f43e512ccd3e54cc6cec624f7c9a34080fc66686`;
- G06-C: `c5a2e829ecdfee5674f7e112ba52fbdd2c3e9667`.

## Risultato forced-stale

`G-STALE-01` usa `CFG-G-STALE`, durata 180 s, seed 104729, workload WL-I, scaling STATIC e 300 ms di ritardo artificiale.

La run produce 33 job completati e 33 risultati stale, senza applicazioni. Il validator smoke ordinario respinge la run per `strategyApplications <= 0`; il rigetto è atteso e documentato, non ignorato. Snapshot lag e violazioni runtime restano a zero.

Il percorso keep-last non è osservabile perché manca una strategia valida precedente. Il token `EXCEEDS_CURRENT_AND_NEXT` non compare nelle righe overrun. Questi limiti devono essere mantenuti nei risultati e nella tesi.

## Decisione sulle run opzionali

- `G-SPARSE-01`: `SKIP_NOT_REQUIRED_FOR_MANDATORY_G06`.
- `G-ADAPTIVE-01`: `SKIP_OPTIONAL_NON_CANONICAL_T121`.

Non sono richieste altre simulazioni G06.

## Prossima fase obbligatoria

Prima di G02B eseguire un audit delle evidenze residue di G04/G05. In particolare:

- T-091: EDGE applicato resta evidenza parziale;
- T-094: coverage insufficiente applicata resta non osservata;
- T-096 e T-098: valutare una run G04-R con traiettoria controllata `rsu_0 -> rsu_1` e task remoti attivi;
- non ripetere le 45 baseline G02;
- non ripetere G03 high-density extended salvo studio separato del limite MOSAIC/TraCI.

Solo dopo l'audit decidere eventuali run G04-R. La sequenza resta:

`audit residui -> eventuali G04-R -> G02B -> G07`.

## Vincoli operativi

- Non modificare il core Java durante l'audit residuo.
- G02B sarà svolta su branch sperimentale dedicato.
- Le modifiche Java minime per G02B devono essere discusse prima dell'implementazione.
- Le baseline FULL_MA_GA di G02 vanno riutilizzate.
- Il validator canonico non deve essere modificato per rendere PASS la run forced-stale.
