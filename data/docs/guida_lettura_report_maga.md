# Guida alla lettura dei report MA-GA

Questa guida serve a leggere i report prodotti da `app.AdaptiveWindowMain`,
specialmente quelli composti da `AdaptiveWindowReportPrinter`.

Il report non e' un unico blocco omogeneo: e' una sequenza di diagnostiche.
La lettura piu' utile non e' dall'inizio alla fine, ma per domande.

## Lettura rapida in 7 passi

1. Parti da `EXECUTIVE SUMMARY`.
   Qui capisci se il run e' sano a colpo d'occhio: numero finestre, task
   valutati, violazioni deadline, problemi di coverage, CPU e banda.

2. Vai a `WORST WINDOWS`.
   Identifica le finestre davvero critiche. Una fitness enorme, ad esempio
   `10000000000...`, di solito indica una penalita' pesante da deadline o
   risorse, non un normale valore di costo.

3. Se ci sono deadline violate, leggi `DEADLINE CAUSE SUMMARY`,
   `TOP DEADLINE VIOLATIONS BY WINDOW` e poi il report
   `DEADLINE / DEGRADED BEST-EFFORT`.
   Queste sezioni dicono quali task sforano, di quanto, e quale componente
   domina lo sforamento.

4. Se la causa e' comunicativa, leggi `COMMUNICATION LATENCY SUMMARY` e
   `TOP COMMUNICATION-LATENCY DECISIONS`.
   Qui upload, download e `tau_n` sono separati. Se `uploadSum` e' molto alto,
   il problema e' quasi sempre banda effettiva troppo bassa rispetto a input e
   quota `p`.

5. Se si parla di banda, leggi sia `LINK BANDWIDTH...` sia
   `BANDWIDTH POOL...`.
   Il vincolo e' gerarchico: il singolo link source-aware e il pool condiviso
   devono essere entrambi rispettati.

6. Se si parla di cloud, handover o copertura, leggi `CLOUD GATEWAY...`,
   `ACCESS LINK DYNAMICITY...` e `MOBILITY...`.
   In `STRICT_GATEWAY`, il cloud deriva dall'access gateway attivo: non va
   letto come cloud sempre stabile.

7. Alla fine controlla `ADAPTIVE WINDOW SUMMARY`,
   `POPULATION REUSE DECISION SUMMARY`, `SYSTEM STATE SOURCE SUMMARY` e
   `CANDIDATE PREFILTER SUMMARY`.
   Queste sezioni spiegano perche' la finestra cambia durata, perche' una
   popolazione viene riusata o resettata, e se gli snapshot sono temporalmente
   allineati.

## Regola base

MA-GA minimizza la fitness `J`: piu' bassa e' meglio.

Non leggere mai `J` da sola. Incrociala sempre con:

- deadline violate;
- coverage insufficiente;
- violazioni CPU/banda;
- saturazioni CPU/banda;
- latenza comunicativa;
- decisioni LOCAL/EDGE/CLOUD/VEHICLE;
- riuso della popolazione.

Una finestra puo' avere risorse sature ma nessuna violazione: significa che il
repair ha rispettato il vincolo, ma il sistema sta lavorando al limite.

## Glossario essenziale

| Campo | Significato pratico |
| --- | --- |
| `idx` | Indice della finestra temporale. E' la chiave per collegare sezioni diverse. |
| `snapshot` | Nome dello snapshot JSON osservato in quella finestra. |
| `D` | Dinamicita' globale tra la finestra corrente e la precedente. |
| `Dv` | Variazione veicoli: ingresso/uscita di veicoli. |
| `Dt` | Variazione task: attivazioni, disattivazioni o cambiamenti task. |
| `Dr` | Variazione risorse computazionali. |
| `Dl` | Variazione qualita' link di accesso gateway-aware. |
| `level` | Classificazione della dinamicita': `STABLE`, `MODERATE`, ecc. |
| `suggested` | Riuso popolazione suggerito dalla sola dinamicita'. |
| `applied` | Riuso popolazione realmente applicato dopo correzioni di policy. |
| `J` / `finalBest` | Fitness finale migliore della finestra. Piu' bassa e' meglio. |
| `Pres` | Penalita' risorse nella fitness. |
| `p` | Quota di offloading. `0` locale, `1` full offloading, valori intermedi partial. |
| `completion` | Tempo stimato di completamento del task. |
| `deadline` | Deadline del task. |
| `lateness` | `completion - deadline`, tagliato a zero se il task rispetta la deadline. |
| `ratio` | Sforamento normalizzato rispetto alla deadline. |
| `coverage` | Tempo di copertura stimato del link remoto. |
| `phiLink` | Instabilita' del link. Vicino a `0` e' buono, vicino a `1` e' cattivo. |
| `phiHo` | Rischio handover. |
| `Pmob` | Penalita' mobility-aware. |
| `L(C)` | Latenza comunicativa aggregata della soluzione. |
| `L_i` | Latenza comunicativa di un task remoto. |
| `tau_n` | Ritardo di propagazione end-to-end del nodo remoto. |
| `sat` / `saturated` | Risorsa almeno al 95%, ma non necessariamente violata. |
| `viol` / `violation` | Risorsa oltre capacita' o vincolo non rispettato. |

