# Cleanup freeze - Stage 2: rimozioni locali sicure

Data: 2026-06-09 00:53:02
Branch: MOSAIC/SUMO-integration
HEAD: dba3da7057c4811aff0ff02b94079a86ecbb1689

## Obiettivo

Questa sottofase elimina esclusivamente artefatti locali già classificati
come temporanei, vuoti, ripetitivi oppure obsoleti.

## Risultati

- Risorse locali eliminate: 23
- Dimensione liberata: 7.487 MiB
- ZIP temporanei eliminati: 2
- Log top-level ripetitivi eliminati: 19
- Directory di staging vuote eliminate: 1
- Output diagnostici obsoleti eliminati: 1

## Risorse non eliminate

- Output compilati dei componenti attivi
- Run MOSAIC intermedi
- Cinque run finali literature-based
- Scenario materializzato
- Scenario deployato
- Scenario-Convert estratto

## Verifiche

Prima delle rimozioni sono stati verificati:

- branch e HEAD;
- assenza di modifiche tracciate non consolidate;
- archivio locale Stage 1;
- hash delle 18 risorse storiche spostate;
- hash delle 161 evidenze leggere copiate;
- presenza dei cinque run finali;
- presenza delle risorse operative richieste.
