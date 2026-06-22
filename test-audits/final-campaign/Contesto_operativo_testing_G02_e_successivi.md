# Contesto operativo compatto — campagna finale MA-GA da G02 in avanti

## 1. Identità del progetto

- Repository: `Ma-Ga`
- Branch operativo: `testing/final-campaign`
- Core scientifico congelato: `5a9477735a3d707a5f000a64653cd2a6fc7f2007`
- HEAD finale dopo G01: `0be2507becf47e9e5c104b61b22bec407fdc0877`
- Subject: `test(campaign): complete G01 with recovered post-processing`
- Stato Git: locale e remoto allineati
- Working tree versionabile: pulito
- Diff Java/core: vuoto

## 2. Pipeline di riferimento

```text
InTAS / candidate_0045
→ materializer
→ scenario SUMO
→ deploy MOSAIC
→ Eclipse MOSAIC + SUMO
→ live-state layer
→ workload live
→ SystemSnapshot
→ bridge
→ TemporalWindowManager
→ MA-GA
→ strategia di offloading
→ reporting
→ validator
```

Il core MA-GA deve restare indipendente da MOSAIC. L’integrazione avviene tramite `SystemSnapshot` e bridge.

## 3. Decisioni già congelate

- Nessuna modifica Java durante la campagna senza discussione preventiva.
- `low_density` e `nominal` sono profili operativi.
- `high_density` è uno stress profile documentato.
- Baseline canonica: `gaParameterScalingMode=STATIC`.
- `ADAPTIVE` è una variante non canonica.
- Tecnologia: ITS-G5 / IEEE 802.11p.
- Scenario misto, prevalentemente urbano.
- Cinque seed ufficiali:
  - `104729`
  - `130363`
  - `155921`
  - `181081`
  - `207547`
- Cinque repliche/seed per configurazione principale.
- `taskCompletionModel=NOT_IMPLEMENTED`.
- Non inventare valori SUMO non esplicitamente disponibili.

## 4. Stato G00

G00 è completato.

Risultati:

- audit Git e freeze verificati;
- 69 istanze di scenario preparate;
- configurazioni classificate;
- incompatibilità della banda in notazione scientifica corretta;
- valore calibrato della banda invariato;
- commit G00D: `3e02146c9161e70caeae76c673f4068de5ecf1b7`;
- nessuna modifica Java/core.

## 5. Stato G01

G01 è completato.

Configurazione smoke:

- Config_ID: `CFG-SMOKE`
- Run_ID: `PRE-03-SMOKE`
- Materialization_ID canonico: `MAT-CFG-SMOKE-104729`
- Seed: `104729`
- Densità: `nominal`
- Durata: `180 s`
- Scaling GA: `STATIC`
- Run MOSAIC: `log-20260622-114955-MaGaLiteratureBasedUrbanStudy`

Artefatto runtime:

- modalità: `RECOVERED_VALIDATED_ARTIFACT`
- dimensione: `491454` byte
- SHA-256: `1a0aa6d2b0441f0be38baf5df1acc2d4d889b9f356b6111f7715105cd013a9b4`

Esito:

- deploy PASS;
- MOSAIC PASS;
- SUMO PASS;
- simulazione 180/180 s;
- summarizer originale fallito dopo la simulazione per percorso assoluto;
- summarizer e validator recuperati offline una sola volta;
- nessuna seconda simulazione;
- validator: `LITERATURE_SMOKE_TEST_PASSED`;
- errori validator: `0`.

Metriche principali:

- tasks generated/activated/removed: `101/101/101`;
- pending end/peak: `0/5`;
- GA submitted/completed/applied: `243/243/233`;
- stale: `10`;
- stale ratio: `4.115226%`;
- runtime GA mean/median/P95/max:
  `0.036802903 / 0.004650299 / 0.1787343 / 0.5146541 s`;
- snapshot lag massimo: `0`;
- runtime violations: `0`.

Manifest finale:

- evidenze: `85`;
- errori hash: `0`.

Bundle:

- `G01_final_verification_PASS_20260622-163931.zip`
- SHA-256: `80356d5f975afc476a6b5b95f1b6b3d6e751578c3bee5ca4adf54f7e52d71463`

## 6. Anomalie residue

Accettate:

- `AN-G01-0002`: alias diverso del Materialization_ID;
- `AN-G01-0003`: fresh build Java non riproducibile su Windows.

Risolte:

- `AN-G01-0001`;
- `AN-G01-0004`;
- `AN-G01-0005`;
- `AN-G01-0006`.

## 7. Gruppi successivi

| Gruppo | Descrizione | Dimensione |
|---|---|---:|
| G02 | Fattoriale principale | 9 configurazioni, 45 run |
| G03 | Riproducibilità e durata | 4 configurazioni, 11 run |
| G04 | Mobilità e connettività | 5 configurazioni, 5 run |
| G05 | Risorse e repair | 6 configurazioni, 6 run |
| G06 | Runtime e policy temporali | 3 configurazioni, 3 run |
| G07 | Audit trasversale finale | nessuna nuova configurazione |

## 8. G02 — fattoriale principale

Configurazioni:

```text
CFG-L-E  CFG-L-I  CFG-L-S
CFG-N-E  CFG-N-I  CFG-N-S
CFG-H-E  CFG-H-I  CFG-H-S
```

Ogni configurazione usa cinque seed, per un totale di 45 run.

Interpretazione:

- righe: densità `low`, `nominal`, `high`;
- colonne: workload `elementare`, `intermedio`, `stress`;
- `CFG-N-I` è la baseline scientifica centrale;
- `CFG-H-S` è stress combinato e non deve essere presentato come profilo stabile.

## 9. Sottofase obbligatoria G02B

Prima delle run comparative bisogna verificare se lo stato congelato permette, solo tramite configurazione o tooling esterno, di confrontare:

1. MA-GA completo;
2. LOCAL-only;
3. MA-GA senza penalità mobility-aware;
4. cold start rispetto al riuso della popolazione;
5. random-feasible o greedy solo se già supportata.

Sottoinsieme minimo:

- `CFG-N-I`;
- `CFG-N-S`;
- `CFG-H-I`;
- `CFG-H-S` facoltativa;
- cinque seed.

Regola bloccante:

> Se serve modificare il core Java congelato, fermarsi e discutere la scelta prima di procedere.

## 10. Primo compito della nuova conversazione

La nuova conversazione riceverà:

1. il bundle finale G01;
2. il report finale G00–G01;
3. la matrice Excel generale;
4. questo contesto operativo.

Il primo compito è aggiornare la matrice senza eseguire simulazioni.

### Aggiornamenti minimi nel workbook

Workbook attuale:

- `00_Guida`
- `01_Profili`
- `02_Configurazioni`
- `03_Piano_run`
- `04_Catalogo_test`
- `05_Output_dizionario`
- `06_Oracoli`
- `07_Checklist`
- `08_Limiti`
- `09_Fonti`

Operazioni consigliate:

1. creare una copia di backup;
2. aggiungere il foglio `10_Risultati_G00_G01`;
3. aggiornare `03_Piano_run`:
   - `PRE-01-AUDIT`: completato;
   - `PRE-02-CONFIG`: completato;
   - `PRE-03-SMOKE`: completato con recupero offline;
   - directory run: `log-20260622-114955-MaGaLiteratureBasedUrbanStudy`;
   - validator: `LITERATURE_SMOKE_TEST_PASSED`;
   - errori: `0`;
4. aggiornare `04_Catalogo_test`:
   - `T-001`: PASS;
   - `T-002`: PASS con limitazione artefatto recuperato;
   - `T-011`, `T-015`, `T-017`, `T-020`, `T-021`: PASS;
   - `T-010`, `T-012`, `T-013`, `T-018` e test runtime trasversali: solo evidenza parziale, non chiusura definitiva;
   - `T-016`: non completato, perché errors/teleports/emergency braking non sono esplicitamente disponibili;
5. aggiornare `07_Checklist` solo con voci provate dalle evidenze;
6. non cambiare lo stato delle run G02–G07;
7. aggiungere alla matrice la sottofase G02B.

Stati consigliati:

```text
PASS
PASS_CON_LIMITAZIONE
EVIDENZA_PARZIALE
NON_DISPONIBILE
DA_ESEGUIRE
```

## 11. Modalità di lavoro richiesta

La conversazione deve sempre spiegare prima:

- cosa stiamo facendo;
- perché serve;
- quale risultato ci aspettiamo.

Il linguaggio deve essere semplice, progressivo e concreto.

### Quando Codex non è disponibile

Produrre:

- un solo file PowerShell scaricabile;
- un solo comando da incollare in console;
- lo script deve cercare automaticamente i file in `Download`;
- deve fare backup;
- deve installare o modificare i file nelle cartelle corrette;
- deve eseguire verifiche;
- l’utente non deve sostituire file manualmente.

Quando è più semplice, può essere fornito un unico blocco PowerShell da eseguire interamente in console.

### Quando l’utente dichiara che Codex è disponibile

Produrre un prompt Codex completo con:

- obiettivo;
- vincoli;
- file consentiti;
- file vietati;
- verifiche;
- output atteso;
- messaggio di commit breve.

Dopo l’output di Codex:

1. analizzarlo;
2. verificare le modifiche effettive;
3. spiegare in modo semplice cosa è successo;
4. fornire il passo successivo.

## 12. Regole dopo ogni output

Quando l’utente incolla un output:

1. non ripetere il piano completo;
2. indicare cosa ha funzionato;
3. spiegare il primo punto reale di errore;
4. distinguere errore del test da errore dello script;
5. non rilanciare simulazioni già valide;
6. fornire un solo prossimo passo;
7. non procedere a G02 se la matrice non è stata aggiornata e verificata.

## 13. File da conservare localmente

Necessari per G02 e gruppi successivi:

- `tmp/mosaic-25.2/`;
- `tmp/materialized-literature-scenarios/`;
- `tmp/final-campaign-runtime-artifacts/`;
- `tmp/external-tools/`;
- `tmp/manual-audits/`;
- `tmp/archive/`;
- cartella di handoff G00–G01.

Gli intermedi G00/G01 possono essere compressi e spostati in `tmp/archive`.