## Sezioni principali

### `EXECUTIVE SUMMARY`

E' il sommario del run.

Da leggere cosi':

- `Executed windows`: quante finestre sono state eseguite.
- `Critical-event windows`: quante finestre sono partite per evento critico.
- `Population-reuse windows`: quante finestre hanno riusato almeno in parte la
  popolazione precedente.
- `Best final fitness`: miglior fitness finale osservata.
- `deadline violations`: indicatore piu' importante se stai validando vincoli
  temporali.
- `coverage insufficient`: se diverso da zero, i candidati remoti scelti non
  coprono abbastanza.
- `CPU violations` e `bandwidth violations`: devono idealmente restare zero.
- `CPU saturated` e `bandwidth saturated`: indicano pressione, anche se il
  repair evita violazioni.

### `TEMPORAL / DYNAMICITY SUMMARY`

Mostra come cambia lo scenario finestra per finestra.

Lettura tipica:

- `D` basso e `level=STABLE`: il riuso della popolazione e' ragionevole.
- `Dt` alto: cambia il carico task.
- `Dr` alto: cambiano CPU/banda/risorse.
- `Dl` alto: cambia la qualita' degli access link.
- `Dv` alto: entrano o escono veicoli.
- `suggested != applied`: la policy ha corretto la scelta, spesso per
  performance precedente o spike.

### `GA CONVERGENCE SUMMARY`

Serve a capire se il GA ha lavorato o si e' fermato presto.

Colonne chiave:

- `pop`: popolazione usata.
- `maxGen`: generazioni massime.
- `genRun`: generazioni realmente eseguite.
- `stop`: motivo di stop.
- `initBest` e `finalBest`: fitness iniziale e finale.
- `gain%`: miglioramento relativo.
- `last10` e `last50`: miglioramento nelle ultime generazioni.

Interpretazione:

- `STAGNATION_REACHED` con `last10=0`: il GA non migliorava piu'.
- `MAX_GENERATIONS_REACHED` con `last50 > 0`: il GA poteva forse migliorare
  ancora.
- `gain%=0` non e' sempre un problema: con warm start la soluzione iniziale puo'
  gia' essere buona.
- Fitness enormi tipo `1000000000011` indicano penalita' residue, spesso
  deadline violate.

### `DECISION / OFFLOADING SUMMARY`

Riassume dove vengono eseguiti i task.

Campi importanti:

- `LOCAL`, `EDGE`, `CLOUD`, `VEHICLE`: conteggio task per tipo di nodo.
- `localExec`, `partial`, `full`: tipo di decisione.
- `avgP`: media della quota di offloading.
- bucket `p=0`, `low`, `midLow`, `midHigh`, `high`, `p=1`: distribuzione di `p`.

Se `FULL_OFFLOADING` non appare mai, non e' automaticamente un bug, ma se te lo
aspetti devi controllare inizializzazione, mutazione e repair di
`offloadingRatio`.

### `DEADLINE CAUSE SUMMARY`

Conta le cause dominanti delle deadline violate.

Cause frequenti:

- `LOCAL_EXECUTION_BOTTLENECK`: la parte locale domina.
- `UPLOAD_BOTTLENECK`: upload troppo lungo.
- `REMOTE_EXECUTION_BOTTLENECK`: computazione remota troppo lenta.
- `DOWNLOAD_BOTTLENECK`: output remoto troppo pesante.
- `BASE_LATENCY_BOTTLENECK`: latenza base o `tau_n` troppo alta.
- `MIXED_LOCAL_REMOTE_BOTTLENECK`: ramo locale e remoto sono entrambi vicini al
  cammino critico.
- `COVERAGE_INSUFFICIENT`: copertura non sufficiente.

