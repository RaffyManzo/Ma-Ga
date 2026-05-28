package window.dynamicity;

import window.population.PopulationReuseMode;

import config.window.TemporalWindowConfig;
import model.node.NodeCandidate;
import model.snapshot.SystemSnapshot;
import model.snapshot.TaskInstance;
import model.snapshot.VehicleSnapshot;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Valutatore della dinamicità dello scenario tra due snapshot consecutivi.
 *
 * <p>La classe confronta lo stato precedente {@code S_{k-1}} con lo stato
 * corrente {@code S_k} e sintetizza il cambiamento osservato in un
 * {@link DynamicityBreakdown}. Il risultato è pensato per essere consumato dal
 * gestore temporale, che potrà decidere quanto riutilizzare della popolazione
 * genetica prodotta nella finestra precedente.</p>
 *
 * <p>La dinamicità globale viene calcolata come media pesata di quattro
 * componenti normalizzate nell'intervallo {@code [0, 1]}:</p>
 *
 * <pre>
 * D_k =
 *     lambdaVehicles  * vehicleVariation
 *   + lambdaTasks     * taskVariation
 *   + lambdaResources * resourceVariation
 *   + lambdaLinks     * linkVariation
 * </pre>
 *
 * <p>Ogni componente vale {@code 0} quando non viene rilevato alcun
 * cambiamento e tende a {@code 1} quando il cambiamento è massimo. Le soglie
 * {@code thetaLow} e {@code thetaHigh}, definite in {@link TemporalWindowConfig},
 * trasformano poi il valore numerico in un {@link DynamicityLevel}.</p>
 *
 * <p>Responsabilità escluse: questa classe non esegue il MA-GA, non modifica
 * popolazioni genetiche e non legge file JSON. Il suo compito è solo misurare
 * e classificare il cambiamento tra due snapshot già caricati e validati.</p>
 */
public final class DynamicityEvaluator {

    /**
     * Soglia numerica minima usata come denominatore nelle differenze relative.
     *
     * <p>Serve a evitare divisioni per zero quando sia il valore precedente sia
     * quello corrente sono nulli o estremamente piccoli.</p>
     */
    private static final double EPSILON = 1.0E-9;

    /**
     * Configurazione temporale da cui vengono lette soglie e pesi della
     * dinamicità.
     *
     * <p>In particolare, questa classe usa:</p>
     *
     * <ul>
     *     <li>i pesi lambda normalizzati delle quattro componenti;</li>
     *     <li>{@code thetaLow} e {@code thetaHigh} per la classificazione.</li>
     * </ul>
     */
    private final TemporalWindowConfig config;

    /**
     * Crea un valutatore legato a una specifica configurazione temporale.
     *
     * @param config configurazione contenente pesi e soglie della dinamicità
     * @throws NullPointerException se {@code config} è {@code null}
     */
    public DynamicityEvaluator(TemporalWindowConfig config) {
        this.config = Objects.requireNonNull(
                config,
                "config must not be null."
        );
    }

    // ---------------------------------------------------------------------
    // API pubblica
    // ---------------------------------------------------------------------

