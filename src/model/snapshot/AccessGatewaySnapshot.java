package model.snapshot;

/**
 * Descrive un punto di accesso radio osservato nello scenario.
 *
 * <p>Il gateway non coincide necessariamente con un nodo computazionale.
 * Una RSU può offrire accesso sia a un EDGE sia al CLOUD, ma il suo ruolo
 * radio resta distinto dalla destinazione di esecuzione scelta dal GA.</p>
 */
public final class AccessGatewaySnapshot {
    private final String gatewayId;
    private final String gatewayType;
    private final double x;
    private final double y;
    private final double coverageRadiusMeters;

    public AccessGatewaySnapshot(
            String gatewayId,
            String gatewayType,
            double x,
            double y,
            double coverageRadiusMeters
    ) {
        this.gatewayId = requireText(gatewayId, "gatewayId");
        this.gatewayType = requireText(gatewayType, "gatewayType");
        this.x = requireFinite(x, "x");
        this.y = requireFinite(y, "y");
        this.coverageRadiusMeters = requirePositive(
                coverageRadiusMeters,
                "coverageRadiusMeters"
        );
    }

    public String getGatewayId() { return gatewayId; }
    public String getGatewayType() { return gatewayType; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getCoverageRadiusMeters() { return coverageRadiusMeters; }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank.");
        }
        return value;
    }

    private static double requireFinite(double value, String fieldName) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite.");
        }
        return value;
    }

    private static double requirePositive(double value, String fieldName) {
        requireFinite(value, fieldName);
        if (value <= 0.0) {
            throw new IllegalArgumentException(fieldName + " must be > 0.");
        }
        return value;
    }

    @Override
    public String toString() {
        return "AccessGatewaySnapshot{" +
                "gatewayId='" + gatewayId + '\'' +
                ", gatewayType='" + gatewayType + '\'' +
                ", x=" + x +
                ", y=" + y +
                ", coverageRadiusMeters=" + coverageRadiusMeters +
                '}';
    }
}
