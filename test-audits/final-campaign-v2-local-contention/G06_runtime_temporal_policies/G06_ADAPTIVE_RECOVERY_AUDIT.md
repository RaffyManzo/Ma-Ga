# Recupero selettivo G06 adaptive

- causa: il validatore di deploy canonico richiede STATIC;
- effetto del primo macro: G-ADAPTIVE-01 associata per errore alla run sparse;
- simulazioni ripetute: soltanto G-ADAPTIVE-01;
- run sparse conservata: `log-20260627-015101-MaGaLiteratureBasedUrbanStudy`;
- nuova run adaptive: `log-20260627-021248-MaGaLiteratureBasedUrbanStudy`;
- modalità osservata: `ADAPTIVE`;
- tick runtime: `3000`;
- durata nominale: `300 s`;
- run duplicate dopo il recupero: no;
- modifiche Java/core: nessuna.

Il deploy usa una copia temporanea con metadati STATIC soltanto per la
validazione. Lo scenario effettivamente eseguito viene ripristinato ad
ADAPTIVE prima dell'avvio di MOSAIC.