    /**
     * Confronta due snapshot e restituisce il breakdown della dinamicità.
     *
     * <p>Lo snapshot corrente è sempre obbligatorio. Lo snapshot precedente può
     * invece essere {@code null} solo nel caso della prima finestra temporale:
     * in quel caso la dinamicità non è misurabile, quindi viene restituito un
     * breakdown speciale con livello {@link DynamicityLevel#UNKNOWN} e modalità
     * di riuso {@link PopulationReuseMode#FIRST_RUN}.</p>
     *
     * <p>Quando entrambi gli snapshot sono disponibili, il metodo procede in
     * quattro passi:</p>
     *
     * <ol>
     *     <li>calcola le variazioni elementari di veicoli, task, risorse e link;</li>
     *     <li>combina le componenti in un indice globale pesato;</li>
     *     <li>classifica l'indice globale in stabile, moderato o alto;</li>
     *     <li>traduce il livello nella strategia di riuso della popolazione.</li>
     * </ol>
     *
     * @param previousSnapshot snapshot precedente, oppure {@code null} alla prima esecuzione
     * @param currentSnapshot snapshot corrente da valutare
     * @return breakdown completo della dinamicità rilevata
     * @throws NullPointerException se {@code currentSnapshot} è {@code null}
     */
    public DynamicityBreakdown evaluate(
            SystemSnapshot previousSnapshot,
            SystemSnapshot currentSnapshot
    ) {
        // Lo snapshot corrente è l'unico input sempre indispensabile.
        Objects.requireNonNull(
                currentSnapshot,
                "currentSnapshot must not be null."
        );

        // Primo snapshot della sequenza: non esiste ancora una differenza da misurare.
        if (previousSnapshot == null) {
            return DynamicityBreakdown.firstRun(
                    currentSnapshot.getSnapshotId(),
                    currentSnapshot.getTimeSeconds()
            );
        }

        // Componenti elementari, tutte normalizzate nell'intervallo [0, 1].
        double vehicleVariation = computeVehicleVariation(
                previousSnapshot,
                currentSnapshot
        );

        double taskVariation = computeTaskVariation(
                previousSnapshot,
                currentSnapshot
        );

        double resourceVariation = computeResourceVariation(
                previousSnapshot,
                currentSnapshot
        );

        double linkVariation = computeLinkVariation(
                previousSnapshot,
                currentSnapshot
        );

        // Sintesi pesata delle componenti secondo i lambda configurati.
        double globalDynamicity = computeGlobalDynamicity(
                vehicleVariation,
                taskVariation,
                resourceVariation,
                linkVariation
        );

        // Il valore numerico viene trasformato in decisione qualitativa e operativa.
        DynamicityLevel level = classify(globalDynamicity);
        PopulationReuseMode reuseMode = level.toReuseMode();

        // L'oggetto di ritorno conserva sia le componenti sia la decisione finale.
        return new DynamicityBreakdown(
                previousSnapshot.getSnapshotId(),
                currentSnapshot.getSnapshotId(),
                previousSnapshot.getTimeSeconds(),
                currentSnapshot.getTimeSeconds(),
                vehicleVariation,
                taskVariation,
                resourceVariation,
                linkVariation,
                globalDynamicity,
                level,
                reuseMode
        );
    }

    // ---------------------------------------------------------------------
    // Calcolo delle quattro componenti di dinamicità
    // ---------------------------------------------------------------------

    /**
     * Variazione dei veicoli osservati.
     *
     * <p>La componente confronta gli insiemi degli identificativi dei veicoli,
     * senza considerare posizione, velocità o CPU locale. Questo significa che
     * misura la variazione della composizione dello scenario, non il movimento
     * dei singoli veicoli già presenti.</p>
     *
     * <p>La distanza usata è:</p>
     *
     * <pre>
     * 1 - |intersection| / |union|
     * </pre>
     *
     * <p>Se gli insiemi sono identici, la variazione è {@code 0}. Se non hanno
     * elementi in comune, la variazione è {@code 1}.</p>
     */
    private double computeVehicleVariation(
            SystemSnapshot previousSnapshot,
            SystemSnapshot currentSnapshot
    ) {
        return computeSetVariation(
                previousSnapshot.getVehicles(),
                currentSnapshot.getVehicles(),
                VehicleSnapshot::getVehicleId
        );
    }

    /**
     * Variazione dei task attivi.
     *
     * <p>Come per i veicoli, questa componente confronta solo gli identificativi
     * dei task. Non valuta se un task già presente abbia cambiato dimensione,
     * deadline o cicli CPU richiesti: quel tipo di variazione non è modellato
     * qui.</p>
     */
    private double computeTaskVariation(
            SystemSnapshot previousSnapshot,
            SystemSnapshot currentSnapshot
    ) {
        return computeSetVariation(
                previousSnapshot.getTasks(),
                currentSnapshot.getTasks(),
                TaskInstance::getTaskId
        );
    }

