# Audit riepilogativo G00 per il capitolo dei risultati

## Identificazione

- campagna: MA-GA V2 con contesa CPU locale;
- gruppo: G00 - preparazione e materializzazione degli scenari;
- branch: 
testing/final-campaign-v2-local-contention
;
- freeze di riferimento: 
bf41e5682293a79939af2c53858126ad4b9f2ef0
;
- commit tooling: 
a9aace8177902f0e4eec63e80149b7f5e2cb534b
;
- subject previsto per la chiusura: 
test(campaign-v2): recover G00 isolation and close materialization
;

## Obiettivo

Preparare in percorsi isolati gli scenari necessari alla campagna V2,
mantenendo invariati configurazioni, seed e parametri scientifici della
campagna precedente e senza eseguire run MOSAIC.

## Piano sperimentale materializzato

- istanze complessive: 69;
- materialization_id univoci: 69;
- directory di destinazione univoche: 69;
- seed: 
104729, 130363, 155921, 181081, 207547
;

- G01: 1 istanze;
- G02: 45 istanze;
- G03: 9 istanze;
- G04: 5 istanze;
- G05: 6 istanze;
- G06: 3 istanze;

## Esito della preparazione

- MATERIALIZED_VALIDATED: 63 istanze;
- MATERIALIZED_WITH_WARNINGS: 6 istanze;
- validate senza warning: 63; - validate con warning ammessi: 6; - fallite: 0; - bloccate: 0; - manifest scenario trovati: 69; - report di validazione trovati: 69;  ## Istanze con warning ammessi 
- MAT-CFG-M-BACKGROUND-104729 | gruppo=G04 | configurazione=CFG-M-BACKGROUND | seed=104729;
- MAT-CFG-M-RSU0-104729 | gruppo=G04 | configurazione=CFG-M-RSU0 | seed=104729;
- MAT-CFG-M-RSU1-104729 | gruppo=G04 | configurazione=CFG-M-RSU1 | seed=104729;
- MAT-CFG-M-SWITCH-104729 | gruppo=G04 | configurazione=CFG-M-SWITCH | seed=104729;
- MAT-CFG-M-V2V-104729 | gruppo=G04 | configurazione=CFG-M-V2V | seed=104729;
- MAT-CFG-R-V2VBW-104729 | gruppo=G05 | configurazione=CFG-R-V2VBW | seed=104729;

## Anomalia e recupero

Il primo macro-batch ha completato le 69 materializzazioni, ma alcune
funzioni di repair della copia V2 scrivevano ancora report nei percorsi
legacy. Il gate Git ha bloccato correttamente la chiusura.

La recovery ha ripristinato integralmente i percorsi legacy, corretto
i riferimenti hardcoded della copia V2 e riutilizzato le 69
materializzazioni senza rigenerarle.

## Vincoli rispettati

- nessuna modifica al core Java;
- nessuna modifica alle configurazioni scientifiche;
- nessuna importazione di risultati legacy;
- nessuna run MOSAIC;
- nessuna simulazione sperimentale;
- branch legacy remoto invariato;

## Interpretazione

G00 dimostra che il piano sperimentale V2 e' stato predisposto in modo
riproducibile e separato dalla campagna precedente. Questo gruppo non
produce metriche comparative dell'algoritmo: certifica la validita'
degli input e abilita l'avvio delle run da G01.

## Limiti

I sei warning devono essere riportati come avvisi di validazione ammessi
e non come fallimenti. Le metriche prestazionali saranno prodotte dai
gruppi successivi e non devono essere anticipate usando dati di G00.

## Evidenze principali

- data/docs/testing/final-campaign-v2-local-contention/scenario_instance_plan.csv;
- test-audits/final-campaign-v2-local-contention/G00_scenario_preparation_generation/materialization_summary_all.json;
- data/docs/testing/final-campaign-v2-local-contention/G00_CLOSURE.md;
- test-audits/final-campaign-v2-local-contention/G00_scenario_preparation_generation/G00_macro_isolation_recovery_20260626.md;
- tmp/materialized-literature-scenarios/final-campaign-v2-local-contention/;

## Decisione

PASS_G00_COMPLETE_READY_FOR_G01_RUNS
