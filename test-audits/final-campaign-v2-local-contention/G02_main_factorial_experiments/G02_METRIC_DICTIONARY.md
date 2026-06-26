# Dizionario delle metriche G02

| Campo | Significato | Uso nel capitolo 7 |
|---|---|---|
| tasksGenerated | Task generati durante la run | Carico prodotto dalla densita' veicolare |
| tasksPendingPeak | Massimo numero di task pendenti | Pressione del workload |
| gaJobsSubmitted | Ottimizzazioni inviate al worker | Attivita' del runtime GA |
| gaJobsApplied | Strategie applicate | Risultati validi usati dal runtime |
| gaJobsDiscardedAsStale | Risultati arrivati oltre il limite temporale | Reattivita' del runtime |
| shutdownInFlight | Job ancora pendenti alla chiusura | Stato asincrono terminale, non fallimento |
| localAssignments | Assegnazioni LOCAL osservate | Distribuzione delle decisioni |
| vehicleAssignments | Assegnazioni VEHICLE osservate | Offloading V2V |
| edgeAssignments | Assegnazioni EDGE osservate | Offloading infrastrutturale vicino |
| cloudAssignments | Assegnazioni CLOUD osservate | Offloading remoto |
| maxIndependentLocalExecutionTimeSeconds | Tempo locale isolato massimo tra le porzioni selezionate localmente | Non misura direttamente la complessita' del generatore |
| maxContendedLocalCompletionTimeSeconds | Completion time locale massimo con contesa | Effetto della CPU condivisa |
| maxLocalContentionDelaySeconds | Ritardo massimo introdotto dalla contesa | Evidenza diretta della correzione V2 |
| maxLocalDemandRatio | Rapporto massimo tra domanda locale e capacita' | Saturazione della CPU locale |
| maxLocalCpuOverflowRatio | Eccesso rispetto alla capacita' locale | Violazione/pressione locale |
| localDeadlineViolations | Deadline locali non rispettate | Fattibilita' temporale locale |
| staleRatioPercent | Percentuale di job obsoleti | Comportamento asincrono |
| gaRuntimeMeanSeconds / P95 / max | Tempo del GA | Costo computazionale |
