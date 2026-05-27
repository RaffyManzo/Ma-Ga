package window;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Implementazione statica di {@link CriticalEventDetector} basata su una lista
 * predefinita di eventi critici.
 *
 * <p>Questa classe è pensata per test e prime simulazioni senza integrazione
 * con MOSAIC o con un monitor runtime. Riceve una lista di eventi, la ordina
 * per tempo simulato e restituisce il primo evento critico rilevante in un
 * intervallo temporale richiesto.</p>
 *
 * <p>Vengono restituiti solo eventi la cui severità richiede una
 * riottimizzazione anticipata, secondo
 * {@link CriticalEvent#shouldTriggerReoptimization()}.</p>
 */
public final class StaticCriticalEventDetector implements CriticalEventDetector {

    /**
     * Eventi disponibili, ordinati per {@code eventTimeSeconds} crescente.
     */
    private final List<CriticalEvent> events;

    /**
     * Crea un detector statico.
     *
     * <p>La lista ricevuta viene copiata, validata e ordinata. La lista interna
     * è immutabile, così le successive modifiche alla lista del chiamante non
     * cambiano il comportamento del detector.</p>
     *
     * @param events eventi disponibili ordinabili per tempo
     */
    public StaticCriticalEventDetector(List<CriticalEvent> events) {
        if (events == null) {
            throw new IllegalArgumentException("events must not be null.");
        }

        List<CriticalEvent> copiedEvents = new ArrayList<>();

        for (CriticalEvent event : events) {
            // Un evento nullo renderebbe ambiguo ordinamento e filtro per severità.
            if (event == null) {
                throw new IllegalArgumentException("events must not contain null elements.");
            }

            copiedEvents.add(event);
        }

        copiedEvents.sort(
                Comparator.comparingDouble(CriticalEvent::getEventTimeSeconds)
        );

        this.events = Collections.unmodifiableList(copiedEvents);
    }

    /**
     * Crea un detector senza eventi critici.
     *
     * <p>È utile quando si vuole eseguire il ciclo temporale solo con scadenze
     * programmate, senza anticipi dovuti a eventi.</p>
     *
     * @return detector vuoto
     */
    public static StaticCriticalEventDetector empty() {
        return new StaticCriticalEventDetector(Collections.emptyList());
    }

    /**
     * Restituisce il primo evento nell'intervallo indicato.
     *
     * <p>L'intervallo è aperto a sinistra e chiuso a destra:</p>
     *
     * <pre>
     * currentTimeSeconds < eventTimeSeconds <= maxTimeSeconds
     * </pre>
     *
     * <p>Vengono considerati solo eventi con severità sufficiente ad attivare
     * una riesecuzione.</p>
     */
    @Override
    public Optional<CriticalEvent> findNextCriticalEvent(
            double currentTimeSeconds,
            double maxTimeSeconds
    ) {
        validateTimeRange(currentTimeSeconds, maxTimeSeconds);

        for (CriticalEvent event : events) {
            double eventTime = event.getEventTimeSeconds();

            // Il tempo corrente è escluso per non riprocessare l'evento già consumato.
            boolean isAfterCurrentTime = eventTime > currentTimeSeconds;

            // Il limite superiore è incluso: un evento alla scadenza programmata resta valido.
            boolean isBeforeMaxTime = eventTime <= maxTimeSeconds;

            if (isAfterCurrentTime
                    && isBeforeMaxTime
                    && event.shouldTriggerReoptimization()) {
                return Optional.of(event);
            }
        }

        return Optional.empty();
    }

    /**
     * @return lista immutabile degli eventi ordinati per tempo simulato
     */
    public List<CriticalEvent> getEvents() {
        return events;
    }

    /**
     * @return {@code true} se non sono stati configurati eventi critici
     */
    public boolean isEmpty() {
        return events.isEmpty();
    }

    /**
     * Valida l'intervallo temporale richiesto al detector.
     *
     * <p>Entrambi gli estremi devono essere finiti e non negativi; il limite
     * superiore non può precedere il tempo corrente.</p>
     */
    private static void validateTimeRange(
            double currentTimeSeconds,
            double maxTimeSeconds
    ) {
        validateFiniteAndNonNegative("currentTimeSeconds", currentTimeSeconds);
        validateFiniteAndNonNegative("maxTimeSeconds", maxTimeSeconds);

        if (maxTimeSeconds < currentTimeSeconds) {
            throw new IllegalArgumentException(
                    "maxTimeSeconds must be >= currentTimeSeconds."
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
}
