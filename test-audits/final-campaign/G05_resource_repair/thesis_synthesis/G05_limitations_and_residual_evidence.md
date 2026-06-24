# G05 - Limiti, comparabilita e stato delle evidenze residue

## Comparabilita con G02

- stesso core Java congelato e diff Java vuoto;
- stessa topologia candidate_0045;
- stessa densita, workload e seed per la baseline matched;
- durata differente: 300 s in G02, 180 s in G05;
- JAR della stessa dimensione ma SHA-256 differente;
- JAR G02: `1a0aa6d2b0441f0be38baf5df1acc2d4d889b9f356b6111f7715105cd013a9b4`;
- JAR G05: `3a5ae6111f251b97e3940fc57f5df5ab960adcb0af1bd985a9a0a7f680893575`.

Il diverso hash impedisce di dichiarare identita binaria. La comparabilita e quindi confermata a livello di sorgente congelata e configurazione, non byte per byte. Le differenze quantitative vengono presentate come confronto sperimentale controllato, non come equivalenza perfetta.

## Limiti del reporting

- taskCompletionModel e NOT_IMPLEMENTED;
- i task rimossi alla deadline non sono conteggi di completamento;
- le assegnazioni sono cumulative sulle finestre applicate e non task unici;
- repair e fallback non sono esposti con contatori causali strutturati;
- SUMO errors, teleports ed emergency braking restano null e non vengono inferiti;
- i risultati stale non sono considerati strategie applicate.

## Stato evidenze

| Evidenza | Stato dopo G05 | Decisione |
|---|---|---|
| T-091 EDGE applicato | Supporto aggiuntivo nelle recovery R-LOCALCPU, non nel test EDGECPU | Rivalutare dopo G06 |
| T-093 VEHICLE applicato | PASS_RECOVERED | Nessun rerun dedicato |
| T-094 coverageSufficient=false applicato | NOT_OBSERVED | Rivalutare dopo G06 |
| Repair/fallback strutturato | NOT_EXPOSED | Dichiarare limite di osservabilita |

## Regola per la chiusura

G05 puo essere chiusa con limitazioni documentate. Non servono altre simulazioni prima di G06. Le matrici saranno aggiornate nella sottofase G05-F4.
