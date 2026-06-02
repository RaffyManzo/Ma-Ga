# Actual results - situazione attuale

Aggiornato dopo l'esecuzione della suite:

`data/snapshots/temporal/scenarios/comprehensive_dynamic_validation`

Sono stati confrontati due run da 27 finestre:

1. `JSON_SEQUENCE CONFIGURED_RUNTIME`
   - Replay diagnostico ordinale.
   - Consuma tutti i 27 snapshot in sequenza.
   - Usa il runtime GA configurato per rendere riproducibili gli esperimenti offline.

2. `JSON_TIME OBSERVED_RUNTIME`
   - Replay time-indexed.
   - Espone solo snapshot con `sourceTime <= managerTime`.
   - Usa il runtime GA osservato per verificare il comportamento operativo.

## Fonti usate per la rivalutazione

Questa lettura va intesa alla luce di:

- formalizzazione nel PDF locale `Formalizzazione_del_Mobility_Aware_Genetic_Algorithm_per_il_Computation_Offloading_nel_Vehicular_Edge_to_Cloud_Continuum-1.pdf`;
- README pubblico della repo GitHub `https://github.com/RaffyManzo/Ma-Ga`;
- documentazione locale `data/docs/documentazione_completa_codice_maga.md`.

La formalizzazione definisce il cromosoma come `C = [P | F | B | N]` e il gene come `g_i = (p_i, f_i, b_i, n_i)`. La quota `p_i` ha tre casi distinti:

- `p_i = 0`: esecuzione locale;
- `p_i = 1`: offloading completo;
- `0 < p_i < 1`: partial offloading.

Inoltre:

- `T(C) = max_i T_i(C)`;
- `L(C) = sum_i L_i(C)`;
- la deadline `T_i(C) <= D_i` e' un vincolo di ammissibilita';
- la banda aggregata deve rispettare `sum_i b_i <= Bmax`;
- la sostenibilita' mobility-aware richiede il confronto tra tempo richiesto e tempo di copertura;
- la finestra temporale deve rispettare `DeltaT_min <= DeltaT <= DeltaT_max` e viene aggiornata in base a `D_k`.

## Sintesi breve

La suite sta funzionando come diagnostica: non emergono problemi di coverage finale, non ci sono violazioni CPU aggregate, e il meccanismo di replay temporale evita look-ahead futuri in `JSON_TIME`.

Restano pero' tre aree aperte:

- deadline violations in finestre stressate o volutamente degradate;
- bandwidth repair ancora incompleta;
- classificazione/gestione del full offloading non stabile quando `p` e' molto vicino a 1.

Il risultato complessivo e' quindi positivo come stato di integrazione, ma non ancora "pulito" come validazione finale.

## Rivalutazione rispetto alla formalizzazione

### Giudizio complessivo

La suite conferma che l'architettura implementata e' vicina alla formalizzazione nelle parti centrali, ma non dimostra ancora una conformita' piena.

Il codice e i report sono allineati su:

- rappresentazione gene/cromosoma;
- calcolo separato di tempo, latenza, mobilita' e risorse;
- latenza aggregata come somma `L(C)`;
- replay `JSON_TIME` senza look-ahead futuro;
- finestra adattiva basata su dinamicita', coverage e runtime;
- riuso della popolazione tramite warm start, partial restart e cold start;
- repair CPU aggregato.

Restano invece non pienamente conformi o ancora prototipali:

- banda aggregata: la formalizzazione usa `Bmax`, mentre la repo dichiara che la banda e' ancora per link/candidato e non c'e' repair aggregato banda;
- cloud mobility: la formalizzazione lega il cloud al nodo di accesso/gateway, mentre il codice usa ancora un placeholder stabile;
- deadline: la formalizzazione tratta la deadline come vincolo di ammissibilita', mentre i report hanno ancora decisioni `DEGRADED_BEST_EFFORT`;
- full offloading: formalmente `p_i = 1`, quindi valori come `0,999979` restano partial se non viene introdotta esplicitamente una tolleranza di discretizzazione;
- run `JSON_TIME`: corretto operativamente, ma non garantisce copertura completa degli snapshot della suite quando si ferma a 27 finestre.

### Lettura corretta del full offloading

La prima interpretazione "soglia `p ~= 1`" va raffinata.

Secondo la formalizzazione:

- `p_i = 1` e' full offloading;
- `0 < p_i < 1` e' partial offloading.

