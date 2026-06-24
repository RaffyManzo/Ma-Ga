# G04-R0 - Preflight e proposta per la discussione

Stato: **G04_R0_PREFLIGHT_COMPLETE_DISCUSSION_REQUIRED**

## Che cosa e stato fatto

Il preflight ha analizzato le route materializzate, i log di mobilita gia disponibili, la configurazione delle due RSU, il generatore di workload e i campi del reporting. Non ha avviato simulazioni e non ha modificato codice o matrici.

## Candidato proposto

- Base della selezione: `GEOMETRY`
- Veicolo probe: `synthetic_005`
- Route: `route_dual_rsu_switch_04`
- Transizione rsu_0 -> rsu_1 osservata/stimata: `64.5474192598514 s`
- Durata proposta: `180 s`
- Densita proposta: `low_density`
- Seed: `104729`
- Local CPU multiplier: `0.5`

## Stato delle capacita

- Route: **GEOMETRY_ROUTE_REQUIRES_RUNTIME_CONFIRMATION**
- Workload mirato: **CONFIG_EXPOSED**
- Reporting: **MINIMAL_DIAGNOSTIC_EXTENSION_REQUIRED**
- Raccomandazione: **MINIMAL_DIAGNOSTIC_REPORTING_EXTENSION_REQUIRED**

## Obiettivi

- Primari: T-096 e T-098.
- Secondari: T-091 e T-094.
- Escluso: T-093, gia PASS_RECOVERED.

## Decisioni richieste

- The selected route is geometry-based and must be confirmed at runtime.
- Some critical fields are not confirmed in existing outputs; decide whether a minimal diagnostic extension is needed.
- Confirm whether localCpuMultiplier=0.5 is acceptable as a fixed enabling condition rather than the variable under test.
- Confirm the single-run acceptance criteria and the treatment of T-094 as a secondary opportunity, not a forced outcome.

## Vincoli

- Una sola run nel Blocco 2.
- Nessuna ripetizione delle baseline G02.
- Nessuna ripetizione high-density extended G03.
- Nessuna modifica del core MA-GA.
- Qualunque eventuale modifica Java resta da discutere e autorizzare.
- Le matrici non vengono aggiornate nel Blocco 1.

## Prossima azione

**DISCUSS_G04_R0_PROPOSAL_THEN_PREPARE_SINGLE_BLOCK2_RUN**
