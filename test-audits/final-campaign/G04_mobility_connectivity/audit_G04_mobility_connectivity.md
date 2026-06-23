# Audit formale G04 — mobilità e connettività

Data di ricostruzione: 23 giugno 2026
Gruppo: `G04`
Stato complessivo proposto: **PASS_WITH_OBSERVABILITY_LIMITS**
Core congelato: `5a9477735a3d707a5f000a64653cd2a6fc7f2007`
Branch operativo attestato dalle evidenze: `testing/final-campaign`
HEAD attestato prima della chiusura: `06eb415e720d08aba82f5aacc1501c86de2c0841`

## 1. Scopo e regola di interpretazione

G04 verifica la parte mobility-aware del MA-GA e la costruzione dei candidati LOCAL, VEHICLE, EDGE e CLOUD. La chiusura distingue sempre:

- **PASS tecnico della run**: simulazione completata, validator passato, errori e warning del validator pari a zero, snapshot lag zero e nessuna violazione runtime;
- **funzionalità osservata**: il comportamento richiesto compare nei dati runtime;
- **evidenza parziale**: il modello o la metrica compare, ma non soddisfa integralmente l’oracolo;
- **funzionalità non osservata**: la run è valida ma l’evento richiesto non si verifica;
- **errore reale**: violazione di un criterio hard o dato incoerente.

Una run tecnicamente PASS non implica il PASS automatico dei test funzionali associati.

## 2. Integrità delle evidenze

- Bundle tecnico: `G04_evidence_collection_PASS_20260623-222722.zip` — SHA-256 `b175f52879ba55ca55729e6c77e63231be2f10b315fa96f0386a3d599264f4d5`.
- Bundle offline autorevole: `G04_full_offline_audit_evidence_20260623-224200.zip` — SHA-256 `3c4de2c7851bc86878b67daebeffe07fdf608ec63799356131d950f455c16d75`.
- Inventario offline: 135/135 file verificati nel bundle di raccolta; CRC degli archivi valido.
- Result JSON autorevoli: 5/5 presenti e verificati tramite file `.sha256` e manifest.
- Raccolta offline: 0 simulazioni avviate, 0 file versionabili modificati, diff Java dal freeze vuoto.
- Problema noto corretto: il riepilogo iniziale aveva campi vuoti per tre run riprese; `G04_batch_summary_corrected.json` è ricostruito dai cinque result JSON autorevoli.

## 3. Risultati tecnici delle cinque run

| Run | Config | Stato tecnico | Task | GA sub/comp/appl | Stale | Runtime medio s | P95 s | Max s | Assegnazioni L/V/E/C |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| M-BACKGROUND-01 | CFG-M-BACKGROUND | PASS | 5901 | 416/416/411 | 5 (1.201923%) | 0.047822 | 0.134082 | 0.308151 | 13894/0/0/0 |
| M-RSU0-01 | CFG-M-RSU0 | PASS | 5299 | 370/370/369 | 1 (0.270270%) | 0.058550 | 0.138445 | 0.203413 | 11234/0/0/0 |
| M-RSU1-01 | CFG-M-RSU1 | PASS | 2887 | 427/427/427 | 0 (0.000000%) | 0.028549 | 0.065512 | 0.124636 | 7079/0/0/2 |
| M-SWITCH-01 | CFG-M-SWITCH | PASS | 6141 | 309/309/294 | 15 (4.854369%) | 0.090604 | 0.192657 | 0.372398 | 9418/0/0/0 |
| M-V2V-01 | CFG-M-V2V | PASS | 9396 | 193/193/100 | 93 (48.186528%) | 0.367724 | 1.188447 | 1.360664 | 2096/0/0/0 |

Totali G04:

- task generati: **29.624**;
- job GA submitted/completed/applied: **1.715 / 1.715 / 1.601**;
- stale: **114**, pari al **6,647230%** pesato sui job completati;
- assegnazioni applicate LOCAL/VEHICLE/EDGE/CLOUD: **43.721 / 0 / 0 / 2**;
- runtime massimo osservato: **1,3606641 s**;
- snapshot lag massimo: **0 s**;
- violazioni runtime: **0**.

`M-V2V-01` presenta uno stale ratio del **48,186528%**. Il dato è un limite operativo della configurazione V2V ad alta densità: non sono presenti lag dello snapshot, violazioni runtime o errori del validator, quindi non viene classificato automaticamente come regressione del core.

