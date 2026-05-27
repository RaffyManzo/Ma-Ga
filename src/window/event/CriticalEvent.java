package window.event;

import model.snapshot.SystemSnapshot;
import window.trigger.ReoptimizationTrigger;
import window.trigger.TriggerReason;

import java.util.Objects;

/**
 * Evento critico osservato durante l'esecuzione temporale del sistema.
 *
 * <p>Questa classe separa in modo esplicito tre concetti che nel gestore
 * temporale devono restare distinti:</p>
 *
 * <ul>
 *     <li>la finestra temporale programmata;</li>
 *     <li>l'evento imprevisto che può invalidare la strategia corrente;</li>
 *     <li>lo snapshot aggiornato che verrà eventualmente ottimizzato dopo l'evento.</li>
 * </ul>
 *
 * <p>Un {@code CriticalEvent} non è uno snapshot, non contiene lo stato completo
 * del sistema e non esegue il MA-GA. Il suo ruolo è rappresentare il fatto che,
 * a un certo tempo simulato, è accaduto qualcosa che può rendere non più
 * affidabile la strategia corrente di offloading.</p>
 *
 * <p>Dopo un evento critico, il gestore temporale potrà richiedere o selezionare
 * uno {@code SystemSnapshot} aggiornato e costruire un
 * {@link ReoptimizationTrigger} di tipo {@link TriggerReason#CRITICAL_EVENT}.</p>
 */
public final class CriticalEvent {

    /**
     * Identificativo univoco dell'evento.
     */
    private final String eventId;

    /**
     * Tempo simulato in cui l'evento è stato osservato.
     */
    private final double eventTimeSeconds;

    /**
     * Tipologia semantica dell'evento.
     */
    private final CriticalEventType type;

    /**
     * Severità dell'evento, usata per capire se proporre una riesecuzione.
     */
    private final CriticalEventSeverity severity;

    /**
     * Veicolo interessato dall'evento, se applicabile.
     */
    private final String affectedVehicleId;

    /**
     * Task interessato dall'evento, se applicabile.
     */
    private final String affectedTaskId;

    /**
     * Candidato/link interessato dall'evento, se applicabile.
     */
    private final String affectedCandidateId;

    /**
     * Nodo fisico di esecuzione interessato dall'evento, se applicabile.
     */
    private final String affectedExecutionNodeId;

    /**
     * Descrizione opzionale, utile per log, report o simulazioni.
     */
    private final String description;

    /**
     * Costruisce un evento critico.
     *
     * <p>I campi {@code affected*} sono opzionali perché non tutti gli eventi
     * riguardano lo stesso tipo di entità. Il costruttore normalizza le stringhe
     * opzionali vuote a {@code null}, così il resto del codice può distinguere
     * facilmente tra campo assente e campo valorizzato.</p>
     *
     * <p>Esempi di associazione:</p>
     *
     * <ul>
     *     <li>{@link CriticalEventType#VEHICLE_LEFT}: {@code affectedVehicleId};</li>
     *     <li>{@link CriticalEventType#TASK_ARRIVAL}: {@code affectedTaskId};</li>
     *     <li>{@link CriticalEventType#LINK_DEGRADED}: {@code affectedCandidateId};</li>
     *     <li>{@link CriticalEventType#NODE_RESOURCE_DROP}: {@code affectedExecutionNodeId}.</li>
     * </ul>
     *
     * @param eventId identificativo univoco dell'evento
     * @param eventTimeSeconds tempo simulato dell'evento
     * @param type tipologia dell'evento
     * @param severity severità dell'evento
     * @param affectedVehicleId veicolo interessato, opzionale
     * @param affectedTaskId task interessato, opzionale
     * @param affectedCandidateId candidato/link interessato, opzionale
     * @param affectedExecutionNodeId nodo di esecuzione interessato, opzionale
     * @param description descrizione libera opzionale
     */
    public CriticalEvent(
            String eventId,
            double eventTimeSeconds,
            CriticalEventType type,
            CriticalEventSeverity severity,
            String affectedVehicleId,
            String affectedTaskId,
            String affectedCandidateId,
            String affectedExecutionNodeId,
            String description
    ) {
        // L'ID è obbligatorio: ogni evento deve essere tracciabile nei report.
        this.eventId = requireText(eventId, "eventId");

        validateFiniteAndNonNegative(
                "eventTimeSeconds",
                eventTimeSeconds
        );

        this.eventTimeSeconds = eventTimeSeconds;

        this.type = Objects.requireNonNull(
                type,
                "type must not be null."
        );

        this.severity = Objects.requireNonNull(
                severity,
                "severity must not be null."
        );

        // I riferimenti alle entità coinvolte sono opzionali e dipendono dal tipo.
        this.affectedVehicleId = normalizeOptionalText(affectedVehicleId);
        this.affectedTaskId = normalizeOptionalText(affectedTaskId);
        this.affectedCandidateId = normalizeOptionalText(affectedCandidateId);
        this.affectedExecutionNodeId = normalizeOptionalText(affectedExecutionNodeId);
        this.description = normalizeOptionalText(description);
    }

