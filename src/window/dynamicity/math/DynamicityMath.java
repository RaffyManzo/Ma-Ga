package window.dynamicity.math;

import java.util.HashSet;
import java.util.Set;

/**
 * Utility matematica per il calcolo della dinamicità.
 */
public final class DynamicityMath {

    private static final double EPSILON = 1.0E-9;

    private DynamicityMath() {
    }

    /**
     * Differenza relativa normalizzata tra due valori.
     *
     * @param previous valore precedente
     * @param current valore corrente
     * @return differenza relativa in [0, 1]
     */
    public static double relativeDifference(
            double previous,
            double current
    ) {
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
     * Limita un valore in [0, 1].
     *
     * @param value valore da normalizzare
     * @return valore limitato
     */
    public static double clamp01(double value) {
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
     * Restituisce l'unione di due insiemi di chiavi.
     *
     * @param first primo insieme
     * @param second secondo insieme
     * @return unione
     */
    public static Set<String> union(
            Set<String> first,
            Set<String> second
    ) {
        Set<String> result = new HashSet<>();

        if (first != null) {
            result.addAll(first);
        }

        if (second != null) {
            result.addAll(second);
        }

        return result;
    }
}