    /**
     * Variazione delle risorse computazionali.
     *
     * <p>Nel modello attuale le risorse CPU remote sono contenute nei
     * {@link NodeCandidate} source-aware, ma la CPU è interpretata come
     * proprietà del nodo fisico di esecuzione. Per questo motivo la risorsa
     * viene aggregata per {@code executionNodeId}, non per {@code candidateId}.</p>
     *
     * <p>Si aggiunge anche la CPU locale dei veicoli, indicizzata per
     * {@code vehicleId}, così variazioni di {@code localCpu} restano visibili
     * anche se i candidati locali non fossero presenti o non fossero ancora
     * completi.</p>
     */
    private double computeResourceVariation(
            SystemSnapshot previousSnapshot,
            SystemSnapshot currentSnapshot
    ) {
        Map<String, Double> previousResources = buildResourceMap(previousSnapshot);
        Map<String, Double> currentResources = buildResourceMap(currentSnapshot);

        return computeNumericMapVariation(previousResources, currentResources);
    }

    /**
     * Variazione dei link/candidati.
     *
     * <p>Nel modello source-aware un candidato rappresenta anche la relazione
     * sorgente-destinazione. Per questo motivo la variazione dei link viene
     * calcolata per {@code candidateId} considerando:</p>
     *
     * <ul>
     *     <li>banda disponibile;</li>
     *     <li>latenza base;</li>
     *     <li>tempo di copertura stimato.</li>
     * </ul>
     *
     * <p>Se un candidato esiste in uno snapshot ma non nell'altro, la sua
     * variazione è considerata massima e vale {@code 1}.</p>
     */
    private double computeLinkVariation(
            SystemSnapshot previousSnapshot,
            SystemSnapshot currentSnapshot
    ) {
        Map<String, LinkMetrics> previousLinks = buildLinkMap(previousSnapshot);
        Map<String, LinkMetrics> currentLinks = buildLinkMap(currentSnapshot);

        Set<String> allIds = union(previousLinks.keySet(), currentLinks.keySet());

        if (allIds.isEmpty()) {
            return 0.0;
        }

        double sum = 0.0;

        for (String candidateId : allIds) {
            LinkMetrics previous = previousLinks.get(candidateId);
            LinkMetrics current = currentLinks.get(candidateId);

            // Link comparso o scomparso: cambiamento strutturale massimo.
            if (previous == null || current == null) {
                sum += 1.0;
            } else {
                // Link presente in entrambi gli snapshot: confronto numerico delle metriche.
                sum += previous.relativeVariation(current);
            }
        }

        return clamp01(sum / allIds.size());
    }

    /**
     * Combina le quattro componenti elementari in un solo indice globale.
     *
     * <p>I pesi usati sono quelli normalizzati dalla configurazione. Questo
     * rende il calcolo robusto anche se i lambda originali non sommano
     * esattamente a {@code 1}.</p>
     */
    private double computeGlobalDynamicity(
            double vehicleVariation,
            double taskVariation,
            double resourceVariation,
            double linkVariation
    ) {
        double value =
                config.getNormalizedLambdaVehicles() * vehicleVariation
                        + config.getNormalizedLambdaTasks() * taskVariation
                        + config.getNormalizedLambdaResources() * resourceVariation
                        + config.getNormalizedLambdaLinks() * linkVariation;

        return clamp01(value);
    }

    /**
     * Traduce il valore numerico della dinamicità in un livello qualitativo.
     *
     * <p>Le soglie sono interpretate così:</p>
     *
     * <ul>
     *     <li>{@code D < thetaLow}: scenario stabile;</li>
     *     <li>{@code thetaLow <= D <= thetaHigh}: scenario moderatamente dinamico;</li>
     *     <li>{@code D > thetaHigh}: scenario molto dinamico.</li>
     * </ul>
     */
    private DynamicityLevel classify(double globalDynamicity) {
        if (globalDynamicity < config.getThetaLow()) {
            return DynamicityLevel.STABLE;
        }

        if (globalDynamicity <= config.getThetaHigh()) {
            return DynamicityLevel.MODERATE;
        }

        return DynamicityLevel.HIGH;
    }

    // ---------------------------------------------------------------------
    // Costruzione delle rappresentazioni confrontabili
    // ---------------------------------------------------------------------

