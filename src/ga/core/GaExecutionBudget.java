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

    /**
     * Restituisce il tempo residuo noto in nanosecondi.
     *
     * <p>{@link Long#MAX_VALUE} indica che il budget non espone una scadenza
     * misurabile. Il metodo è {@code default} per mantenere compatibili i
     * budget esistenti espressi come lambda.</p>
     */
    default long remainingNanos() {
        return Long.MAX_VALUE;
    }

    static GaExecutionBudget unlimited() {
        return () -> false;
    }

    static GaExecutionBudget deadlineNanoTime(long deadlineNanoTime) {
        if (deadlineNanoTime <= 0L) {
            throw new IllegalArgumentException("deadlineNanoTime must be > 0.");
        }
        return new GaExecutionBudget() {
            @Override
            public boolean isExhausted() {
                return System.nanoTime() >= deadlineNanoTime;
            }

            @Override
            public long remainingNanos() {
                long remaining = deadlineNanoTime - System.nanoTime();
                return remaining > 0L ? remaining : 0L;
            }
        };
    }
}
