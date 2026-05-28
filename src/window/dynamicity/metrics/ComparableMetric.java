package window.dynamicity.metrics;

/**
 * Contratto per metriche confrontabili tra due snapshot.
 *
 * @param <T> tipo concreto della metrica
 */
public interface ComparableMetric<T> {

    /**
     * Calcola la variazione relativa rispetto a una metrica corrente.
     *
     * @param other metrica corrente
     * @return variazione normalizzata in [0, 1]
     */
    double relativeVariation(T other);
}
