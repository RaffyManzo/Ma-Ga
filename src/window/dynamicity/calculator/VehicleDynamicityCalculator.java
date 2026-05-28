package window.dynamicity.calculator;

import model.snapshot.SystemSnapshot;
import model.snapshot.VehicleSnapshot;
import window.dynamicity.compare.MetricMapComparator;
import window.dynamicity.metrics.VehicleMetrics;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Calcola Dv(k), cioè la variazione dei veicoli tra due snapshot.
 *
 * Il confronto non usa solo la presenza degli ID.
 * Per veicoli presenti in entrambi gli snapshot considera anche:
 *
 * - posizione;
 * - velocità;
 * - CPU locale.
 */
public final class VehicleDynamicityCalculator {

    private final MetricMapComparator<VehicleMetrics> comparator;

    /**
     * Costruisce il calculator con comparator standard.
     */
    public VehicleDynamicityCalculator() {
        this(new MetricMapComparator<>());
    }

    /**
     * Costruisce il calculator con comparator esplicito.
     *
     * @param comparator comparator tra mappe di metriche
     */
    public VehicleDynamicityCalculator(
            MetricMapComparator<VehicleMetrics> comparator
    ) {
        this.comparator = Objects.requireNonNull(
                comparator,
                "comparator must not be null."
        );
    }

    /**
     * Calcola la variazione normalizzata dei veicoli.
     *
     * @param previousSnapshot snapshot precedente
     * @param currentSnapshot snapshot corrente
     * @return Dv(k) in [0, 1]
     */
    public double compute(
            SystemSnapshot previousSnapshot,
            SystemSnapshot currentSnapshot
    ) {
        return comparator.computeVariation(
                buildVehicleMap(previousSnapshot),
                buildVehicleMap(currentSnapshot)
        );
    }

    /**
     * Costruisce la mappa vehicleId -> VehicleMetrics.
     */
    private Map<String, VehicleMetrics> buildVehicleMap(
            SystemSnapshot snapshot
    ) {
        Map<String, VehicleMetrics> result = new HashMap<>();

        for (VehicleSnapshot vehicle : snapshot.getVehicles()) {
            result.put(
                    vehicle.getVehicleId(),
                    new VehicleMetrics(
                            vehicle.getX(),
                            vehicle.getY(),
                            vehicle.getSpeed(),
                            vehicle.getLocalCpu()
                    )
            );
        }

        return result;
    }
}
