package model.snapshot;

/**
 * Associazione radio osservata tra un veicolo e un gateway.
 *
 * <p>{@code active} indica il collegamento attualmente usato dal veicolo.
 * {@code available} rappresenta la disponibilità dichiarata dalla sorgente.
 * La disponibilità geometrica viene verificata separatamente usando posizione
 * del veicolo e raggio del gateway.</p>
 */
public final class AccessLinkSnapshot {
    private final String accessLinkId;
    private final String vehicleId;
    private final String gatewayId;
    private final boolean active;
    private final boolean available;

    public AccessLinkSnapshot(
            String accessLinkId,
            String vehicleId,
            String gatewayId,
            boolean active,
            boolean available
    ) {
        this.accessLinkId = requireText(accessLinkId, "accessLinkId");
        this.vehicleId = requireText(vehicleId, "vehicleId");
        this.gatewayId = requireText(gatewayId, "gatewayId");
        this.active = active;
        this.available = available;
    }

    public String getAccessLinkId() { return accessLinkId; }
    public String getVehicleId() { return vehicleId; }
    public String getGatewayId() { return gatewayId; }
    public boolean isActive() { return active; }
    public boolean isAvailable() { return available; }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank.");
        }
        return value;
    }

    @Override
    public String toString() {
        return "AccessLinkSnapshot{" +
                "accessLinkId='" + accessLinkId + '\'' +
                ", vehicleId='" + vehicleId + '\'' +
                ", gatewayId='" + gatewayId + '\'' +
                ", active=" + active +
                ", available=" + available +
                '}';
    }
}
