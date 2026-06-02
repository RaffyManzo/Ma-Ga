package window.dynamicity.calculator;

import model.snapshot.SystemSnapshot;
import model.snapshot.VehicleSnapshot;
import window.dynamicity.compare.MetricMapComparator;
import window.dynamicity.math.DynamicityMath;
import window.dynamicity.metrics.VehicleMetrics;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Calcola Dv(k), cioè la variazione dell'insieme dei veicoli osservati.
 *
 * <p>La formalizzazione associa Dv(k) esclusivamente al churn dei veicoli:
 * ingresso e uscita dallo scenario. Posizione, velocità e CPU locale non
 * devono essere assorbite da questa componente:</p>
 *
 * <pre>
 * Dv(k) = |V(k) symmetricDifference V(k-1)| / (|V(k) union V(k-1)| + epsilon)
 * </pre>
 *
 * <p>La qualità geometrica dei collegamenti appartiene a Dl(k). La CPU locale
 * continua a essere osservata da Dr(k).</p>
 */
public final class VehicleDynamicityCalculator {
    private static final double EPSILON = 1.0E-9;

    /** Costruisce il calculator aderente alla formalizzazione. */
    public VehicleDynamicityCalculator() { }

    /**
     * Overload storico mantenuto per compatibilità sorgente.
     *
     * <p>Il comparator non viene più usato: il confronto di metriche interne
     * del veicolo non appartiene alla definizione formale di Dv(k).</p>
     *
     * @param ignoredComparator comparator storico, richiesto soltanto per
     *                          mantenere compatibilità con chiamanti esistenti
     */
    @Deprecated
    public VehicleDynamicityCalculator(
            MetricMapComparator<VehicleMetrics> ignoredComparator
    ) {
        Objects.requireNonNull(
                ignoredComparator,
                "ignoredComparator must not be null."
        );
    }

    /**
     * Calcola la variazione normalizzata dell'insieme dei veicoli.
     *
     * @param previousSnapshot snapshot precedente
     * @param currentSnapshot snapshot corrente
     * @return Dv(k) in [0, 1]
     */
    public double compute(
            SystemSnapshot previousSnapshot,
            SystemSnapshot currentSnapshot
    ) {
        Objects.requireNonNull(
                previousSnapshot,
                "previousSnapshot must not be null."
        );
        Objects.requireNonNull(
                currentSnapshot,
                "currentSnapshot must not be null."
        );

        Set<String> previousIds = vehicleIds(previousSnapshot);
        Set<String> currentIds = vehicleIds(currentSnapshot);
        Set<String> union = DynamicityMath.union(previousIds, currentIds);

        if (union.isEmpty()) {
            return 0.0;
        }

        Set<String> symmetricDifference = new HashSet<>(previousIds);
        symmetricDifference.removeAll(currentIds);

        Set<String> enteredVehicles = new HashSet<>(currentIds);
        enteredVehicles.removeAll(previousIds);
        symmetricDifference.addAll(enteredVehicles);

        return DynamicityMath.clamp01(
                symmetricDifference.size() / (union.size() + EPSILON)
        );
    }

    private Set<String> vehicleIds(SystemSnapshot snapshot) {
        Set<String> result = new HashSet<>();
        for (VehicleSnapshot vehicle : snapshot.getVehicles()) {
            result.add(vehicle.getVehicleId());
        }
        return result;
    }
}
