package model.snapshot;

import model.bandwidth.BandwidthPoolType;

import java.util.Objects;

/**
 * Capacità di banda condivisa osservata nello snapshot.
 *
 * <p>Nel livello 18.2 il prototipo usa un solo pool GLOBAL, equivalente a
 * Bmax nella formalizzazione. La stessa struttura viene riutilizzata dal
 * livello 18.3 per pool distinti per gateway e V2V.</p>
 */
public final class BandwidthPoolSnapshot {
    private final String poolId;
    private final BandwidthPoolType poolType;
    private final double availableBandwidth;

    public BandwidthPoolSnapshot(
            String poolId,
            BandwidthPoolType poolType,
            double availableBandwidth
    ) {
        this.poolId = requireText(poolId, "poolId");
        this.poolType = Objects.requireNonNull(poolType, "poolType must not be null.");
        this.availableBandwidth = requirePositive(availableBandwidth, "availableBandwidth");
    }

    public String getPoolId() { return poolId; }
    public BandwidthPoolType getPoolType() { return poolType; }
    public double getAvailableBandwidth() { return availableBandwidth; }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be null or blank.");
        }
        return value;
    }

    private static double requirePositive(double value, String field) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(field + " must be finite and > 0.");
        }
        return value;
    }

    @Override
    public String toString() {
        return "BandwidthPoolSnapshot{" +
                "poolId='" + poolId + '\'' +
                ", poolType=" + poolType +
                ", availableBandwidth=" + availableBandwidth +
                '}';
    }
}
