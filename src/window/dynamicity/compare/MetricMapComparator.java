package window.dynamicity.compare;

import window.dynamicity.math.DynamicityMath;
import window.dynamicity.metrics.ComparableMetric;

import java.util.Map;
import java.util.Set;

/**
 * Comparator generico per mappe di metriche confrontabili.
 *
 * Regola:
 * - chiave presente solo in uno snapshot: variazione 1;
 * - chiave presente in entrambi: relativeVariation(...);
 * - risultato finale: media normalizzata in [0, 1].
 *
 * @param <T> tipo della metrica confrontabile
 */
public final class MetricMapComparator<T extends ComparableMetric<T>> {

    /**
     * Calcola la variazione media tra due mappe.
     *
     * @param previousValues valori precedenti
     * @param currentValues valori correnti
     * @return variazione normalizzata in [0, 1]
     */
    public double computeVariation(
            Map<String, T> previousValues,
            Map<String, T> currentValues
    ) {
        Set<String> allKeys = DynamicityMath.union(
                previousValues.keySet(),
                currentValues.keySet()
        );

        if (allKeys.isEmpty()) {
            return 0.0;
        }

        double sum = 0.0;

        for (String key : allKeys) {
            T previous = previousValues.get(key);
            T current = currentValues.get(key);

            if (previous == null || current == null) {
                sum += 1.0;
            } else {
                sum += previous.relativeVariation(current);
            }
        }

        return DynamicityMath.clamp01(sum / allKeys.size());
    }
}
