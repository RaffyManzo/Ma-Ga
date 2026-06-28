package ga.core;

/**
 * Criterio cooperativo di arresto per una singola esecuzione del MA-GA.
 *
 * <p>Non interrompe thread e non modifica fitness, repair o operatori
 * genetici. L'optimizer consulta il budget soltanto in punti sicuri e, quando
 * è esaurito, restituisce il migliore cromosoma completamente riparato e
 * valutato già disponibile.</p>
 */
@FunctionalInterface
public interface GaExecutionBudget {

    boolean isExhausted();

    static GaExecutionBudget unlimited() {
        return () -> false;
    }

    static GaExecutionBudget deadlineNanoTime(long deadlineNanoTime) {
        if (deadlineNanoTime <= 0L) {
            throw new IllegalArgumentException("deadlineNanoTime must be > 0.");
        }
        return () -> System.nanoTime() >= deadlineNanoTime;
    }
}
