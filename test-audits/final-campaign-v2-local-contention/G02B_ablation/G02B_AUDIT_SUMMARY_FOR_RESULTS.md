# Audit scientifico finale G02B

## Integrità e disegno

- bundle: `MA_GA_V2_G02B_RUNS_20260627-030948.zip`;
- SHA-256: `385f082bae059d8d6fd96c149b3d2aab1608f545d7cdd6c2dc2d67338e63adc9`;
- entry ZIP: 1243;
- file coperti dal manifest: 1242;
- errori CRC/hash/dimensione: 0;
- file aggiuntivi: 0;
- nuove run: 45;
- baseline FULL_MA_GA riusate: 15;
- confronti appaiati: 45;
- durata: 300 s per run;
- configurazioni: CFG-N-I, CFG-N-S, CFG-H-I;
- seed: 104729, 130363, 155921, 181081, 207547.

## Validità tecnica

- validator PASS: 45/45;
- tick runtime: 3.000 in 45/45;
- directory run distinte: 45/45;
- task generati uguali alla baseline: 45/45;
- accounting asincrono coerente: 45/45;
- snapshot lag massimo: 0 s;
- violazioni runtime: 0;
- JAR congelato invariato;
- modifiche Java/core: nessuna.

## LOCAL_ONLY

La variante viene attivata correttamente:

- assegnazioni remote: 0;
- runtime GA medio: 0,04358 s contro 0,24480 s della baseline;
- stale medio: 0% contro 6,93%;
- strategie applicate medie: 583,13 contro 27,60;
- contesa locale: circa 95,13% delle finestre applicate;
- overflow locale: circa 94,24%;
- violazioni locali per applicazione: 16,42 contro 0,019;
- ritardo locale massimo medio: 17,49 s contro 1,33 s.

In tutte le 15 coppie LOCAL_ONLY riduce il runtime e aumenta sia le
strategie applicate sia le violazioni locali. La ricerca più semplice
mantiene la cadenza, ma trasferisce tutto il carico sulla CPU del veicolo.

I conteggi assoluti non sono direttamente confrontabili senza cautela:
LOCAL_ONLY applica molte più finestre. Anche dopo la normalizzazione,
comunque, la contesa e l'overflow restano dominanti.

## NO_MOBILITY_PENALTY

- peso effettivo: wM=0 in 15/15;
- pesi residui normalizzati: wL=0,3333, wT=0,4667, wR=0,2;
- runtime medio: 0,20915 s;
- stale medio: 6,81%;
- quota remota media: 10,24% contro 8,34%.

Le differenze appaiate non sono coerenti tra seed. Non emerge un vantaggio
o uno svantaggio aggregato robusto. La corretta conclusione è che
l'ablazione è stata applicata, ma il contributo prestazionale della
penalità di mobilità non è isolato in modo conclusivo in queste condizioni.

## COLD_START_NO_REUSE

- WARM_START osservati: 0;
- PARTIAL_RESTART osservati: 0;
- runtime medio: 0,29769 s contro 0,24480 s;
- stale medio: 7,46% contro 6,93%.

Le medie suggeriscono un costo moderato quando il riuso viene disattivato,
ma l'effetto non è uniforme nelle 15 coppie. Il beneficio del riuso resta
quindi plausibile e parzialmente sostenuto, non dimostrato in modo forte.

## Classificazione

- PASS: 7;
- PASS_CON_LIMITAZIONE: 1;
- EVIDENZA_PARZIALE: 2;
- NON_OSSERVATO: 0;
- FAIL: 0.

## Limiti da mantenere nel capitolo 7

- il limite di cadenza riduce la copertura decisionale di FULL_MA_GA,
  NO_MOBILITY_PENALTY e COLD_START_NO_REUSE;
- i conteggi assoluti LOCAL_ONLY sono amplificati da molte più applicazioni;
- `taskCompletionModel = NOT_IMPLEMENTED`;
- le assegnazioni non equivalgono a completamenti end-to-end;
- l'ablazione della penalità di mobilità normalizza anche i pesi residui;
- gli effetti di mobilità e riuso non risultano robusti tra tutti i seed.

## Decisione

**PASS_G02B_COMPLETE_WITH_STRONG_LOCAL_ONLY_EFFECT_AND_LIMITED_MARGINAL_EFFECTS_READY_FOR_G07**
