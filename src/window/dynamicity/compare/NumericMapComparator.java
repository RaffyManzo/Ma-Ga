package window.dynamicity.compare;

import window.dynamicity.math.DynamicityMath;

import java.util.Map;
import java.util.Set;

/**
 * Comparator per mappe numeriche.
 *
 * Usato per Dr(k), dove le metriche sono capacità computazionali
 * associate a vehicleId o executionNodeId.
 */
public final class NumericMapComparator {

    /**
     * Calcola la variazione media tra due mappe numeriche.
     *
     * @param previousValues valori precedenti
     * @param currentValues valori correnti
     * @return variazione normalizzata in [0, 1]
     */
    public double computeVariation(
            Map<String, Double> previousValues,
            Map<String, Double> currentValues
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
            Double previous = previousValues.get(key);
            Double current = currentValues.get(key);

            if (previous == null || current == null) {
                sum += 1.0;
            } else {
                sum += DynamicityMath.relativeDifference(previous, current);
            }
        }

        return DynamicityMath.clamp01(sum / allKeys.size());
    }
}
