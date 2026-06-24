# Chiusura G05 e passaggio a G06

## Stato finale G05

G05 è chiusa con lo stato:

`PASS_WITH_RESOURCE_AND_TEMPORAL_LIMITS`

Le sei run principali hanno superato i controlli tecnici:

- 6/6 `LITERATURE_SMOKE_TEST_PASSED`;
- snapshot lag massimo pari a 0;
- violazioni runtime pari a 0;
- nessuna modifica Java rispetto al freeze.

La configurazione `R-LOCALCPU-01` è stata ripetuta due volte in isolamento. Le tre osservazioni complessive mostrano offloading remoto applicato e un lungo intervallo finale senza nuove strategie. Il verdetto definitivo è:

`PASS_WITH_REPRODUCIBLE_TEMPORAL_LIMIT`

## Risultati utilizzabili nella tesi

G05 mostra tre fenomeni distinti:

1. la riduzione della CPU locale modifica realmente le decisioni del MA-GA e favorisce l'offloading remoto;
2. le risorse remote degradate vengono spesso evitate, ma questo non dimostra direttamente l'attivazione del repair;
3. sotto pressione elevata, soprattutto nella variante V2V high-density, aumentano runtime e risultati stale.

Il confronto con G02 usa 15 baseline già esistenti. Poiché G02 dura 300 s e G05 dura 180 s, vengono confrontati solo tassi, percentuali e rapporti normalizzati.

## Evidenze residue

- `T-093`: `PASS_RECOVERED`;
- `T-091`: evidenza parziale, supportata da EDGE applicato nelle recovery LOCALCPU ma non dalla variante EDGECPU;
- `T-094`: `NON_OSSERVATO` nelle strategie applicate;
- repair/fallback: `NOT_EXPOSED` nei trace correnti.

Queste evidenze non richiedono nuove simulazioni prima di G06.

## Prossima fase

La sequenza operativa diventa:

`G06 → audit evidenze residue → eventuali run G04-R → G02B → G07`

G06 dovrà concentrarsi su runtime, stale policy, riuso della popolazione, finestra temporale e trigger. Dopo G06 si rivaluteranno T-091 e T-094 prima di decidere eventuali run mobility mirate.

## Integrità e provenienza

- Core congelato: `5a9477735a3d707a5f000a64653cd2a6fc7f2007`
- Commit sorgente sintesi G05: `38a86ae77b49c99a0bef5cb75524c1d9007b6141`
- Bundle F3 SHA-256: `ddae03e4fb0f24368082e97ea71442663876138f37f57c2ffc43c8f13581d0f7`
- Matrice completa G05 SHA-256: `916d62801d135b50b1760da7d86ed648a11b9d6176189958f9f5d274d7aabdd2`
- Matrice semplificata G05 SHA-256: `f4cead82aa57f3443f13ff618b4df0273039d4f1b99e404a804554412e1a257f`

Il commit di chiusura formale viene creato dallo script F4. Il relativo hash è registrato nel bundle finale locale.