    /**
     * Factory method per creare un evento che riguarda un veicolo.
     *
     * <p>Utile per eventi come {@link CriticalEventType#VEHICLE_JOINED},
     * {@link CriticalEventType#VEHICLE_LEFT}, {@link CriticalEventType#COVERAGE_RISK}
     * o {@link CriticalEventType#HANDOVER_RISK}.</p>
     *
     * @return evento critico con solo {@code affectedVehicleId} valorizzato
     */
    public static CriticalEvent forVehicle(
            String eventId,
            double eventTimeSeconds,
            CriticalEventType type,
            CriticalEventSeverity severity,
            String affectedVehicleId,
            String description
    ) {
        return new CriticalEvent(
                eventId,
                eventTimeSeconds,
                type,
                severity,
                affectedVehicleId,
                null,
                null,
                null,
                description
        );
    }

    /**
     * Factory method per creare un evento che riguarda un task.
     *
     * <p>Utile per eventi come {@link CriticalEventType#TASK_ARRIVAL},
     * {@link CriticalEventType#TASK_REMOVED} o
     * {@link CriticalEventType#DEADLINE_RISK}.</p>
     *
     * @return evento critico con solo {@code affectedTaskId} valorizzato
     */
    public static CriticalEvent forTask(
            String eventId,
            double eventTimeSeconds,
            CriticalEventType type,
            CriticalEventSeverity severity,
            String affectedTaskId,
            String description
    ) {
        return new CriticalEvent(
                eventId,
                eventTimeSeconds,
                type,
                severity,
                null,
                affectedTaskId,
                null,
                null,
                description
        );
    }

    /**
     * Factory method per creare un evento che riguarda un candidato/link.
     *
     * <p>Utile per eventi come {@link CriticalEventType#LINK_DEGRADED},
     * {@link CriticalEventType#LINK_LOST} o
     * {@link CriticalEventType#COVERAGE_RISK} quando il rischio è riferito a
     * uno specifico {@code candidateId}.</p>
     *
     * @return evento critico con solo {@code affectedCandidateId} valorizzato
     */
    public static CriticalEvent forCandidate(
            String eventId,
            double eventTimeSeconds,
            CriticalEventType type,
            CriticalEventSeverity severity,
            String affectedCandidateId,
            String description
    ) {
        return new CriticalEvent(
                eventId,
                eventTimeSeconds,
                type,
                severity,
                null,
                null,
                affectedCandidateId,
                null,
                description
        );
    }

    /**
     * Factory method per creare un evento che riguarda un nodo fisico
     * di esecuzione.
     *
     * <p>Utile per eventi come {@link CriticalEventType#NODE_RESOURCE_DROP} o
     * {@link CriticalEventType#NODE_RESOURCE_RECOVERY}.</p>
     *
     * @return evento critico con solo {@code affectedExecutionNodeId} valorizzato
     */
    public static CriticalEvent forExecutionNode(
            String eventId,
            double eventTimeSeconds,
            CriticalEventType type,
            CriticalEventSeverity severity,
            String affectedExecutionNodeId,
            String description
    ) {
        return new CriticalEvent(
                eventId,
                eventTimeSeconds,
                type,
                severity,
                null,
                null,
                null,
                affectedExecutionNodeId,
                description
        );
    }

