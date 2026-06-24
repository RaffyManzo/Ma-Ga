# G06-B - Esecuzione controllata G-STALE-01

Stato tecnico: **PASS_WITH_EXPECTED_CANONICAL_VALIDATOR_REJECTION_TEMPORAL_AUDIT_REQUIRED**

## Domanda sperimentale

Il runtime scarta i risultati del GA diventati obsoleti quando viene introdotto un ritardo artificiale di 300 ms, senza violare gli oracoli tecnici HARD?

## Recupero del post-processing

La simulazione MOSAIC era gia terminata. Il summarizer e stato corretto forzando sei import CSV ad array. Il validator canonico e stato eseguito senza essere modificato e ha respinto la run esclusivamente per strategyApplications <= 0. Nella configurazione forced-stale questo esito e atteso: 33 job su 33 sono stati completati e scartati come stale, con zero applicazioni.

## Variabile modificata

- diagnosticArtificialGaDelayMs: 300 ms
- scaling: STATIC
- densita: nominal
- workload: WL-I
- durata: 180 s
- seed: 104729

## Validita tecnica

- Validator canonico: `LITERATURE_SMOKE_TEST_FAILED`
- Compatibilita validator: `EXPECTED_REJECTION_STRATEGY_APPLICATIONS_ZERO`
- Unico errore validator: `strategyApplications <= 0`
- Snapshot lag massimo: `0`
- Violazioni runtime: `0`
- DeltaT max mismatch: `0`

## Evidenze temporali preliminari

- Record temporali: `33`
- APPLIED: `0`
- STALE_DISCARDED: `33`
- Stale nel summary: `33`
- Righe overrun: `66`
- EXCEEDS_CURRENT_AND_NEXT: `0`
- Trigger: `FIRST_RUN=33`
- Riuso applicato: `FIRST_RUN=33`
- Azioni finestra: `FIRST_RUN=33`

## Interpretazione provvisoria

Controlled stale status: **CONTROLLED_STALE_EVIDENCE_PRESENT_ALL_RESULTS_DISCARDED**

Questa sottofase conferma la validita tecnica specifica della run forced-stale ed estrae le evidenze. Il fallimento del validator smoke non viene ignorato: viene classificato come incompatibilita attesa del criterio strategyApplications > 0 con una run progettata per scartare tutti i risultati. G06-C deve ancora verificare la catena causale completa e distinguere cio che e osservato da cio che non puo esserlo senza una strategia valida precedente.

## Limiti

- Le matrici non sono aggiornate.
- G-SPARSE-01 e G-ADAPTIVE-01 restano differite.
- T-116 non e ancora dichiarato finale.
- taskCompletionModel non viene reinterpretato.