## 4. Verdetti T-090–T-099

| Test | Funzionalità | Configurazioni | Evidenza osservata | Criterio | Verdetto | Motivazione | Limite |
|---|---|---|---|---|---|---|---|
| T-090 | LOCAL_CONVENTIONAL | CFG-M-BACKGROUND | 14233 geni LOCAL; model=LOCAL_CONVENTIONAL; phiCov=phiLink=phiHo=Pmob=0; coverageTime=300 s; coverageSufficient=true. | Componenti locali coerenti e nessuna penalità mobility-aware sintetica. | **PASS** | Il comportamento convenzionale locale è stato osservato in strategie applicate e stale, con valori coerenti e finiti. | Nessuno specifico per il caso LOCAL. |
| T-091 | EDGE_GEOMETRIC | CFG-M-RSU0; CFG-M-RSU1; evidenza aggiuntiva CFG-M-V2V | 14 occorrenze di candidati EDGE negli snapshot iniziali di M-RSU0. Un gene EDGE_GEOMETRIC è stato calcolato in M-V2V-01 con phiLink=0.739655927, phiHo=0.032227990, Pmob=0.385941959, ma il job è STALE_DISCARDED. | Distanza/raggio finiti e phiLink coerente con d/R, con evidenza di crescita verso il bordo. | **EVIDENZA_PARZIALE** | Il modello geometrico EDGE è operativo e produce valori coerenti, ma non è stata applicata alcuna assegnazione EDGE e non è disponibile una sequenza sufficiente per dimostrare empiricamente la monotonia verso il bordo. | Un solo gene EDGE selezionato, peraltro stale; nessuna assegnazione EDGE applicata. |
| T-092 | CLOUD_GATEWAY_GEOMETRIC | CFG-M-RSU0; CFG-M-SWITCH; osservazione effettiva CFG-M-RSU1 | 2 assegnazioni CLOUD applicate, entrambe con model=CLOUD_GATEWAY_GEOMETRIC. phiLink=0.512421925/0.294512203; phiHo=0.044169684/0.002006684; Pmob=0.278295804/0.148259443. Placeholder legacy assente. | Cloud valutato attraverso il gateway radio attivo e nessun CLOUD_STABLE_PLACEHOLDER. | **PASS_CON_LIMITAZIONE** | Il percorso cloud gateway-aware è stato osservato in due strategie applicate e il placeholder legacy non compare. | Osservazione numericamente limitata e avvenuta in CFG-M-RSU1, non nel profilo switch; nessuna transizione gateway dimostrata. |
| T-093 | V2V_SCALAR_RELATIVE_SPEED | CFG-M-V2V | Nel profilo M-V2V-01 sono presenti 46 metriche di velocità relativa V2V: min=0.210279890 m/s, media=0.911777092 m/s, max=1.105719092 m/s; tutte finite. | Metriche finite e modello scalare dichiarato. | **PASS_CON_LIMITAZIONE** | La costruzione dei candidati V2V e della velocità relativa scalare è osservabile e numericamente valida. | Nessuna assegnazione VEHICLE è stata applicata; il modello usa |v_source-v_target| e non heading o vettori di velocità. |
| T-094 | Coverage insufficiente | CFG-M-SWITCH; future CFG-R-RTT/CFG-R-CELLBW | 0 casi coverageSufficient=false su 51683 geni; phiCov sempre 0 nelle evidenze G04. | Osservare completionTime > coverageTime oppure prefilter/penalità coerente. | **NON_OSSERVATO** | La run M-SWITCH non ha prodotto una decisione remota con copertura insufficiente. Il comportamento resta implementato nel core ma non è stato esercitato. | Il caso potrà emergere nei test G05 con RTT o banda CELL sotto pressione; non trasformare l’assenza in PASS. |
| T-095 | Link instability / phiLink | MOB | Nei tre geni remoti osservati phiLink è finito e in [0,1]: 0.294512203, 0.512421925 e 0.739655927. Negli snapshot M-RSU0 il rapporto distanza/raggio varia tra 0.407925601 e 0.479654932. | Valori in range e coerenti con distanza/raggio. | **PASS_CON_LIMITAZIONE** | I valori rispettano il dominio e la formula implementata clamp(d/R); le evidenze distinguono collegamenti più interni e più prossimi al bordo. | Campione ridotto di geni selezionati e assenza di una serie controllata continua sulla stessa sorgente. |
| T-096 | Handover risk / phiHo | CFG-M-SWITCH | phiHo non nullo in tre geni remoti fuori dal profilo switch: 0.002006684, 0.032227990 e 0.044169684. In M-SWITCH-01 non risultano transizioni e tutte le strategie applicate sono LOCAL. | Rischio maggiore vicino a una transizione di gateway osservata. | **EVIDENZA_PARZIALE** | La componente phiHo è calcolata e finita, ma non è stato possibile correlarla a uno switch reale. | G04_gateway_transitions.csv contiene solo l’intestazione; nessuna transizione rsu_0↔rsu_1. |
| T-097 | Pmob | MOB; MAIN | Tre geni remoti con Pmob non nullo. Verifica Pmob=1.0*phiCov+0.5*phiLink+0.5*phiHo; errore assoluto massimo 0.000e+00. | Somma pesata coerente con le componenti e valori finiti. | **PASS_CON_LIMITAZIONE** | La composizione numerica coincide con i coefficienti congelati e non presenta NaN o valori illegali. | Solo tre geni remoti osservati; uno appartiene a un risultato stale. |
| T-098 | Gateway switch | CFG-M-SWITCH | 0 transizioni gateway e 0 switch osservati. Il CSV delle transizioni contiene soltanto l’intestazione. | Almeno una transizione rsu_0→rsu_1 o rsu_1→rsu_0. | **NON_OSSERVATO** | Il criterio funzionale non è stato soddisfatto, pur essendo la run tecnicamente valida. | Non dichiarare handover o switch sulla base del solo nome della configurazione. |
| T-099 | Legacy cloud placeholder negativo | Tutte le run G04 | 0 occorrenze CLOUD_STABLE_PLACEHOLDER su 51683 geni; i 2 geni CLOUD osservati usano CLOUD_GATEWAY_GEOMETRIC. | placeholderCloud=0 in STRICT_GATEWAY. | **PASS** | Il modello legacy non compare nelle evidenze runtime e il cloud effettivamente selezionato è gateway-aware. | Il metodo legacy resta nel sorgente solo come API deprecata per compatibilità diagnostica. |

