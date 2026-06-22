# Report finale della campagna di testing — gruppi G00 e G01

## 1. Scopo del documento

Questo documento chiude formalmente i primi due gruppi della campagna finale di testing del progetto MA-GA integrato con Eclipse MOSAIC e SUMO.

I gruppi coperti sono:

- **G00 — preparazione e generazione degli scenari**;
- **G01 — validazione della pipeline end-to-end**.

Il report separa con precisione ciò che è stato verificato, le limitazioni accettate e ciò che dovrà essere dimostrato nei gruppi successivi. Non vengono attribuiti risultati a test che non dispongono di evidenze sufficienti.

## 2. Stato iniziale congelato

Il core scientifico di riferimento è quello congelato al commit:

```text
5a9477735a3d707a5f000a64653cd2a6fc7f2007
```

Branch operativo della campagna:

```text
testing/final-campaign
```

Durante G00 e G01 non sono state introdotte modifiche Java al core MA-GA. La chiusura finale conferma un diff Java/core vuoto.

## 3. Struttura della campagna

Il piano complessivo è articolato nei seguenti gruppi:

| Gruppo | Contenuto | Stato |
|---|---|---|
| G00 | Preparazione, audit e generazione degli scenari | Completato |
| G01 | Validazione tecnica della pipeline smoke | Completato |
| G02 | Esperimenti fattoriali principali, 9 configurazioni × 5 seed | Non avviato |
| G03 | Riproducibilità e durata | Non avviato |
| G04 | Mobilità e connettività | Non avviato |
| G05 | Risorse, policy e repair | Non avviato |
| G06 | Runtime e policy temporali | Non avviato |
| G07 | Audit finale trasversale | Non avviato |

G02 non è stato avviato durante la chiusura di G01.

# Parte I — Gruppo G00

## 4. Obiettivi di G00

G00 doveva preparare una base sperimentale controllata prima delle run MOSAIC. Gli obiettivi principali erano:

1. verificare branch, commit congelato e stato Git;
2. controllare la compatibilità delle configurazioni con il tooling esistente;
3. costruire le istanze previste dalla matrice;
4. verificare che ogni istanza avesse metadati e file coerenti;
5. individuare incompatibilità bloccanti prima delle simulazioni.

## 5. Risultati di G00

G00 ha preparato e verificato **69 istanze di scenario** previste dal piano della campagna.

Sono state confermate le seguenti decisioni:

- `low_density` è un profilo operativo stabile;
- `nominal` è un profilo operativo stabile;
- `high_density` è uno **stress profile documentato**, non un profilo da presentare come stabile;
- la baseline canonica usa `gaParameterScalingMode=STATIC`;
- le varianti non canoniche devono restare separate dalla baseline.

La generazione degli scenari ha mantenuto la distinzione tra:

- `Config_ID`: definizione logica dell’esperimento;
- `Materialization_ID`: istanza concreta ottenuta da configurazione, seed e parametri;
- `Run_ID`: singola esecuzione MOSAIC di una materializzazione.

## 6. Problema individuato in G00

Il parser MOSAIC non accettava alcuni valori di banda serializzati in notazione scientifica.

La correzione G00D ha trasformato la rappresentazione, per esempio:

```text
4.92E7
```

in:

```text
49200000
```

Il valore numerico calibrato non è stato modificato. È cambiata solo la rappresentazione testuale.

Commit di chiusura tecnica G00:

```text
3e02146c9161e70caeae76c673f4068de5ecf1b7
fix(campaign): serialize MOSAIC bandwidth without exponents
```

## 7. Esito di G00

G00 è considerato completato perché:

- il repository e il freeze sono stati verificati;
- le configurazioni sono state censite e classificate;
- le 69 istanze sono state preparate;
- l’incompatibilità della banda è stata corretta;
- i file prodotti sono risultati utilizzabili dalla fase smoke;
- il core Java è rimasto invariato.

G00 non dimostra ancora il risultato statistico delle 45 run principali. Tale verifica appartiene a G02.

# Parte II — Gruppo G01

## 8. Obiettivo di G01

G01 doveva validare una singola configurazione smoke lungo l’intera catena:

```text
materializzazione
→ deploy
→ avvio Eclipse MOSAIC
→ avvio SUMO
→ live-state layer
→ generazione task
→ snapshot
→ bridge
→ TemporalWindowManager
→ algoritmo genetico
→ applicazione strategie
→ reporting
→ validator
```

