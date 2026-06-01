# Timeline della sequenza `comprehensive_dynamic_validation`

La cartella contiene una sola sequenza temporale completa. Ogni file rappresenta la fotografia successiva dello scenario.

| idx | t | fase | obiettivo | verifica attesa |
|---:|---:|---|---|---|
| `000` | `0.0 s` | 00 - baseline | LOCAL / EDGE / CLOUD disponibili in uno scenario leggero. | Snapshot valido; nessuna violazione CPU o banda; TcovRef atteso = 35 s. |
| `001` | `5.0 s` | 01 - stabilità | Snapshot fisicamente identico. | D=0; riuso della popolazione favorito; TcovRef invariato. |
| `002` | `10.0 s` | 02 - regressione TcovRef | Aggiunta di candidati EDGE inferiori e di un candidato V2V. | TcovRef deve restare 35 s: ogni veicolo pesa una sola volta e i candidati V2V non entrano nella media. |
| `003` | `15.0 s` | 03 - prefilter strutturale | EDGE vicino al limite, EDGE fuori copertura e candidato associato a sorgente senza task. | edge_fragile_valid mantenuto; edge_outside rimosso; edge_idle rimosso per NO_TASK_FOR_SOURCE. |
| `004` | `20.0 s` | 04 - V2V fuori raggio | Il target V2V supera il raggio di comunicazione. | Il candidato VEHICLE per vehicle_a viene filtrato per copertura insufficiente; Dl aumenta. |
| `005` | `25.0 s` | 05 - V2V velocità uguali | Il target V2V torna vicino con velocità scalare uguale alla sorgente. | Candidato V2V nuovamente valido; tempo di copertura clampato al massimo convenzionale tramite epsilonSpeed. |
| `006` | `30.0 s` | 06 - deadline riparabile | Task impossibile in locale ma facilmente eseguibile su EDGE. | degradedBestEffort=0; il repair deve trovare una configurazione remota ammissibile. |
| `007` | `35.0 s` | 07 - DEGRADED_BEST_EFFORT | Task oltre deadline per tutte le alternative valutabili. | Il report deve mostrare DEGRADED_BEST_EFFORT e lateness positiva; CLOUD non va privilegiato per regola fissa. |
| `008` | `40.0 s` | 08 - full offloading | Scenario costruito per favorire una quota p prossima o uguale a 1. | Verificare se p=1 compare. Se resta assente, investigare initializer, mutation e repair prima della fitness. |
| `009` | `45.0 s` | 09 - semantica tau_n | Due EDGE equivalenti tranne per tau_n. | edge_low_tau deve essere più competitivo; tau_n va conteggiato una sola volta per scelta remota. |
| `010` | `50.0 s` | 10 - repair CPU aggregato | Due task competono per lo stesso executionNodeId remoto. | Il report finale non deve mostrare CPU violations; il repair aggregato deve ridimensionare o riallocare. |
| `011` | `55.0 s` | 11 - pressione banda | Due task remoti con trasferimenti elevati verso lo stesso EDGE. | Osservare saturazione e scelte del repair. Il modello gerarchico dei pool radio resta open issue. |
| `012` | `60.0 s` | 12 - ingresso veicolo | Ingresso di un nuovo veicolo con un task attivo. | Dv e Dt devono aumentare; il riuso della popolazione deve adattarsi al nuovo task. |
| `013` | `65.0 s` | 13 - uscita veicolo | Il veicolo introdotto nella finestra precedente scompare. | Dv e Dt devono aumentare nuovamente. |
| `014` | `70.0 s` | 14 - spike task | Aumento improvviso del numero di task attivi. | Dt elevato; possibile riduzione della finestra e riuso più prudente. |
| `015` | `75.0 s` | 15 - degrado risorse | Caduta della CPU disponibile su edge_center durante lo spike. | Dr elevato; maggiore pressione sul repair e possibile cold start. |
| `016` | `80.0 s` | 16 - recupero risorse | Ritorno allo scenario leggero. | Dr e Dt riflettono il recupero; nessuna violazione aggregata residua. |
| `017` | `85.0 s` | 17 - degrado link | I veicoli si avvicinano al bordo della copertura EDGE senza superarlo. | EDGE mantenuti dal prefilter; Dl aumenta; TcovRef si riduce; phi_link elevato ma < 1. |
| `018` | `90.0 s` | 18 - perdita copertura EDGE | I veicoli superano il raggio di edge_center. | Candidati EDGE filtrati per INSUFFICIENT_COVERAGE; LOCAL e CLOUD restano disponibili. |
| `019` | `95.0 s` | 19 - spike severo combinato | Spike task, link fragili e degrado risorse nello stesso istante. | Dt, Dl e Dr elevati; COLD_START atteso se le soglie correnti rilevano il picco severo. |
| `020` | `100.0 s` | 20 - recupero | Ritorno alla baseline dopo lo spike severo. | Il manager osserva una forte variazione inversa e ristabilisce gradualmente il riuso. |
| `021` | `105.0 s` | 21 - raw vs filtered, sorgente inattiva | Veicolo e candidati presenti nello snapshot grezzo, ma nessun task attivo per la sorgente. | I candidati di vehicle_idle_2 sono esclusi dalla vista ottimizzabile per NO_TASK_FOR_SOURCE. |
| `022` | `110.0 s` | 22 - raw vs filtered, attivazione task | Il task attiva candidati già presenti nello snapshot grezzo. | Dt aumenta; Dr e Dl non devono simulare l'apparizione fisica di nuovi nodi. |
| `023` | `115.0 s` | 23 - raw vs filtered, disattivazione task | Il task viene rimosso, ma veicolo e candidati restano osservati fisicamente. | Dt aumenta; Dr e Dl non devono simulare la scomparsa fisica dei candidati. |
| `024` | `120.0 s` | 24 - placeholder cloud | Task con CLOUD come unica alternativa remota. | Il report mobility rende visibile CLOUD_STABLE_PLACEHOLDER e phi_link CLOUD=0. |
| `025` | `125.0 s` | 25 - CPU locale condivisa | Due task locali sullo stesso veicolo. | Scenario esplorativo: osservare l'ipotesi corrente; la contesa locale aggregata resta open issue. |
| `026` | `130.0 s` | 26 - stabilità finale | Ripetizione esatta della finestra precedente. | D=0; warm start favorito salvo correzioni prestazionali. |
