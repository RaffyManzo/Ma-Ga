# Tooling di materializzazione MA-GA V2

Questa directory contiene copie complete e isolate del tooling G00 per la
campagna `testing/final-campaign-v2-local-contention`.

Non modifica il tooling storico in `final_campaign/` e non importa risultati
della campagna precedente.

File:

- `final_campaign_v2_spec.json`: spec con percorsi V2 e freeze corrente;
- `materialize_final_campaign_v2.py`: orchestratore V2;
- `validate_final_campaign_v2.py`: validator V2;
- `materialize_final_campaign_v2.ps1`: wrapper PowerShell con spec esterna.

Modalita principali: `check`, `materialization`, `all`, `audit`,
`repair-canonical-metadata`, `repair-bandwidth-serialization`.

G00 materializza e valida input; non avvia MOSAIC o SUMO.