Configurazione usata:

| Campo | Valore |
|---|---|
| Config_ID | `CFG-SMOKE` |
| Run_ID | `PRE-03-SMOKE` |
| Materialization_ID canonico | `MAT-CFG-SMOKE-104729` |
| Seed | `104729` |
| Densità | `nominal` |
| Durata | `180 s` |
| GA scaling | `STATIC` |
| Mobility mode | `SYNTHETIC_CALIBRATED_ON_INTAS_SUBNETWORK` |

La matrice originaria usa anche l’alias `MAT-SMOKE-104729`. La differenza è stata registrata come discrepanza nominale accettata; il contenuto dello scenario non cambia.

## 9. Problema ambientale della fresh build

Nell’ambiente Windows corrente, `javac` ha prodotto tutte le classi previste ma la chiusura ZipFS dei JAR non è risultata riproducibile. Il comportamento è stato osservato con più JDK.

La limitazione è stata separata dalla validazione scientifica:

- non è stato dichiarato che una fresh build fosse riuscita;
- è stato recuperato un JAR già prodotto e deployato in RETRY-02;
- il JAR è stato validato prima del riuso.

Artefatto runtime validato:

| Proprietà | Valore |
|---|---|
| Dimensione | `491454` byte |
| SHA-256 | `1a0aa6d2b0441f0be38baf5df1acc2d4d889b9f356b6111f7715105cd013a9b4` |
| Modalità | `RECOVERED_VALIDATED_ARTIFACT` |

Checkpoint del tooling che supporta esplicitamente questa modalità:

```text
7b3f6359aae81388d009d35da3adb527fc8f11b8
fix(campaign): support validated recovered runtime artifact
```

## 10. Esito di RETRY-04

RETRY-04 è stato eseguito **una sola volta**.

La run MOSAIC validata è:

```text
log-20260622-114955-MaGaLiteratureBasedUrbanStudy
```

Esito tecnico:

- validazione JAR: PASS;
- deploy: PASS;
- avvio MOSAIC: PASS;
- avvio SUMO: PASS;
- simulazione: completata a `180/180 s`;
- runner originale: exit code `1` dopo la simulazione;
- punto di errore: summarizer post-MOSAIC;
- causa: `MosaicRoot` assoluto ricombinato con `RepoRoot`.

Il summarizer corretto e il validator sono stati eseguiti una sola volta offline sulla run già terminata. Non sono stati rilanciati build, deploy, MOSAIC, SUMO o RETRY-04.

Classificazione finale:

```text
G01_RETRY_04_OFFLINE_POST_PROCESSING_RECOVERED
```

Validator finale:

```text
LITERATURE_SMOKE_TEST_PASSED
```

Errori validator:

```text
0
```

## 11. Metriche finali G01

| Metrica | Valore |
|---|---:|
| Durata simulazione | 180 s |
| Task generati | 101 |
| Task attivati | 101 |
| Task rimossi alla deadline | 101 |
| Task pendenti alla fine | 0 |
| Picco task pendenti | 5 |
| GA job submitted | 243 |
| GA job completed | 243 |
| Strategie applicate | 233 |
| Risultati stale | 10 |
| Stale ratio | 4,115226% |
| Sequenza stale consecutiva massima | 2 |
| Runtime GA medio | 0,036802903 s |
| Runtime GA mediano | 0,004650299 s |
| Runtime GA P95 | 0,1787343 s |
| Runtime GA massimo | 0,5146541 s |
| Snapshot lag assoluto massimo | 0 s |
| Finestre con lag non nullo | 0 |
| Ultima strategia applicata | 180 s |
| Secondi finali senza strategia applicata | 0 |
| Violazioni runtime | 0 |

Il modello di completamento dei task resta:

```text
taskCompletionModel=NOT_IMPLEMENTED
```

Di conseguenza non deve essere calcolato o dichiarato un task completion rate.

I valori `SUMO errors`, `teleports` ed `emergency braking` non sono riportati esplicitamente nei risultati finali e non devono essere inventati.

## 12. Anomalie G01