Quindi nel report `JSON_TIME`, `phase_08_full_offloading_diagnostic` con `p = 0,999979` e' formalmente classificato correttamente come partial. Il problema non e' solo il printer: se quello scenario e' costruito per diagnosticare il full offloading, allora initializer, mutation, repair o `OffloadingRatioPolicy` devono produrre o snapparsi esattamente a `p = 1`.

Un epsilon puo' essere introdotto solo se diventa parte esplicita del modello implementativo, ad esempio:

- `p >= 1 - eps` viene normalizzato a `1.0` nel repair;
- report, fitness e decision type usano la stessa normalizzazione;
- la documentazione dichiara che il GA continuo viene discretizzato agli estremi.

Senza questa scelta esplicita, il report "FULL_OFFLOADING never appears" e' formalmente giusto nel run osservato.

### Lettura corretta delle deadline violations

La formalizzazione non considera la deadline una metrica soft ordinaria: `T_i(C) <= D_i` e' una condizione di ammissibilita'. Per questo motivo:

- `DEGRADED_BEST_EFFORT` e' utile come fallback ingegneristico;
- ma non va letto come piena conformita' alla formalizzazione;
- una violazione deadline residua deve essere marcata come expected degraded, infeasible-by-construction oppure open issue.

Rivalutazione dei casi:

| Snapshot | Stato formale |
| --- | --- |
| `phase_07_degraded_best_effort` | Probabile expected degraded: va dichiarato esplicitamente come caso non ammissibile o best-effort intenzionale. |
| `phase_10_remote_cpu_contention` | Non conforme finche' non si dimostra che nessuna alternativa ammissibile rispetta la deadline. |
| `phase_11_bandwidth_pressure` | Non conforme nel run sequenziale: e' anche collegato al repair banda mancante. |

Quindi le deadline violations non rendono inutile la suite; al contrario, indicano esattamente dove la distanza dalla formulazione resta da chiudere.

### Lettura corretta delle risorse

La parte CPU e' la piu' matura:

- la formalizzazione richiede `sum_i f_i <= Fmax_n` per nodo remoto;
- i report mostrano `CPU violations = 0`;
- le saturazioni CPU sono segnali di pressione, non violazioni finali.

La banda invece resta il principale scostamento formale:

- la formalizzazione richiede `sum_i b_i <= Bmax`;
- il README della repo dichiara che la banda e' ancora modellata per link/candidato;
- i report mostrano violazioni banda, soprattutto nel profilo `JSON_TIME OBSERVED_RUNTIME`.

Quindi la banda non e' solo un dettaglio di calibrazione: e' il principale gap tra formalizzazione e implementazione corrente.

### Lettura corretta della mobilita'

La formalizzazione accetta una prima stima conservativa della copertura per edge/RSU e V2V. Da questo punto di vista, il modello corrente e' coerente come prima implementazione:

- edge usa distanza, raggio e velocita';
- V2V usa distanza e velocita' relativa scalare;
- il prefilter rimuove candidati con coverage insufficiente;
- `coverage insufficient = 0` nei risultati finali.

Il cloud pero' e' diverso. Nella formalizzazione, la copertura cloud non e' la distanza dal datacenter, ma il tempo residuo del collegamento di accesso verso la rete. Nel codice attuale, invece, il report dichiara:

- coverage cloud fissato a `300 s`;
- link instability cloud fissata a `0`;
- modello `CLOUD_STABLE_PLACEHOLDER`.

Questo e' accettabile solo come assunzione sperimentale dichiarata. Non e' ancora una validazione completa della parte mobility-aware cloud.

### Lettura corretta del replay temporale

Il comportamento del profilo `JSON_TIME OBSERVED_RUNTIME` e' coerente con la formalizzazione del gestore temporale:

- il manager osserva lo stato al proprio tempo logico;
- non deve vedere snapshot futuri;
- se non esiste uno snapshot esatto, usa lo stato passato piu' recente;
- la prossima finestra dipende da dinamicita', runtime e coverage.

Per questo motivo gli snapshot saltati o ripetuti in `JSON_TIME` non sono un bug. Sono l'effetto normale di un replay time-indexed con finestra adattiva.

La conseguenza pratica e':

- `JSON_SEQUENCE CONFIGURED_RUNTIME` serve per regressione completa della suite;
- `JSON_TIME OBSERVED_RUNTIME` serve per validazione operativa del gestore temporale;
- i due report non vanno confrontati come se fossero due run equivalenti dello stesso esperimento.

### Estensioni implementative rispetto alla formalizzazione

La repo aggiunge alcune politiche operative non presenti in forma pura nella legge matematica:

- correzione del riuso popolazione in base alla performance precedente;
- fallback `DEGRADED_BEST_EFFORT`;
- profilo `CONFIGURED_RUNTIME` per replay riproducibili;
- report diagnostici dettagliati;
- prefilter dei candidati.

Queste estensioni sono ragionevoli per un prototipo, ma devono essere etichettate come policy implementative. In particolare, quando il report mostra `suggested = PARTIAL_RESTART` e `applied = WARM_START` o `COLD_START`, non e' necessariamente una violazione: puo' essere una correzione dovuta alla qualita' precedente della soluzione. Va pero' documentato come estensione della formalizzazione base.

## Report 1 - JSON_SEQUENCE + CONFIGURED_RUNTIME

Questo run e' il riferimento migliore per capire se tutti gli snapshot della suite vengono attraversati.

### Numeri principali

| Metrica | Valore |
| --- | ---: |
| Finestre eseguite | 27 |
| Snapshot coperti | 27/27 |
| Task evaluations | 95 |
| Deadline violations | 5, cioe' 5,263158% |
| Coverage insufficient | 0 |
| CPU violations | 0 |
| Bandwidth violations | 1 |
| CPU saturated >= 95% | 17 |
| Bandwidth saturated >= 95% | 48 |
| Best final fitness | 0,398887 |

### Interpretazione

Il replay sequenziale attraversa tutte le fasi:

- da `phase_00_baseline`;
- fino a `phase_26_final_stable_repeat`;
- senza saltare snapshot.

La differenza tra `managerTime` e `sourceTime` cresce perche' gli snapshot vengono consumati ordinalmente mentre la finestra adattiva cambia durata. Questo e' accettabile per il run diagnostico, ma non va letto come comportamento operativo time-driven.

### Deadline e degraded best effort

Le deadline violations sono concentrate in tre scenari:

| Snapshot | Violazioni | Causa dominante |
| --- | ---: | --- |
| `phase_07_degraded_best_effort` | 1 | `MIXED_LOCAL_REMOTE_BOTTLENECK` |
| `phase_10_remote_cpu_contention` | 2 | `REMOTE_EXECUTION_BOTTLENECK` |
| `phase_11_bandwidth_pressure` | 2 | `MIXED_LOCAL_REMOTE_BOTTLENECK` |

Nel dettaglio:

- `phase_07_degraded_best_effort` conferma il caso degradato atteso: `task_best_effort` resta fuori deadline e viene mantenuta la scelta con lateness minima tra le alternative valutate.
- `phase_10_remote_cpu_contention` evidenzia che il collo di bottiglia e' il tempo di esecuzione remoto su edge condiviso.
- `phase_11_bandwidth_pressure` evidenzia un problema misto locale/remoto, con latenza di comunicazione molto alta e offloading ratio basso.

Il report degraded e' coerente con il report deadline:

| Snapshot | Degraded best-effort |
| --- | ---: |
| `phase_07_degraded_best_effort` | 1 |
| `phase_10_remote_cpu_contention` | 2 |
| `phase_11_bandwidth_pressure` | 2 |

### Risorse

La parte CPU sembra riparata a livello aggregato:

- `CPU violations = 0`;
- restano molte saturazioni, ma non superamenti aggregati finali.

La banda invece resta un punto aperto:

- `Bandwidth violations = 1`;
- la violazione esplicita compare in `phase_19_severe_combined_spike`;
- le saturazioni banda sono frequenti, 48 finestre/link saturi nel totale diagnostico.

Il report stesso conferma la diagnosi:

- CPU aggregate repair efficace;
- bandwidth repair da promuovere da `OpenIssue` a implementazione.

### Mobilita' e coverage

Il coverage finale e' buono:

- `coverage insufficient = 0`;
- nessun task finale risulta senza copertura sufficiente;
- il prefilter rimuove correttamente candidati con `INSUFFICIENT_COVERAGE` o `NO_TASK_FOR_SOURCE`.

Il modello cloud resta pero' provvisorio:

- coverage cloud fissato a `300 s`;
- instabilita' link cloud fissata a `0`;
- nessun gateway radio cloud e' ancora modellato.

Quindi le decisioni cloud sono ancora supportate da un placeholder stabile, non da un modello radio completo.

### Finestra adattiva

La finestra adattiva reagisce come previsto:

- cresce durante fasi stabili;
- viene clampata quando la copertura temporale scende;
- si riduce nello spike severo;
- poi ricomincia a crescere in recovery.

