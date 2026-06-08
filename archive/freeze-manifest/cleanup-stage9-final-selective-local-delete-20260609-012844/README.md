# Cleanup freeze - Stage 9: pulizia locale selettiva finale

Data: 2026-06-09 01:28:50
Branch: MOSAIC/SUMO-integration
HEAD: dba3da7057c4811aff0ff02b94079a86ecbb1689

## Obiettivo

Questa sottofase rimuove le vecchie classi compilate sotto `out` e i backup
temporanei superati dalla validazione finale.

## Directory root out

La directory `out` non è stata eliminata completamente.

È stata mantenuta:

`out\codex-lib`

perché contiene le tre librerie Jackson ancora richieste dagli script di
validazione.

## Risultati

- Vecchie risorse derivate rimosse sotto `out`: 16
- Backup temporanei di rebuild e deploy rimossi: 6
- Risorse locali eliminate complessivamente: 22
- Dimensione liberata: 14.291 MiB
- Librerie Jackson mantenute e verificate: 3
- Output attivi ricostruiti mantenuti: 3
- Run MOSAIC finali mantenuti: 6

## Vincoli rispettati

- Nessuna modifica al core Java MA-GA.
- Nessuna rimozione dei tre output attivi ricostruiti.
- Nessuna rimozione dei sei run MOSAIC finali.
- Nessuna rimozione delle evidenze storiche locali archiviate.
- Nessuna modifica allo staging Git.
