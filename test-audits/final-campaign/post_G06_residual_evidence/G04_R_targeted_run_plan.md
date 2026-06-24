# Piano preliminare G04-R - run mobility controllata

Stato: **DA DISCUTERE PRIMA DELL IMPLEMENTAZIONE**

## Obiettivo principale

Eseguire una sola run controllata che produca una transizione temporale verificabile da rsu_0 a rsu_1 mentre almeno un task remoto e attivo e una strategia viene applicata.

## Test primari

- T-096: transizione gateway/RSU controllata e ricostruibile.
- T-098: effetto della transizione su una decisione remota applicata.

## Test secondari

- T-091: osservare almeno un assegnamento EDGE applicato.
- T-094: osservare coverageSufficient=false in un gene appartenente a una strategia applicata.

## Vincoli metodologici

- Non ripetere le 45 run G02.
- Non ripetere T-093, gia PASS_RECOVERED.
- Non ripetere high-density extended G03.
- Una sola run mirata, salvo fallimento tecnico non imputabile al core.
- Nessuna modifica Java prima della discussione delle classi e degli effetti.
- Preferire configurazione e scenario gia esposti; modifiche minime e isolate.

## Condizioni sperimentali richieste

- traiettoria deterministica rsu_0 -> rsu_1;
- intervallo di transizione noto;
- task remoto attivo prima e durante il cambio gateway;
- candidati EDGE disponibili;
- reporting di gateway, copertura, mobility model, geni applicati e timestamp;
- snapshot lag e violazioni runtime pari a zero;
- confronto prima/durante/dopo la transizione.

## Decisione operativa

`REQUIRED_SINGLE_TARGETED_CONTROLLED_MOBILITY_RUN`

Il presente file non autorizza ancora modifiche allo scenario o al codice.
