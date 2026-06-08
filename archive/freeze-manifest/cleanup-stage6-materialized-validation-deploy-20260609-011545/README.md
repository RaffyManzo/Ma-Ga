# Cleanup freeze - Stage 6: validazione e deploy controllato

Data: 2026-06-09 01:16:10
Branch: MOSAIC/SUMO-integration
HEAD: dba3da7057c4811aff0ff02b94079a86ecbb1689

## Scenario materializzato validato

`tmp\materialized-literature-scenarios\MaGaLiteratureBasedUrbanStudy\nominal-smoke-seed-104729`

## Scenario deployato

`tmp\mosaic-25.2\scenarios\MaGaLiteratureBasedUrbanStudy`

## Strategia reversibile

Lo scenario precedentemente deployato è stato conservato localmente sotto:

`tmp\archive\freeze-local-evidence-20260609\pre-deploy-scenario-backup-20260609-011545\MaGaLiteratureBasedUrbanStudy`

## Verifiche eseguite

- Validator dello scenario materializzato completato.
- Database SQLite reale presente.
- Deploy nello scenario MOSAIC locale completato.
- JAR runtime iniettato con hash identico al JAR rigenerato.
- JAR diagnostico ad-hoc iniettato con hash identico al JAR rigenerato.
- Nessuna modifica tracciata aggiuntiva introdotta dal deploy.
- Smoke test non ancora eseguito.
