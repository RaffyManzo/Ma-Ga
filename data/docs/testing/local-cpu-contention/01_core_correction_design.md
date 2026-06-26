# Correzione della contesa CPU locale nel core MA-GA

## Stato del problema

La campagna sperimentale storica ha mostrato una quota LOCAL pari al
99,929113%. L'audit del core ha confermato che:

- il carico locale viene registrato nel breakdown;
- ogni task usa indipendentemente l'intera CPU locale del veicolo;
- il carico locale non entra nella penalità aggregata delle risorse;
- il completion time locale non include il lavoro degli altri task dello
  stesso veicolo.

I parametri calibrati di CPU, workload, deadline, rete e fitness non vengono
modificati.

## Modello introdotto

Per ogni task \(i\), la porzione locale richiede:

\[
C_i^{loc} = (1-p_i)C_i
\]

Le porzioni locali dello stesso veicolo vengono ordinate con EDF:

1. deadline crescente;
2. `taskId` crescente in caso di parità.

Per il task in posizione \(k\):

\[
T_{v,k}^{loc,cont} =
\frac{\sum_{j=1}^{k} C_j^{loc}}{f_v^{loc}}
\]

Il rapporto di domanda locale è:

\[
\rho_{v,k} =
\frac{\sum_{j=1}^{k} C_j^{loc}}
     {f_v^{loc}D_k}
\]

Per il veicolo:

\[
\rho_v = \max_k \rho_{v,k}
\]

L'overflow è:

\[
O_v^{loc} = \max(0,\rho_v-1)
\]

L'overflow usa il peso CPU già presente nella `PenaltyConfig`. Non viene
introdotto un nuovo coefficiente.

## Completion time

Per un task locale:

\[
T_i = T_i^{loc,cont}
\]

Per un task parzialmente remoto:

\[
T_i = \max(T_i^{loc,cont}, T_i^{rem})
\]

Per un task interamente remoto:

\[
T_i = T_i^{rem}
\]

Il termine globale della fitness resta il makespan:

\[
T(C)=\max_i T_i
\]

## Repair

Il repair della contesa locale interviene soltanto quando:

- \(\rho_v>1\); oppure
- almeno una deadline locale viene violata.

Il repair esplora le quote remote già previste dalla policy, incluse le quote
parziali sulla griglia a passi di 0,05. Ogni sostituzione viene rivalutata sul
cromosoma completo.

La scelta è deterministica:

1. minore numero di veicoli ancora in violazione;
2. minore numero di deadline locali violate;
3. minore overflow residuo;
4. se la contesa è risolta, quota remota minima sufficiente;
5. altrimenti, minore rapporto di domanda residuo;
6. completion time remoto, `taskId` e `candidateId`.

Questa regola evita di trasformare automaticamente un task in full remote
quando una quota parziale è già sufficiente a eliminare la contesa.

Se non esiste una scelta remota ammissibile, il cromosoma resta invariato e la
violazione viene rappresentata dalla fitness.

La variante `LOCAL_ONLY` conserva l'invariante: il repair non crea candidati
remoti assenti dallo snapshot di ottimizzazione.

## Breakdown e reporting

Per task vengono distinti:

- tempo locale indipendente;
- completion time locale corretto;
- ritardo dovuto alla contesa;
- completion time complessivo.

Per veicolo vengono esposti:

- numero di porzioni locali;
- cicli locali;
- tempo indipendente massimo;
- completion time locale massimo;
- ritardo di contesa massimo;
- rapporto di domanda massimo;
- overflow;
- deadline locali violate.

## Limite dichiarato

Il modello opera a livello di snapshot. Non implementa ancora:

- una coda CPU persistente tra finestre;
- il completamento reale e la rimozione anticipata dei task;
- una schedulazione multicore;
- preemption e costi del sistema operativo.

La correzione elimina comunque l'ipotesi precedente secondo cui più task
possono usare simultaneamente l'intera CPU locale.

## Test offline obbligatori

L'harness verifica:

1. compatibilità del caso con un solo task;
2. contesa tra task dello stesso veicolo;
3. assenza di contesa tra veicoli diversi;
4. ordinamento EDF deterministico;
5. quota locale del partial offloading;
6. makespan e deadline nella fitness;
7. overflow nella penalità risorse;
8. repair verso un candidato remoto;
9. invariante `LOCAL_ONLY`;
10. ripetibilità dell'evaluator.

La validazione MOSAIC/SUMO viene eseguita soltanto nel batch successivo.
