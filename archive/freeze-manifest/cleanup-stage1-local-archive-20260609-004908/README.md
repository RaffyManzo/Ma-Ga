# Cleanup freeze - Stage 1: archivio locale controllato

Data: 2026-06-09 00:49:56
Branch: MOSAIC/SUMO-integration
HEAD: dba3da7057c4811aff0ff02b94079a86ecbb1689

## Obiettivo

Questa sottofase conserva le evidenze storiche locali senza eliminare artefatti
rigenerabili e senza modificare il core MA-GA.

## Struttura adottata

- Manifest leggeri versionabili: `archive\freeze-manifest\cleanup-stage1-local-archive-20260609-004908`
- Archivio locale pesante ignorato da Git: `tmp\archive\freeze-local-evidence-20260609`

## Risultati

- Risorse storiche spostate e verificate: 18
- Dimensione complessiva spostata: 66.706 MiB
- Evidenze leggere copiate dai run intermedi: 161
- Dimensione complessiva copiata: 1.6 MiB
- Candidati alla rimozione futura: 96
- Dimensione potenzialmente liberabile dopo le verifiche successive: 1906.87 MiB

## Vincoli rispettati

- Nessun output compilato è stato eliminato.
- Nessun run MOSAIC intermedio è stato eliminato.
- I cinque run finali literature-based sono rimasti integralmente nella directory MOSAIC.
- La baseline offline è stata spostata integralmente nell'archivio locale.
- Ogni spostamento è stato verificato tramite hash SHA-256 aggregato.
- Ogni copia di evidenza leggera è stata verificata tramite hash SHA-256.