Nel profilo `CONFIGURED_RUNTIME` compare un warning:

- `phase_20_recovery_after_spike`;
- `runtimeBudget = EXCEEDS_NEXT`;
- `gaObserved = 0,306246 s`;
- `nextDt = 0,150001 s`.

Questo non rompe il replay offline, ma e' un segnale operativo importante: se si usa il runtime configurato per calcolare `DeltaT_min`, si puo' pianificare una finestra successiva piu' corta del runtime GA osservato.

## Report 2 - JSON_TIME + OBSERVED_RUNTIME

Questo run e' il riferimento migliore per verificare il comportamento time-driven.

### Numeri principali

| Metrica | Valore |
| --- | ---: |
| Finestre eseguite | 27 |
| Task evaluations | 117 |
| Deadline violations | 3, cioe' 2,564103% |
| Coverage insufficient | 0 |
| CPU violations | 0 |
| Bandwidth violations | 9 |
| CPU saturated >= 95% | 29 |
| Bandwidth saturated >= 95% | 52 |
| Best final fitness | 0,400248 |
| Runtime budget warnings | 0 |

### Interpretazione

Il replay time-indexed non guarda mai nel futuro:

- `futureLookAhead = false` in tutte le righe;
- quando non c'e' uno snapshot esatto, viene riusato lo snapshot passato piu' recente.

Questo e' corretto per un replay operativo.

La conseguenza e' che 27 finestre non equivalgono a 27 snapshot distinti. Alcune fasi vengono saltate, altre ripetute.

Snapshot saltati nel run da 27 finestre:

- `phase_04_v2v_out_of_range`;
- `phase_06_deadline_repairable`;
- `phase_09_tau_tradeoff`;
- `phase_11_bandwidth_pressure`;
- `phase_13_vehicle_exit`;
- `phase_16_resource_recovery`;
- `phase_25_local_cpu_sharing_open_issue`;
- `phase_26_final_stable_repeat`.

Snapshot ripetuti:

- `phase_17_link_degradation_reachable`;
- `phase_18_link_loss_outside`;
- `phase_19_severe_combined_spike`;
- `phase_20_recovery_after_spike`.

Quindi questo run e' operativo, ma non e' un test di copertura completa della suite. Per coprire tutte le fasi serve `JSON_SEQUENCE`, oppure una policy di esecuzione time-driven che continui fino al tempo finale dello scenario invece che fermarsi a 27 finestre.

### Deadline e degraded best effort

Le deadline violations scendono a 3 perche' `phase_11_bandwidth_pressure` non viene visitata in questo run.

| Snapshot | Violazioni | Causa dominante |
| --- | ---: | --- |
| `phase_07_degraded_best_effort` | 1 | `MIXED_LOCAL_REMOTE_BOTTLENECK` |
| `phase_10_remote_cpu_contention` | 2 | `REMOTE_EXECUTION_BOTTLENECK` |

Il degraded report e' coerente:

| Snapshot | Degraded best-effort |
| --- | ---: |
| `phase_07_degraded_best_effort` | 1 |
| `phase_10_remote_cpu_contention` | 2 |

Il caso `phase_11_bandwidth_pressure` non sparisce per miglioramento dell'algoritmo: non viene proprio campionato dal replay time-driven con questa sequenza di finestre.

### Risorse

Il profilo osservato non mostra warning di runtime budget:

- `Runtime budget warnings = 0`;
- `DeltaT_min` segue il runtime GA osservato.

Questo e' piu' robusto per integrazione live.

La pressione sulle risorse resta pero' piu' evidente:

- `Bandwidth violations = 9`;
- la causa e' anche la ripetizione di snapshot severi durante finestre corte;
- `CPU violations = 0`, quindi il repair CPU resta efficace.

### Full offloading

Nel report time-driven compare la diagnosi:

> FULL_OFFLOADING never appears.

Il caso piu' chiaro e' `phase_08_full_offloading_diagnostic`:

- `task_full_required`;
- `p = 0,999979`;
- nel report decisionale viene ancora conteggiato come partial, non full.

Nel run sequenziale invece lo stesso scenario arriva a `p = 1,000000` e viene conteggiato come `full = 1`.

Questo indica una instabilita' nella gestione degli estremi della quota di offloading:

- se la classificazione usa la definizione formale esatta, `0,999979` e' correttamente partial;
- mutazione, repair o inizializzazione possono lasciare `p` a pochi epsilon da 1;
- se lo scenario richiede full offloading, il repair deve normalizzare il gene a `p = 1`;
- in alternativa va dichiarata una tolleranza implementativa comune, ad esempio `p >= 1 - eps` normalizzato a `1.0`.

