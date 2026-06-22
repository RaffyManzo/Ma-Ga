# Report di tracciamento della campagna finale G00-G01

## 1. Scopo della campagna finale

La campagna finale verifica in modo controllato la preparazione degli scenari e la pipeline live MA-GA integrata con Eclipse MOSAIC e SUMO.

## 2. Stato congelato di partenza

Il core scientifico MA-GA deriva dal commit congelato 5a9477735a3d707a5f000a64653cd2a6fc7f2007. Durante G00 e G01 non sono state introdotte modifiche Java al core congelato.

## 3. Fase G00

G00 ha verificato la generazione e la compatibilitÃ  delle materializzazioni della campagna. Sono state preparate 69 istanze. I profili low_density e nominal restano profili operativi. high_density resta uno stress profile documentato.

## 4. Problemi trovati in G00

La preparazione ha individuato incongruenze nei metadati canonici e una serializzazione della banda in notazione scientifica non accettata dal parser MOSAIC.

## 5. Correzione della serializzazione della banda

G00D ha convertito i valori della banda in una notazione fissa compatibile, per esempio 49200000 bps, senza cambiare i valori calibrati.

## 6. Avvio di G01

G01 aveva l'obiettivo di validare l'intera catena: build o selezione del runtime, deploy, MOSAIC, SUMO, live-state layer, snapshot, TemporalWindowManager, GA, applicazione della strategia, reporting e validator.

## 7. Problemi di build e workspace

Le prime sottofasi hanno individuato problemi nel workspace di build e nella propagazione dello stderr dei processi PowerShell annidati. Questi problemi sono stati separati dai risultati scientifici.

## 8. Problema della propagazione MosaicRoot

RETRY-03 si Ã¨ fermato prima della build perchÃ© un percorso MosaicRoot giÃ  assoluto veniva ricombinato con RepoRoot. G01G ha corretto la normalizzazione e la propagazione del percorso nel runner.

## 9. Problema ambientale javac e ZipFS

Le build javac hanno prodotto tutte le 252 classi previste, ma sono terminate in modo non riproducibile con AccessDeniedException durante la chiusura ZipFS dei JAR. Il problema si Ã¨ verificato con piÃ¹ JDK e resta una limitazione ambientale accettata.

## 10. Recupero del JAR RETRY-02

G01K ha recuperato due copie identiche del JAR giÃ  prodotto e deployato in RETRY-02. Il file selezionato ha dimensione 491454 byte e SHA-256 1a0aa6d2b0441f0be38baf5df1acc2d4d889b9f356b6111f7715105cd013a9b4. La struttura del JAR e le classi attese sono state verificate.

## 11. ModalitÃ  RECOVERED_VALIDATED_ARTIFACT

G01L ha introdotto una modalitÃ  esplicita che valida il JAR prima del deploy. Questa modalitÃ  non esegue una nuova build e non presenta il JAR come artefatto appena compilato.

## 12. Esito RETRY-04

RETRY-04 Ã¨ stato eseguito una sola volta. Il deploy Ã¨ riuscito. MOSAIC e SUMO sono partiti. La simulazione ha raggiunto 180 secondi su 180.

Il runner Ã¨ terminato con exit code 1 dopo la simulazione, durante il summarizer, a causa della gestione errata di un MosaicRoot assoluto.

Il summarizer corretto e il validator sono stati eseguiti una sola volta offline sul run log-20260622-114955-MaGaLiteratureBasedUrbanStudy, senza riavviare runner, deploy, MOSAIC, SUMO o simulazione.

Esito finale del validator: LITERATURE_SMOKE_TEST_PASSED.
Errori validator: 0.
Violazioni runtime: 0.

## 13. Stato finale delle anomalie

AN-G01-0001 Ã¨ risolta perchÃ© l'intera pipeline Ã¨ stata validata.
AN-G01-0002 resta una limitazione accettata relativa alla denominazione della materializzazione.
AN-G01-0003 resta una limitazione accettata relativa alla fresh build Windows.
AN-G01-0004, AN-G01-0005 e AN-G01-0006 risultano risolte.

## 14. File e commit prodotti

Il tooling per l'artefatto recuperato Ã¨ stato pubblicato nel commit 7b3f6359aae81388d009d35da3adb527fc8f11b8. Questo script prepara il commit finale contenente summarizer corretto, risultati RETRY-04, metriche, audit, anomalie, manifest e report.

## 15. Limitazioni ancora accettate

La fresh build javac non Ã¨ riproducibile stabilmente nell'ambiente Windows corrente.
Il JAR usato dalla campagna deriva da una precedente build riuscita e giÃ  deployata.
Il taskCompletionModel resta NOT_IMPLEMENTED.
I valori SUMO errors, teleports ed emergency braking non vengono inventati se non esplicitamente disponibili.

## 16. Cosa Ã¨ stato dimostrato

Ãˆ stato dimostrato che il JAR validato puÃ² essere deployato, MOSAIC e SUMO possono completare la simulazione, il runtime produce snapshot e task, il GA viene invocato e le strategie vengono applicate. Il validator finale ha restituito LITERATURE_SMOKE_TEST_PASSED con zero errori.

## 17. Cosa non Ã¨ stato ancora dimostrato

G01 non dimostra ancora la qualitÃ  comparativa dell'algoritmo rispetto alle baseline, il contributo quantitativo della penalitÃ  mobility-aware, il beneficio del population reuse o il comportamento statistico sulle configurazioni e sui seed della campagna completa.

## 18. Stato prima di G02

G01 Ã¨ conclusa con recupero offline del post-processing. G02 non Ã¨ stata avviata. Prima di iniziare G02 bisogna fermarsi e revisionare questo report e lo stato finale della campagna.