La sezione successiva, `TOP DEADLINE VIOLATIONS BY WINDOW`, mostra i task
specifici e va usata per capire se il problema e' sistemico o concentrato in
pochi task.

### `DEADLINE / DEGRADED BEST-EFFORT REPORT`

Questa sezione non certifica che un task sia matematicamente infeasible.

`DEGRADED_BEST_EFFORT` significa:

- il repair ha provato un insieme limitato di alternative coerenti;
- nessuna alternativa valutata ha rispettato la deadline;
- e' stata tenuta quella con lateness stimata minore;
- il task rimane fuori deadline nella soluzione finale.

Quindi questa sezione e' la conferma operativa delle violazioni residue.

### `CLOUD GATEWAY...`

Serve a validare il comportamento cloud gateway-aware.

Campi chiave:

- `mode: STRICT_GATEWAY`: il cloud passa dal gateway radio attivo.
- `legacyPlaceholderEnabled: false`: il vecchio placeholder cloud stabile non
  viene usato.
- `cloudCandidates`: candidati cloud disponibili nello snapshot.
- `cloudDecisions`: quante decisioni finali usano cloud.
- `gatewayAwareCloud`: decisioni cloud con metrica derivata dal gateway.
- `placeholderCloud`: deve essere `0` in `STRICT_GATEWAY`.
- `filteredCloud`: candidati rimossi dal prefilter per access link non
  disponibile.
- `avgPhiLink`, `maxPhiLink`: instabilita' link cloud.
- `avgCoverage`, `minCoverage`: copertura stimata per decisioni cloud.

`ACCESS LINK TRANSITIONS` mostra gli handover tra gateway.

### `ACCESS LINK DYNAMICITY...`

Questa sezione spiega `Dl(k)`.

Formula pratica:

```text
q_v(k) = 1 - phi_link(v, activeGateway)
Dl(k) = media delle variazioni assolute di q_v tra due finestre consecutive
```

Un link non disponibile ha `q_v = 0`.

Gli handover sono mostrati separatamente: incidono su `Dl` solo se cambiano la
qualita' effettiva del link.

### `HIERARCHICAL BANDWIDTH...`

La banda viene controllata su due livelli:

- `candidateId`: limite del singolo link source-aware;
- `poolId`: limite condiviso del dominio radio o V2V.

La condizione corretta e':

```text
sum bandwidth by candidateId <= candidate.availableBandwidth
sum bandwidth by poolId <= pool.availableBandwidth
```

`status=OK` significa che non ci sono violazioni ne' link ne' pool. Non significa
che la banda sia abbondante: se `saturatedLinks` o `saturatedPools` sono alti,
il sistema e' al limite.

Un caso importante:

- pool non saturo;
- link saturo;
- deadline violate per `UPLOAD_BOTTLENECK`.

In quel caso il collo di bottiglia e' il link per-candidato, non il pool
aggregato.

### `MOBILITY...`

Spiega la penalita' mobility-aware.

Componenti:

- `phiCov`: rischio coverage, di solito cresce quando completion supera la
  copertura.
- `phiLink`: instabilita' del link.
- `phiHo`: rischio handover.
- `Pmob`: somma pesata delle componenti.

Modelli:

- `LOCAL_CONVENTIONAL`: copertura convenzionale locale.
- `EDGE_GEOMETRIC`: distanza euclidea da edge e raggio.
- `CLOUD_GATEWAY_GEOMETRIC`: cloud attraverso access gateway.
- `V2V_SCALAR_RELATIVE_SPEED`: V2V con distanza euclidea e velocita' relativa
  scalare.

Se `phiCov=0` ma ci sono deadline violate, la mobilita' non e' la causa primaria
dello sforamento temporale.

### `COMMUNICATION LATENCY...`

La latenza comunicativa e' aggregata come somma:

```text
L(C) = sum_i L_i(C)
```

Per una scelta remota con `p > 0`:

```text
upload   = p * input / bandwidth
download = output / bandwidth
L_i      = upload + download + tau_n
```

Nota importante: il download usa l'output remoto integrale per ogni scelta
remota, non scala con `p`.

Quando una deadline ha causa `UPLOAD_BOTTLENECK`, questa sezione deve mostrare
upload individuali o `uploadSum` molto alti.

### `ADAPTIVE WINDOW SUMMARY`

Spiega come cambia la durata della finestra.

Campi principali:

