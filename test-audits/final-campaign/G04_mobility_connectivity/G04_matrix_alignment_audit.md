# Audit di allineamento delle matrici dopo G04

Data verifica: 23 giugno 2026
Stato: **PASS**

$178859928b85e272d067c5f3c346a0d8702a613c45ac4d15071de09142710d568`
- Fogli: **15**
- Nuovo foglio: `14_Risultati_G04`
- Configurazioni `CFG-M-*`: riallineate agli esiti delle cinque run.
- Piano run: `M-BACKGROUND-01`, `M-RSU0-01`, `M-RSU1-01`, `M-SWITCH-01`, `M-V2V-01` compilate.
- Catalogo: T-090–T-099 valutati con stati normalizzati.
- Checklist, oracoli, limiti, fonti, storico e riepiloghi: aggiornati.
- Prossimo gruppo: `G05`.
- G02B: `DEFERRED_AFTER_G06`.

$19a4956053372833f2d1a7e222e159686a7947d80f4fd20fe74d5dddf8488c11e`
- Fogli: **7**
- Nuovo foglio: `06_Risultati_G04`
- Dashboard, riepilogo configurazioni, storico e legenda: riallineati a G04.

## Controlli strutturali

- errori formula cercati: `#REF!`, `#VALUE!`, `#DIV/0!`, `#NAME?`, `#N/A`;
- errori trovati: **0** in entrambe le matrici;
- riferimenti operativi residui `DA_ESEGUIRE_G04`, `G04 non avviata`, `Avviare G04`, `Prossimo gruppo G04`: **0**;
- freeze panes conservati o impostati nei fogli operativi principali;
- formule aggregate del foglio `14_Risultati_G04` verificate;
- nessun completion rate inserito;
- valori SUMO `null` non trasformati in zero.

## Controllo visivo

Sono stati renderizzati e controllati:

1. guida della matrice completa;
2. piano run G04;
3. righe T-090–T-099;
4. foglio `14_Risultati_G04`;
5. dashboard semplificata;
6. foglio `06_Risultati_G04`.

Le immagini di controllo sono conservate in `_quality_control/previews/` nel pacchetto di chiusura e non vengono installate nella repository.
## Verifica post-chiusura Git

- commit di chiusura verificato: `4ab01845f772640398c25ba7f565ec857d284052`;
- branch remoto verificato: `testing/final-campaign`;
- working tree attestato pulito e diff Java dal freeze vuoto;
- rimossi dalle matrici i riferimenti operativi ormai superati a commit/push ancora da eseguire;
- prossimo passo operativo: preflight G05, senza avviare automaticamente simulazioni.
