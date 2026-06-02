package ga.fitness.breakdown;

import model.bandwidth.BandwidthPoolType;

/** Uso aggregato della banda su un pool radio condiviso. */
public final class BandwidthPoolUsageBreakdown {
    private static final double EPSILON = 1.0E-9;

    private final String poolId;
    private final BandwidthPoolType poolType;
    private final double availableBandwidth;
    private double usedBandwidth;

    public BandwidthPoolUsageBreakdown(
            String poolId,
            BandwidthPoolType poolType,
            double availableBandwidth
    ) {
        this.poolId = poolId;
        this.poolType = poolType;
        this.availableBandwidth = availableBandwidth;
    }

    public void addBandwidth(double value) {
        this.usedBandwidth += Math.max(0.0, value);
    }

    public String getPoolId() { return poolId; }
    public BandwidthPoolType getPoolType() { return poolType; }
    public double getAvailableBandwidth() { return availableBandwidth; }
    public double getUsedBandwidth() { return usedBandwidth; }

    public double getBandwidthUsagePercent() {
        if (availableBandwidth <= EPSILON) {
            return 0.0;
        }
        return (usedBandwidth / availableBandwidth) * 100.0;
    }

    public double getBandwidthOverflowRatio() {
        if (availableBandwidth <= EPSILON) {
            return usedBandwidth > 0.0 ? 1.0 : 0.0;
        }
        return Math.max(
                0.0,
                (usedBandwidth - availableBandwidth) / availableBandwidth
        );
    }

    public boolean hasBandwidthViolation() {
        return usedBandwidth > availableBandwidth;
    }

    public boolean isBandwidthSaturated(double thresholdPercent) {
        return !hasBandwidthViolation()
                && getBandwidthUsagePercent() >= thresholdPercent;
    }
}
