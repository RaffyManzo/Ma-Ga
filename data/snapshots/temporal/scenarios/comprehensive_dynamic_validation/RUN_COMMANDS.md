# Comandi rapidi

## Esecuzione completa e deterministica dell'ordine dei file

```text
Main class:
app.AdaptiveWindowMain

Program arguments:
JSON_SEQUENCE CONFIGURED_RUNTIME data/snapshots/temporal/scenarios/comprehensive_dynamic_validation 27
```

## Esecuzione time-driven

```text
Main class:
app.AdaptiveWindowMain

Program arguments:
JSON_TIME OBSERVED_RUNTIME data/snapshots/temporal/scenarios/comprehensive_dynamic_validation 27
```

## Controlli minimi nel report

```text
- vengono letti i file 000 ... 026 in JSON_SEQUENCE;
- TcovRef resta invariato tra 001 e 002;
- edge_fragile_valid resta disponibile;
- edge_outside viene filtrato;
- il candidato V2V sparisce in 004 e rientra in 005;
- 006 non produce DEGRADED_BEST_EFFORT;
- 007 produce DEGRADED_BEST_EFFORT;
- 010 non produce CPU violations residue;
- Dt cresce in 014;
- Dr cresce in 015;
- Dl cresce in 017;
- gli EDGE fuori copertura sono filtrati in 018;
- cold start plausibile in 019;
- in 022 e 023 Dr/Dl non simulano la comparsa o scomparsa fisica dei nodi;
- JSON_TIME non espone mai snapshot futuri.
```
