package window;

import java.util.Objects;

/**
 * Causa concreta di una riesecuzione del MA-GA.
 *
 * <p>Questo oggetto rappresenta il punto in cui il gestore temporale decide che
 * una nuova ottimizzazione deve essere avviata. Risponde a due domande:</p>
 *
 * <ul>
 *     <li>quando avviene la riesecuzione, espresso in tempo simulato;</li>
 *     <li>perché avviene, espresso tramite {@link TriggerReason}.</li>
 * </ul>
 *
 * <p>Nel caso di {@link TriggerReason#CRITICAL_EVENT}, il trigger conserva
 * anche l'evento critico che ha anticipato la fine naturale della finestra. Nei
 * casi programmati, invece, non deve esistere alcun evento associato.</p>
 */
public final class ReoptimizationTrigger {

    /**
     * Tempo simulato in cui viene richiesta la nuova ottimizzazione.
     */
    private final double triggerTimeSeconds;

    /**
     * Motivo temporale della riesecuzione.
     */
    private final TriggerReason reason;

    /**
     * Evento critico associato al trigger.
     *
     * <p>È valorizzato solo quando {@link #reason} è
     * {@link TriggerReason#CRITICAL_EVENT}; negli altri casi deve essere
     * {@code null}.</p>
     */
    private final CriticalEvent criticalEvent;

    /**
     * Costruisce un trigger di riesecuzione.
     *
     * <p>Regola di consistenza principale:</p>
     *
     * <ul>
     *     <li>se {@code reason == CRITICAL_EVENT}, {@code criticalEvent} deve essere valorizzato;</li>
     *     <li>se {@code reason != CRITICAL_EVENT}, {@code criticalEvent} deve essere {@code null}.</li>
     * </ul>
     *
     * <p>Questa regola evita stati ambigui, per esempio un trigger dichiarato
     * critico ma senza evento associato.</p>
     *
     * @param triggerTimeSeconds tempo simulato della riesecuzione
     * @param reason motivo della riesecuzione
     * @param criticalEvent evento critico associato, solo per trigger critici
     */
    public ReoptimizationTrigger(
            double triggerTimeSeconds,
            TriggerReason reason,
            CriticalEvent criticalEvent
    ) {
        // Il trigger vive sulla linea temporale simulata e non può avere tempo negativo.
        validateFiniteAndNonNegative(
                "triggerTimeSeconds",
                triggerTimeSeconds
        );

        this.triggerTimeSeconds = triggerTimeSeconds;

        this.reason = Objects.requireNonNull(
                reason,
                "reason must not be null."
        );

        // Mantiene allineati motivo e payload opzionale dell'evento critico.
        validateCriticalEventConsistency(reason, criticalEvent);
        this.criticalEvent = criticalEvent;
    }

    /**
     * Crea un trigger per la prima esecuzione del sistema.
     *
     * <p>Non esiste ancora una finestra precedente e non c'è alcun evento
     * critico associato.</p>
     *
     * @param triggerTimeSeconds tempo simulato della prima esecuzione
     * @return trigger con motivo {@link TriggerReason#FIRST_RUN}
     */
    public static ReoptimizationTrigger firstRun(double triggerTimeSeconds) {
        return new ReoptimizationTrigger(
                triggerTimeSeconds,
                TriggerReason.FIRST_RUN,
                null
        );
    }

    /**
     * Crea un trigger per scadenza naturale della finestra temporale.
     *
     * <p>Questo corrisponde al caso:</p>
     *
     * <pre>
     *     t_{k+1} = t_k + Delta_t
     * </pre>
     *
     * <p>senza eventi critici intermedi.</p>
     *
     * @param triggerTimeSeconds tempo simulato della scadenza programmata
     * @return trigger con motivo {@link TriggerReason#SCHEDULED_WINDOW_EXPIRATION}
     */
    public static ReoptimizationTrigger scheduledExpiration(
            double triggerTimeSeconds
    ) {
        return new ReoptimizationTrigger(
                triggerTimeSeconds,
                TriggerReason.SCHEDULED_WINDOW_EXPIRATION,
                null
        );
    }

    /**
     * Crea un trigger causato da evento critico.
     *
     * <p>Questo corrisponde al caso:</p>
     *
     * <pre>
     *     t_crit < t_k + Delta_t
     * </pre>
     *
     * <p>quindi la nuova ottimizzazione viene anticipata rispetto alla scadenza
     * naturale della finestra.</p>
     *
     * @param criticalEvent evento che causa la riesecuzione
     * @return trigger con motivo {@link TriggerReason#CRITICAL_EVENT}
     */
    public static ReoptimizationTrigger criticalEvent(
            CriticalEvent criticalEvent
    ) {
        Objects.requireNonNull(
                criticalEvent,
                "criticalEvent must not be null."
        );

        return new ReoptimizationTrigger(
                criticalEvent.getEventTimeSeconds(),
                TriggerReason.CRITICAL_EVENT,
                criticalEvent
        );
    }

    /**
     * @return tempo simulato in cui avviene la riesecuzione
     */
    public double getTriggerTimeSeconds() {
        return triggerTimeSeconds;
    }

    /**
     * @return motivo della riesecuzione
     */
    public TriggerReason getReason() {
        return reason;
    }

    /**
     * @return evento critico associato, oppure {@code null} per trigger non critici
     */
    public CriticalEvent getCriticalEvent() {
        return criticalEvent;
    }

    /**
     * Restituisce true se il trigger deriva da un evento critico.
     *
     * @return {@code true} se {@link #getReason()} è {@link TriggerReason#CRITICAL_EVENT}
     */
    public boolean isCriticalEventTrigger() {
        return reason == TriggerReason.CRITICAL_EVENT;
    }

    /**
     * Restituisce true se il trigger deriva dalla scadenza naturale della finestra.
     *
     * @return {@code true} se il trigger è programmato
     */
    public boolean isScheduledExpiration() {
        return reason == TriggerReason.SCHEDULED_WINDOW_EXPIRATION;
    }

    /**
     * Restituisce true se il trigger rappresenta la prima esecuzione.
     *
     * @return {@code true} se il trigger è la prima esecuzione
     */
    public boolean isFirstRun() {
        return reason == TriggerReason.FIRST_RUN;
    }

    /**
     * Controlla la coerenza tra reason e criticalEvent.
     *
     * <p>Questa validazione serve a evitare oggetti semanticamente inconsistenti
     * e concentra in un solo punto l'invariante tra motivo e payload
     * opzionale.</p>
     */
    private static void validateCriticalEventConsistency(
            TriggerReason reason,
            CriticalEvent criticalEvent
    ) {
        if (reason == TriggerReason.CRITICAL_EVENT && criticalEvent == null) {
            throw new IllegalArgumentException(
                    "criticalEvent must not be null when reason is CRITICAL_EVENT."
            );
        }

        if (reason != TriggerReason.CRITICAL_EVENT && criticalEvent != null) {
            throw new IllegalArgumentException(
                    "criticalEvent must be null when reason is not CRITICAL_EVENT."
            );
        }
    }

    /**
     * Valida che un tempo simulato sia finito e non negativo.
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
        return "ReoptimizationTrigger{"
                + "triggerTimeSeconds=" + triggerTimeSeconds
                + ", reason=" + reason
                + ", criticalEvent=" + criticalEvent
                + '}';
    }
}