    /**
     * @return identificativo univoco dell'evento
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * @return tempo simulato dell'evento
     */
    public double getEventTimeSeconds() {
        return eventTimeSeconds;
    }

    /**
     * @return tipologia dell'evento
     */
    public CriticalEventType getType() {
        return type;
    }

    /**
     * @return severità dell'evento
     */
    public CriticalEventSeverity getSeverity() {
        return severity;
    }

    /**
     * @return veicolo interessato, oppure {@code null} se non applicabile
     */
    public String getAffectedVehicleId() {
        return affectedVehicleId;
    }

    /**
     * @return task interessato, oppure {@code null} se non applicabile
     */
    public String getAffectedTaskId() {
        return affectedTaskId;
    }

    /**
     * @return candidato/link interessato, oppure {@code null} se non applicabile
     */
    public String getAffectedCandidateId() {
        return affectedCandidateId;
    }

    /**
     * @return nodo di esecuzione interessato, oppure {@code null} se non applicabile
     */
    public String getAffectedExecutionNodeId() {
        return affectedExecutionNodeId;
    }

    /**
     * @return descrizione opzionale dell'evento, oppure {@code null}
     */
    public String getDescription() {
        return description;
    }

    /**
     * Indica se l'evento dovrebbe attivare una riesecuzione anticipata.
     *
     * <p>La scelta finale spetta comunque al gestore temporale. Questo metodo
     * fornisce solo una valutazione locale basata sulla severità.</p>
     *
     * @return {@code true} se la severità è alta o critica
     */
    public boolean shouldTriggerReoptimization() {
        return severity.shouldTriggerReoptimization();
    }

    /**
     * Indica se l'evento riguarda la mobilità.
     *
     * <p>Questo metodo è utile nei report e nelle analisi sperimentali.</p>
     *
     * @return {@code true} se il tipo è classificato come mobility-related
     */
    public boolean isMobilityRelated() {
        return type.isMobilityRelated();
    }

    /**
     * Indica se l'evento riguarda task o deadline.
     *
     * @return {@code true} se il tipo è classificato come task-related
     */
    public boolean isTaskRelated() {
        return type.isTaskRelated();
    }

    /**
     * Indica se l'evento riguarda risorse computazionali o comunicative.
     *
     * @return {@code true} se il tipo è classificato come resource-related
     */
    public boolean isResourceRelated() {
        return type.isResourceRelated();
    }

    /**
     * Verifica che una stringa obbligatoria sia valorizzata.
     *
     * @return stringa originale se valida
     */
    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be null or blank."
            );
        }

        return value;
    }

    /**
     * Normalizza una stringa opzionale.
     *
     * <p>Le stringhe nulle, vuote o composte solo da spazi vengono trattate come
     * assenza del valore e quindi convertite in {@code null}.</p>
     */
    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value;
    }

    /**
     * Valida che un valore numerico sia finito e non negativo.
     *
     * <p>Il tempo simulato dell'evento non può essere infinito, NaN o negativo.</p>
     */
    private static void validateFiniteAndNonNegative(
            String fieldName,
            double value
    ) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite.");
        }

        if (value < 0.0) {
            throw new IllegalArgumentException(fieldName + " must be >= 0.");
        }
    }

    @Override
    public String toString() {
        return "CriticalEvent{"
                + "eventId='" + eventId + '\''
                + ", eventTimeSeconds=" + eventTimeSeconds
                + ", type=" + type
                + ", severity=" + severity
                + ", affectedVehicleId='" + affectedVehicleId + '\''
                + ", affectedTaskId='" + affectedTaskId + '\''
                + ", affectedCandidateId='" + affectedCandidateId + '\''
                + ", affectedExecutionNodeId='" + affectedExecutionNodeId + '\''
                + ", description='" + description + '\''
                + '}';
    }
}