- `currentDt`: durata della finestra corrente.
- `nextDt`: durata pianificata per la finestra successiva.
- `minDt` e `maxDt`: bound temporali calcolati.
- `TcovRef`: riferimento di copertura usato nei bound.
- `gaUsed`: runtime GA stimato usato nel calcolo dei bound.
- `gaObserved`: runtime GA realmente osservato.
- `runtimeBudget`: compatibilita' tra runtime osservato e finestra logica.
- `action`: `INCREASE`, `DECREASE`, `KEEP`, `CLAMP_TO_BOUNDS`, ecc.

Red flag:

- `EXCEEDS_CURRENT_AND_NEXT`: il GA ha impiegato piu' tempo sia della finestra
  corrente sia della prossima pianificata. In live integration serve una
  decisione esplicita su asincronia, budget o scheduling.

### `TEMPORAL TIMING` e `SYSTEM STATE SOURCE`

Queste sezioni separano il tempo logico del manager dal tempo salvato negli
snapshot.

In `JSON_SEQUENCE`, gli snapshot sono consumati per ordine. L'allineamento
temporale esatto non e' garantito quando la finestra adattiva cambia durata.

In `JSON_TIME`, invece, `futureLookAhead=true` e' anomalo: vuol dire che la
sorgente ha esposto uno snapshot piu' nuovo del tempo richiesto dal manager.

### `POPULATION REUSE DECISION SUMMARY`

Spiega perche' e' stato applicato `WARM_START`, `PARTIAL_RESTART` o
`COLD_START`.

Non dedurre la scelta solo da `D`.

Campi utili:

- `base`: scelta derivata dalla dinamicita'.
- `applied`: scelta finale.
- `prevPerf`: qualita' della soluzione precedente.
- `spike` e `severeSpike`: presenza di spike nelle componenti.
- `corrected`: `true` quando la policy ha cambiato la scelta base.
- `reason`: motivazione leggibile.

### `CANDIDATE PREFILTER SUMMARY`

Mostra quanti candidati sono stati rimossi prima dell'ottimizzazione.

Motivi tipici:

- `INSUFFICIENT_COVERAGE`: copertura insufficiente.
- `ACCESS_LINK_UNAVAILABLE`: access link non disponibile.
- `NO_TASK_FOR_SOURCE`: candidato riferito a una sorgente senza task attivi.

Il prefilter agisce sui candidati disponibili, non sulle decisioni finali gia'
prese.

## Checklist dei segnali rossi

| Segnale | Dove guardare | Interpretazione |
| --- | --- | --- |
| `deadline violations > 0` | Sezioni deadline e best-effort | Ci sono task finali fuori deadline. |
| Fitness enorme `1e12+` | `WORST WINDOWS`, GA summary | Penalita' pesante, spesso deadline residua. |
| `bandwidth violations = 0`, ma `BWsat` alto | Resource pressure, bandwidth details | Il repair rispetta i vincoli, ma la rete e' al limite. |
| Pool OK ma upload enorme | Link bandwidth details + latency | Il collo e' il link per-candidato, non il pool. |
| `coverage insufficient > 0` | Coverage summary, mobility | Candidati remoti scelti non coprono abbastanza. |
| `placeholderCloud > 0` in STRICT_GATEWAY | Cloud gateway summary | Regressione o configurazione non coerente. |
| `futureLookAhead=true` in JSON_TIME | System state source | Snapshot futuro esposto al manager. |
| `MAX_GENERATIONS_REACHED` e `last50 > 0` | GA convergence | Il GA potrebbe aver bisogno di piu' budget. |
| `FULL_OFFLOADING never appears` | Decision summary, diagnosis | Controllare policy di `offloadingRatio` se full era atteso. |
| `applied != suggested` spesso | Population reuse summary | La policy sta correggendo per performance o spike. |

## Lettura del report incollato

Il report incollato e' un run:

```text
JSON_SEQUENCE CONFIGURED_RUNTIME data/snapshots/temporal/scenarios/comprehensive_dynamic_validation 27
```

Sintesi:

- 27 finestre eseguite.
- 124 task valutati.
- 11 violazioni deadline, pari a circa 8,87%.
- 0 problemi di coverage finale.
- 0 violazioni CPU.
- 0 violazioni banda.
- 11 saturazioni CPU almeno al 95%.
- 21 saturazioni banda almeno al 95%.
- Cause deadline dominanti: 10 `UPLOAD_BOTTLENECK`, 1
  `MIXED_LOCAL_REMOTE_BOTTLENECK`.