Prima di cambiare la fitness conviene controllare:

- inizializzazione di `offloadingRatio`;
- mutazione;
- repair/clamp;
- classificazione `PARTIAL_OFFLOADING` vs `FULL_OFFLOADING`;
- eventuale epsilon comune per report, repair e diagnostica.

## Stato dei sottosistemi

### OK o quasi OK

- Replay `JSON_SEQUENCE`: copre tutti gli snapshot.
- Replay `JSON_TIME`: non espone snapshot futuri.
- Prefilter candidati: rimuove casi senza task o con coverage insufficiente.
- Coverage finale: nessun problema osservato.
- CPU aggregate repair: nessuna violazione CPU.
- Diagnostica deadline/degraded: coerente e utile.
- Adaptive window: reattiva a stabilita', spike e limiti di coverage.

### Da chiudere

- Bandwidth aggregate repair: ancora aperto.
- Deadline repair: restano casi degradati e stress cases non risolti.
- Full offloading: gli estremi `p = 0` e `p = 1` vanno prodotti/normalizzati in modo coerente con la definizione formale.
- JSON_TIME coverage della suite: 27 finestre non bastano a visitare tutti i 27 snapshot.
- Cloud mobility model: placeholder stabile, non modello radio completo.
- Local CPU sharing: scenario ancora marcato come open issue, anche se nel run sequenziale non produce violazioni.

## Lettura dei risultati

La situazione attuale e' questa:

- il sistema non e' rotto;
- i report sono coerenti tra loro;
- le differenze tra Report 1 e Report 2 sono spiegabili dalla diversa modalita' di replay;
- `JSON_SEQUENCE` e' piu' adatto alla validazione completa della suite;
- `JSON_TIME` e' piu' adatto alla verifica operativa, ma puo' saltare snapshot e ripeterne altri;
- le violazioni residue sono concentrate e diagnosticabili.

La priorita' tecnica piu' alta e' la banda, non la CPU.

La priorita' diagnostica piu' alta e' rendere esplicito che:

- alcune deadline violations sono casi `DEGRADED_BEST_EFFORT` attesi;
- altre sono veri segnali di repair incompleto;
- un run time-indexed da 27 finestre non deve essere usato come prova di copertura completa dei 27 snapshot.

## Prossimi passi consigliati

1. Definire quali deadline violations sono expected failures della suite.
   - `phase_07_degraded_best_effort` sembra intenzionalmente degradato.
   - `phase_10_remote_cpu_contention` e `phase_11_bandwidth_pressure` vanno classificati come stress test non ancora riparati oppure come expected degraded.

2. Implementare o completare il bandwidth repair.
   - In sequenza resta almeno una violazione esplicita.
   - In time-driven le violazioni aumentano quando gli snapshot severi vengono riusati.

3. Sistemare la gestione del full offloading.
   - Se lo scenario richiede full, far produrre al repair `p = 1.0`.
   - Se si usa una tolleranza, dichiararla e applicarla a repair, fitness, report e diagnostica.

4. Separare le metriche di copertura per modalita' di replay.
   - `JSON_SEQUENCE`: aspettarsi 27 snapshot distinti.
   - `JSON_TIME`: aspettarsi monotonicita' temporale e `futureLookAhead=false`, non copertura completa.

5. Aggiungere una sintesi "skipped/repeated snapshots" nel report `JSON_TIME`.
   - Questo renderebbe immediata la lettura dei salti temporali.

6. Decidere il modello cloud definitivo.
   - O dichiarare esplicitamente il placeholder come assunzione sperimentale.
   - Oppure introdurre gateway/access link cloud nel modello di mobilita'.

7. Rieseguire entrambi i profili dopo le correzioni.
   - `JSON_SEQUENCE CONFIGURED_RUNTIME` per regressione completa.
   - `JSON_TIME OBSERVED_RUNTIME` per controllo operativo e runtime budget.

## Conclusione

La nuova suite sta gia' facendo il suo lavoro: mette sotto stress replay temporale, adaptive window, population reuse, prefilter, deadline repair, risorse, mobilita' e comunicazione.

Lo stato attuale e' buono come base diagnostica, ma non ancora conclusivo come validazione finale. La prossima iterazione dovrebbe concentrarsi su bandwidth repair, gestione formale degli estremi `p = 0/1` e criteri espliciti di expected degraded per le finestre di stress.
