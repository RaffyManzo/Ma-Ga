# Chiusura formale G04-R

## Stato finale

`PASS_WITH_RECOVERED_CONTROLLED_MOBILITY_EVIDENCE`

G04-R ha recuperato le evidenze mancanti sui due obiettivi primari della mobilità controllata:

- T-096: `PASS`;
- T-098: `PASS`.

Gli obiettivi secondari restano:

- T-091: `EVIDENZA_PARZIALE`;
- T-094: `NON_OSSERVATO`;
- T-093: non ripetuto perché già `PASS_RECOVERED` in G05.

## Run valida

- Run ID: `G04-R-01-RECOVERY-01`
- Run MOSAIC: `log-20260624-233717-MaGaLiteratureBasedUrbanStudy`
- Durata: 180 s
- Densità: `low_density`
- Seed: 104729
- Veicolo sorgente della route: `synthetic_005`
- Veicolo runtime: `veh_2`
- Route: `route_dual_rsu_switch_04`
- CPU locale: moltiplicatore 0,5
- Modalità GA: `STATIC`
- Modifiche Java: 0

## Evidenza causale

La transizione `rsu_0 → rsu_1` è stata osservata tra 81,7 e 81,8 secondi.

Il probe è presente in 1.548 snapshot; i task probe compaiono in 400 snapshot. Sono stati applicati 123 geni remoti di tipo CLOUD:

- 38 prima della transizione;
- 51 nella finestra vicina alla transizione;
- 13 dopo la transizione.

Questi dati soddisfano il criterio causale previsto per T-098.

## Stato tecnico

- simulazione completata: sì;
- job GA: 760 inviati, 760 completati, 754 applicati;
- risultati stale: 6, pari allo 0,789474%;
- snapshot lag massimo: 0 s;
- violazioni runtime: 0;
- ultima strategia applicata: 180 s.

Il validator canonico restituisce `LITERATURE_SMOKE_TEST_FAILED` esclusivamente per `tasksGeneratedCumulative <= 0`. La run usa 19 task statici attivati, non il generatore Poisson; per questo il contatore dei task generati resta a zero. Il validator non è stato modificato. Lo stato tecnico viene classificato come:

`PASS_WITH_EXPECTED_STATIC_WORKLOAD_VALIDATOR_REJECTION`

## Tentativo escluso

Il primo tentativo MOSAIC è escluso dalle evidenze funzionali perché i task erano associati all'identificativo SUMO `synthetic_005`, mentre il runtime MOSAIC utilizzava `veh_2`. Il problema apparteneva al test harness, non al core MA-GA. Il tentativo viene conservato come `INVALID_HARNESS_RUN`.

## Limiti

La prova è valida per la route e il probe controllati. Non dimostra che ogni possibile traiettoria produca lo stesso comportamento.

T-091 resta parziale perché G04-R applica soltanto geni CLOUD, non EDGE. T-094 resta non osservato perché nessun gene applicato presenta `coverageSufficient=false`. Non sono previste altre run dedicate.

## Sequenza successiva

1. prima G07 sullo stato G00–G06 più G04-R;
2. prima bozza del capitolo sperimentale;
3. handoff ridotto e pulito verso una nuova conversazione;
4. G02B su branch sperimentale dedicato;
5. integrazione dei risultati nel capitolo;
6. G07 completa e definitiva.