Le finestre critiche sono quasi tutte concentrate in due punti:

- `idx=19`, `phase_19_severe_combined_spike`: 10 deadline violate,
  rate 76,92%, fitness enorme `10000000000246,270000`.
- `idx=7`, `phase_07_degraded_best_effort`: 1 deadline violata,
  rate 25%, fitness enorme `1000000000011,940600`.

### Causa principale

Il problema principale non e' CPU, non e' coverage e non e' violazione formale
di banda. E' latenza di upload.

Nella finestra 19:

- `COMMUNICATION LATENCY SUMMARY` riporta `L(C)=86,777759 s`.
- `uploadSum=75,327759 s`.
- Le 10 decisioni severe sono tutte `CLOUD` con `PARTIAL_OFFLOADING`.
- Ogni task degradato ha causa `UPLOAD_BOTTLENECK`.

Questo significa che il repair gerarchico della banda sta rispettando i vincoli,
ma le capacita' effettive dei link cloud per-candidato sono cosi' basse che
l'upload rende impossibile rispettare deadline da circa 1,55-2,05 secondi.

Nella finestra 7:

- un solo task sfora;
- decisione `CLOUD`, `PARTIAL_OFFLOADING`, `p=0,208482`;
- completion `3,925928 s` contro deadline `1,150000 s`;
- causa `MIXED_LOCAL_REMOTE_BOTTLENECK`;
- latenza comunicativa del task `3,591431 s`.

Qui il problema e' misto: ramo locale e remoto sono entrambi vicini al cammino
critico, ma la comunicazione pesa moltissimo.

### Banda

La sezione gerarchica dice sempre `status=OK`.

Questo e' coerente con:

- `violatedLinks=0`;
- `violatedPools=0`;
- molti link saturi.

Nel caso piu' critico, finestra 19:

- il pool `pool_rsu_south` usa solo il 10,2% della capacita';
- i link cloud per `vehicle_alpha`, `vehicle_bravo`, `vehicle_charlie` sono
  saturi al 100%;
- quindi il collo di bottiglia non e' il pool RSU condiviso, ma il limite del
  singolo link cloud/candidato.

### Mobilita e gateway

Il cloud gateway-aware sembra attivo correttamente:

- `mode: STRICT_GATEWAY`;
- `legacyPlaceholderEnabled: false`;
- `placeholderCloud=0`;
- le decisioni cloud hanno gateway e metriche associate.

La mobilita non sembra la causa primaria delle deadline violate:

- in finestra 19 `Pmob` e' alto, ma `phiCov=0`;
- le deadline sono classificate come `UPLOAD_BOTTLENECK`;
- coverage medio cloud circa 41-42 secondi, molto superiore ai completion time.

Quindi le decisioni cloud sono coperte, ma lente per trasferimento dati.

### GA e riuso popolazione

Il GA converge spesso per stagnazione. Questo non e' automaticamente un problema:
molte finestre sono stabili e riusano bene la popolazione.

Punti da notare:

- finestra 8 raggiunge `MAX_GENERATIONS_REACHED` e migliora molto:
  segnale che il caso e' piu' difficile;
- finestra 15 raggiunge `MAX_GENERATIONS_REACHED` e migliora ancora:
  possibile bisogno di piu' budget se quella finestra e' importante;
- finestra 20 usa `COLD_START` perche' la soluzione precedente era cattiva e
  c'era spike.

### Finestra adattiva

C'e' un warning runtime:

- finestra 20: `runtimeBudget=EXCEEDS_CURRENT_AND_NEXT`;
- `gaObserved=2,137110 s`;
- `currentDt=1,418320 s`;
- `nextDt=0,418320 s`.

Per una integrazione live, questo va discusso: il GA puo' finire dopo la
finestra logica disponibile.

### Conclusione sul report incollato

Il run valida bene diverse parti del sistema:

- repair CPU efficace;
- repair banda gerarchico efficace;
- cloud gateway-aware attivo;
- prefilter operativo;
- nessun problema finale di coverage.

Il problema aperto principale e':

```text
deadline residue caused by communication/upload time under severe cloud partial offloading
```

Le prossime verifiche tecniche dovrebbero concentrarsi su:

- capacita' dei link cloud per-candidato nello scenario severe spike;
- scelta di `p` nei task cloud sotto deadline strette;
- tradeoff tra restare locale, usare edge/V2V o ridurre offloading cloud;
- peso della latenza nella fitness e nel repair deadline;
- budget runtime della finestra adattiva dopo spike.

