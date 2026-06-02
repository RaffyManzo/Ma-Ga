# Actual results - riallineamento post gateway e banda

Questo file sostituisce il report storico precedente, che descriveva lo stato
prima dei commit su gateway radio, access link e repair gerarchico della banda.
I numeri dei run precedenti restano utili come riferimento storico, ma non
vanno piu' letti come validazione finale del codice corrente.

## Fonti usate per il riallineamento

- Diff e log dei commit successivi a `4089f77`, in particolare la sequenza da
  `c5dbc50` a `34aab0c`.
- Ispezione del codice corrente in `src`.
- Smoke test locale su due finestre dello scenario:

```text
java -cp "out/codex-classes-local;out/codex-lib/*" app.AdaptiveWindowMain JSON_SEQUENCE CONFIGURED_RUNTIME data/snapshots/temporal/scenarios/comprehensive_dynamic_validation 2
```

## Stato del codice corrente

Le conclusioni principali cambiano rispetto al report storico:

- Il cloud operativo e' gateway-aware. In modalita' `STRICT_GATEWAY`,
  copertura e instabilita' CLOUD derivano dall'access gateway attivo.
- Gli snapshot includono gateway, access link e pool di banda:
  `AccessGatewaySnapshot`, `AccessLinkSnapshot`, `BandwidthPoolSnapshot`.
- Il prefilter riduce solo `candidateNodes` e propaga nello snapshot filtrato
  gateway, access link e pool di banda.
- La banda e' controllata su due livelli: link source-aware (`candidateId`) e
  pool condiviso (`poolId`). Un pool `GLOBAL` riproduce `Bmax`; pool
  `GATEWAY` e `DIRECT_V2V` permettono domini radio separati.
- Il repair aggregato della banda e' implementato: il flusso usa
  `BandwidthAggregateRepairOperator` e `BandwidthPoolAggregateRepairOperator`
  dopo il repair del singolo gene e dopo la CPU aggregata.
- `Dv(k)` misura solo churn dei veicoli. `Dl(k)` misura la variazione della
  qualita' dell'access link attivo.

## Esito dello smoke test

Lo smoke test su 2 finestre serve solo a verificare che il percorso aggiornato
sia eseguibile e che i nuovi report espongano le informazioni attese. Non e'
una validazione statistica completa.

Risultati osservati:

- esecuzione completata senza crash;
- modalita' cloud riportata come `STRICT_GATEWAY`;
- `legacyPlaceholderEnabled: false`;
- report gateway, access link dynamicity e pool banda presenti;
- nessuna violazione deadline, coverage, CPU o banda nelle prime 2 finestre;
- pool di banda letti nello scenario: 9;
- candidati cloud nello snapshot osservato: 6;
- `placeholderCloud = 0`;
- filtraggio candidati nella prima finestra: 18 candidati osservati, 15
  candidati mantenuti, rimozioni dovute a `INSUFFICIENT_COVERAGE`;
- `Dl(k) = 0` tra le prime due finestre osservate, coerente con access link
  invariati.

## Cosa resta da validare

Il quadro corrente e' piu' coerente, ma non basta uno smoke test a dichiarare
chiusa la validazione sperimentale. Restano da rieseguire:

- run completo `JSON_SEQUENCE CONFIGURED_RUNTIME` su tutte le finestre dello
  scenario `comprehensive_dynamic_validation`;
- run `JSON_TIME OBSERVED_RUNTIME`;
- stress test con pressione di banda, contesa CPU remota e deadline strette;
- scenari con decisioni CLOUD effettive e handover di gateway;
- scenari con saturazione di pool `GLOBAL`, `GATEWAY` e `DIRECT_V2V`;
- confronto tra ridimensionamento proporzionale link+pool e policy piu'
  sofisticate;
- verifica del limite V2V ancora scalare: distanza euclidea e velocita'
  relativa `abs(v_source - v_target)`, senza vettori di traiettoria.

## Lettura corretta dei risultati storici

Le vecchie sezioni che indicavano:

- stato pre-fix della banda aggregata;
- assenza del modello gateway-aware per CLOUD;
- modello CLOUD storico non basato su access link;
- `phase_11_bandwidth_pressure` collegato alla diagnostica precedente;

descrivono una versione precedente del progetto. Dopo i commit recenti, quei
punti non sono piu' lo stato del codice. Possono essere usati solo per capire
quali issue sono state risolte e quali esperimenti vanno ripetuti.