    /**
     * Costruisce una mappa normalizzata delle risorse CPU disponibili.
     *
     * <p>La chiave distingue esplicitamente due categorie:</p>
     *
     * <ul>
     *     <li>{@code vehicle:<vehicleId>} per la CPU locale di ogni veicolo;</li>
     *     <li>{@code executionNode:<executionNodeId>} per la CPU dei nodi candidati.</li>
     * </ul>
     *
     * <p>Quando più candidati descrivono lo stesso nodo di esecuzione, viene
     * mantenuto il massimo valore di CPU disponibile. Questa scelta evita di
     * contare più volte la stessa risorsa fisica solo perché compare in più
     * relazioni source-aware.</p>
     */
    private Map<String, Double> buildResourceMap(SystemSnapshot snapshot) {
        Map<String, Double> resources = new HashMap<>();

        // CPU locale dei veicoli: risorsa direttamente associata al vehicleId.
        for (VehicleSnapshot vehicle : snapshot.getVehicles()) {
            resources.put(
                    "vehicle:" + vehicle.getVehicleId(),
                    vehicle.getLocalCpu()
            );
        }

        // CPU dei nodi candidati: aggregata per nodo fisico di esecuzione.
        for (NodeCandidate candidate : snapshot.getCandidateNodes()) {
            String key = "executionNode:" + candidate.getExecutionNodeId();

            resources.merge(
                    key,
                    candidate.getAvailableCpu(),
                    Math::max
            );
        }

        return resources;
    }

    /**
     * Costruisce una mappa dei link/candidati confrontabili tra snapshot.
     *
     * In questa versione il tempo di copertura non viene più letto dal candidato.
     * La variazione del link considera banda disponibile e latenza base.
     */
    private Map<String, LinkMetrics> buildLinkMap(SystemSnapshot snapshot) {
        Map<String, LinkMetrics> links = new HashMap<>();

        for (NodeCandidate candidate : snapshot.getCandidateNodes()) {
            links.put(
                    candidate.getCandidateId(),
                    new LinkMetrics(
                            candidate.getAvailableBandwidth(),
                            candidate.getBaseLatencySeconds()
                    )
            );
        }

        return links;
    }

    // ---------------------------------------------------------------------
    // Primitive comuni di confronto
    // ---------------------------------------------------------------------

    /**
     * Calcola la variazione tra due collezioni trattandole come insiemi di ID.
     *
     * <p>La funzione {@code idExtractor} definisce quale campo dell'oggetto
     * identifica semanticamente l'elemento. Gli ID nulli o vuoti vengono
     * ignorati da {@link #extractIds(Collection, Function)}.</p>
     *
     * <p>Formula usata:</p>
     *
     * <pre>
     * setVariation = 1 - |previousIds ∩ currentIds| / |previousIds ∪ currentIds|
     * </pre>
     */
    private <T> double computeSetVariation(
            Collection<T> previousItems,
            Collection<T> currentItems,
            Function<T, String> idExtractor
    ) {
        Set<String> previousIds = extractIds(previousItems, idExtractor);
        Set<String> currentIds = extractIds(currentItems, idExtractor);

        // L'unione rappresenta tutto ciò che esisteva prima o esiste ora.
        Set<String> union = union(previousIds, currentIds);

        if (union.isEmpty()) {
            return 0.0;
        }

        // L'intersezione rappresenta ciò che è rimasto stabile tra i due snapshot.
        Set<String> intersection = new HashSet<>(previousIds);
        intersection.retainAll(currentIds);

        return clamp01(1.0 - ((double) intersection.size() / union.size()));
    }

    /**
     * Calcola la variazione media tra due mappe numeriche indicizzate per chiave.
     *
     * <p>Ogni chiave rappresenta una risorsa o una metrica confrontabile. Se la
     * chiave compare in una sola delle due mappe, il cambiamento è strutturale e
     * vale {@code 1}. Se compare in entrambe, viene usata una differenza
     * relativa normalizzata.</p>
     */
    private double computeNumericMapVariation(
            Map<String, Double> previousValues,
            Map<String, Double> currentValues
    ) {
        Set<String> allKeys = union(previousValues.keySet(), currentValues.keySet());

        if (allKeys.isEmpty()) {
            return 0.0;
        }

        double sum = 0.0;

        for (String key : allKeys) {
            Double previous = previousValues.get(key);
            Double current = currentValues.get(key);

            // Valore comparso o scomparso: variazione massima per quella chiave.
            if (previous == null || current == null) {
                sum += 1.0;
            } else {
                sum += relativeDifference(previous, current);
            }
        }

        return clamp01(sum / allKeys.size());
    }