Riepilogo dei verdetti:

- `PASS`: 2;
- `PASS_CON_LIMITAZIONE`: 4;
- `EVIDENZA_PARZIALE`: 2;
- `NON_OSSERVATO`: 2;
- `FAIL`: 0.

## 5. Limiti obbligatori di interpretazione

1. `taskCompletionModel=NOT_IMPLEMENTED`: non calcolare completion rate e non interpretare la rimozione alla deadline come completamento.
2. `sumoErrors`, `teleports` ed `emergencyBraking` sono `null`: non dichiarare valori numerici.
3. Gli snapshot pubblicati nel bundle compatto coprono 32 istanti per run tra 2,0 e 5,1 s; per le decisioni lungo l’intera run sono stati usati i JSONL completi.
4. Nessuna transizione gateway è stata osservata: T-098 resta `NON_OSSERVATO`.
5. Nessuna assegnazione VEHICLE o EDGE è stata applicata; le evidenze V2V/EDGE riguardano candidati o risultati calcolati, con un gene EDGE in un job stale.
6. La copertura insufficiente non è stata esercitata in G04; potrà essere rivalutata in G05 con RTT o banda sotto pressione.

## 6. Verdetto complessivo

G04 è chiudibile come **PASS_WITH_OBSERVABILITY_LIMITS** perché:

- tutte le cinque esecuzioni obbligatorie sono tecnicamente valide;
- il modello LOCAL e l’assenza del placeholder cloud legacy sono verificati pienamente;
- cloud gateway-aware, V2V scalare, phiLink e Pmob sono verificati con limitazioni dichiarate;
- EDGE_GEOMETRIC e phiHo dispongono di evidenza parziale;
- coverage insufficiente e gateway switch non sono stati osservati, ma non risultano errori del core o della pipeline.

La chiusura non autorizza a presentare T-094 o T-098 come PASS. La fase successiva è G05; G02B resta `DEFERRED_AFTER_G06`.
