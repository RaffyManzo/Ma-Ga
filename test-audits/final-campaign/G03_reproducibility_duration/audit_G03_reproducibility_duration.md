# Audit finale G03 — riproducibilità e durata

## Esito

**PASS_WITH_OPTIONAL_STRESS_LIMIT**

- Azioni pianificate: 11.
- Azioni concluse con PASS: 10.
- Materializzazioni ripetute: 2/2 PASS.
- Run MOSAIC concluse con PASS: 8.
- Prova extended stress opzionale: limite osservato a 464,0 s su 600 s.
- Modifiche Java: nessuna.
- Core scientifico: invariato rispetto a `5a9477735a3d707a5f000a64653cd2a6fc7f2007`.

## T-010 e T-014

Le due materializzazioni `REP-01-A` e `REP-01-B`, ottenute con configurazione e seed uguali,
sono entrambe validate. I file grezzi differiscono per campi documentali e identificativi attesi,
ma il confronto normalizzato è `LOGICALLY_IDENTICAL` con zero differenze logiche.

## Ripetibilità runtime

Le tre run `CFG-RUNTIME-REPEAT` hanno prodotto gli stessi contatori funzionali principali:

- task generati e attivati: 9.306;
- task rimossi alla deadline: 9.241;
- task pendenti finali: 65;
- picco pending: 79;
- snapshot richiesti/risolti: 3.000/2.981.

Il runtime medio del GA varia tra 0.034014784 s e 0.044145135 s.
Il coefficiente di variazione delle medie è 11.184%.
Anche submitted/applied/stale possono variare perché il GA è asincrono e dipende dalla temporizzazione
wall-clock dell'host. Questo non altera la riproducibilità del workload e dello stato simulato.

## T-018 — durata extended

Le cinque run nominali da 600 s sono tutte PASS:

- task generati complessivi: 96337;
- GA submitted/completed/applied: 6021/
  6021/
  5968;
- stale complessivi: 53;
- stale pesato: 0.880252%;
- snapshot lag massimo: 0 s;
- violazioni runtime: 0;
- ultima strategia applicata: tra 599,9 s e 600,0 s.

## Prova opzionale H-S-EXT-01

La run high-density/WL-S da 600 s è arrivata a 464,0 s, pari al 77,3%.
MOSAIC ha interrotto la federazione dopo il watchdog: `VehicleGetTaxiFleet` ha ricevuto una
`SocketException` perché la connessione TraCI è stata interrotta dal software host.

La run non ha prodotto i report finali e non è classificata PASS. Non viene ripetuta:
`CFG-H-S-EXT` è opzionale e `high_density` è uno stress profile. L'interruzione è registrata
come limite endurance osservato, non come regressione dei profili operativi stabili.

## Chiusura

G03 chiude con gli obiettivi obbligatori soddisfatti e con un limite stress documentato.
La sequenza successiva è `G04 → G05 → G06 → G02B → G07`.