    /**
     * Estrae gli identificativi significativi da una collezione.
     *
     * <p>Il metodo è tollerante verso collezioni nulle, elementi nulli e ID
     * mancanti. Questa tolleranza permette alle metriche insiemistiche di
     * restituire un valore sensato anche in presenza di liste opzionali o
     * parzialmente popolate.</p>
     */
    private <T> Set<String> extractIds(
            Collection<T> items,
            Function<T, String> idExtractor
    ) {
        Set<String> ids = new HashSet<>();

        if (items == null) {
            return ids;
        }

        for (T item : items) {
            if (item == null) {
                continue;
            }

            String id = idExtractor.apply(item);

            if (id != null && !id.isBlank()) {
                ids.add(id);
            }
        }

        return ids;
    }

    /**
     * Restituisce l'unione di due insiemi di stringhe.
     *
     * <p>Il metodo accetta anche insiemi nulli, trattandoli come insiemi vuoti.
     * Questa scelta rende più semplici i chiamanti e centralizza la gestione
     * difensiva delle strutture opzionali.</p>
     */
    private Set<String> union(Set<String> first, Set<String> second) {
        Set<String> result = new HashSet<>();

        if (first != null) {
            result.addAll(first);
        }

        if (second != null) {
            result.addAll(second);
        }

        return result;
    }

    /**
     * Calcola la differenza relativa tra due valori numerici.
     *
     * <p>La normalizzazione usa il massimo tra i valori assoluti confrontati e
     * {@link #EPSILON}. In questo modo il risultato resta nell'intervallo
     * {@code [0, 1]} anche quando i valori sono molto piccoli.</p>
     *
     * <pre>
     * relativeDifference = |previous - current| / max(|previous|, |current|, EPSILON)
     * </pre>
     *
     * <p>Valori non finiti sono trattati come variazione massima.</p>
     */
    private double relativeDifference(double previous, double current) {
        if (!Double.isFinite(previous) || !Double.isFinite(current)) {
            return 1.0;
        }

        double denominator = Math.max(
                Math.max(Math.abs(previous), Math.abs(current)),
                EPSILON
        );

        return clamp01(Math.abs(previous - current) / denominator);
    }

    /**
     * Forza un valore numerico nell'intervallo {@code [0, 1]}.
     *
     * <p>Questa classe usa {@code [0, 1]} come dominio comune per tutte le
     * componenti di dinamicità. Un valore non finito viene considerato come
     * massimo cambiamento possibile, quindi viene riportato a {@code 1}.</p>
     */
    private double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 1.0;
        }

        if (value < 0.0) {
            return 0.0;
        }

        if (value > 1.0) {
            return 1.0;
        }

        return value;
    }

    /**
     * Metriche usate per confrontare un candidato/link tra due snapshot.
     *
     * La copertura non è più una proprietà statica del candidato.
     * Verrà gestita da CoverageEstimator e dai futuri componenti temporali.
     */
    private final class LinkMetrics {

        private final double availableBandwidth;
        private final double baseLatencySeconds;

        private LinkMetrics(
                double availableBandwidth,
                double baseLatencySeconds
        ) {
            this.availableBandwidth = availableBandwidth;
            this.baseLatencySeconds = baseLatencySeconds;
        }

        private double relativeVariation(LinkMetrics other) {
            double bandwidthVariation = relativeDifference(
                    availableBandwidth,
                    other.availableBandwidth
            );

            double latencyVariation = relativeDifference(
                    baseLatencySeconds,
                    other.baseLatencySeconds
            );

            return clamp01(
                    (bandwidthVariation + latencyVariation) / 2.0
            );
        }
    }
}



