# Cleanup freeze - Stage 3: rimozione dei run MOSAIC intermedi

Data: 2026-06-09 00:55:59
Branch: MOSAIC/SUMO-integration
HEAD: dba3da7057c4811aff0ff02b94079a86ecbb1689

## Obiettivo

Questa sottofase rimuove esclusivamente i run MOSAIC intermedi già
classificati come rigenerabili.

## Verifiche eseguite prima della rimozione

- Manifest Stage 1 presente.
- Manifest Stage 2 presente.
- Archivio locale pesante presente.
- 161 copie leggere dei report intermedi verificate tramite SHA-256.
- Cinque run finali literature-based presenti.
- Numero totale di directory MOSAIC prima della rimozione: 74.
- Numero di run intermedi candidati alla rimozione: 69.

## Piano di rimozione

- Run intermedi rimossi: 69
- File contenuti nei run intermedi: 9083
- Dimensione complessiva rimossa: 1885.446 MiB

## Risorse mantenute

- I cinque run finali literature-based.
- Le copie leggere archiviate dei report intermedi.
- Gli output compilati dei componenti attivi.
- Lo scenario materializzato.
- Lo scenario deployato.
- Scenario-Convert estratto.