| ID | Stato finale | Sintesi |
|---|---|---|
| AN-G01-0001 | RESOLVED | Pipeline smoke validata con recupero offline del post-processing |
| AN-G01-0002 | ACCEPTED_LIMITATION | Discrepanza nominale del Materialization_ID |
| AN-G01-0003 | ACCEPTED_LIMITATION | Fresh build Windows non riproducibile |
| AN-G01-0004 | RESOLVED | Gestione dello stderr annidato |
| AN-G01-0005 | RESOLVED | Serializzazione della banda compatibile con MOSAIC |
| AN-G01-0006 | RESOLVED | Gestione corretta di `MosaicRoot` assoluto nel summarizer |

## 13. Test primari coperti

Secondo il piano dei gruppi, G01 possiede come test primari:

- `T-011` — conteggi della rete `candidate_0045`;
- `T-015` — creazione e leggibilità del database SQLite;
- `T-017` — completezza dei file dello scenario;
- `T-020` — deploy controllato dello scenario MOSAIC;
- `T-021` — avvio dei federati MOSAIC/SUMO.

Questi test possono essere aggiornati nella matrice come completati sulla configurazione smoke.

Altri test dispongono di evidenze utili prodotte da G01, ma appartengono come responsabilità primaria a gruppi successivi. Non devono essere marcati automaticamente come definitivamente conclusi. Devono essere indicati come `EVIDENZA PARZIALE`, `OSSERVATO IN SMOKE` o formula equivalente.

Esempi:

- lifecycle task;
- snapshot e causalità;
- job GA submitted/completed/applied;
- stale result;
- reporting;
- scaling `STATIC`;
- state source `MOSAIC_LIVE`.

## 14. Stato Git finale

Commit conclusivo G01:

```text
0be2507becf47e9e5c104b61b22bec407fdc0877
test(campaign): complete G01 with recovered post-processing
```

Controlli finali:

- HEAD locale e remoto allineati;
- working tree versionabile pulito;
- diff Java/core vuoto;
- manifest: 85 evidenze;
- errori hash: 0;
- G02 non avviato.

## 15. Bundle conclusivo

Bundle:

```text
G01_final_verification_PASS_20260622-163931.zip
```

SHA-256:

```text
80356d5f975afc476a6b5b95f1b6b3d6e751578c3bee5ca4adf54f7e52d71463
```

Dimensione:

```text
54382 byte
```

Il file ZIP contiene 31 file complessivi. L’inventario interno elenca 30 file perché non include se stesso.

## 16. Cosa è stato dimostrato

G00 e G01 dimostrano che:

1. la campagna può essere preparata in modo deterministico e tracciabile;
2. le configurazioni possono essere materializzate con una struttura coerente;
3. la banda viene serializzata in un formato accettato da MOSAIC;
4. un JAR recuperato e validato può essere usato in modo esplicito e non ambiguo;
5. MOSAIC e SUMO completano la run smoke;
6. il runtime genera task, snapshot e job GA;
7. le strategie vengono applicate fino al termine della simulazione;
8. il post-processing può essere recuperato senza ripetere la simulazione;
9. il validator finale passa con zero errori.

## 17. Cosa non è stato ancora dimostrato

Restano da verificare:

- risultati statistici delle 45 run principali di G02;
- confronto tra densità e workload;
- intervalli tra cinque seed;
- qualità decisionale rispetto alle baseline;
- contributo quantitativo della componente mobility-aware;
- beneficio del riuso della popolazione;
- riproducibilità e durata G03;
- casi mirati di mobilità G04;
- stress di CPU, banda e repair G05;
- policy temporali e stale controllato G06;
- audit finale G07.

## 18. Prossimo passo

La nuova conversazione deve iniziare aggiornando la matrice Excel generale con i risultati di G00 e G01, senza ancora eseguire G02.

Dopo l’aggiornamento della matrice deve essere pianificata la sottofase comparativa **G02B**, che dovrà verificare se lo stato congelato supporta, senza modificare il core Java:

- MA-GA completo;
- LOCAL-only;
- MA-GA senza penalità mobility-aware;
- cold start rispetto al population reuse;
- baseline random-feasible o greedy solo se già disponibile.

Configurazioni minime consigliate per G02B:

- `CFG-N-I`;
- `CFG-N-S`;
- `CFG-H-I`;
- `CFG-H-S` facoltativa.

Ogni confronto deve usare cinque seed. Se per realizzare una modalità fosse necessaria una modifica Java al core congelato, la campagna deve fermarsi e la scelta deve essere discussa prima di procedere.
