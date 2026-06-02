package model.mobility;

import model.snapshot.AccessGatewaySnapshot;
import model.snapshot.AccessLinkSnapshot;
import model.snapshot.SystemSnapshot;
import model.snapshot.VehicleSnapshot;

import java.util.Objects;

/**
 * Risolve il collegamento radio attivo di un veicolo nello snapshot corrente.
 */
public final class AccessLinkResolver {

    public AccessLinkSnapshot requireActiveAccessLink(
            SystemSnapshot snapshot,
            String vehicleId
    ) {
        Objects.requireNonNull(snapshot, "snapshot must not be null.");
        requireText(vehicleId, "vehicleId");
        AccessLinkSnapshot found = null;
        for (AccessLinkSnapshot link : snapshot.getAccessLinks()) {
            if (!vehicleId.equals(link.getVehicleId()) || !link.isActive()) {
                continue;
            }
            if (found != null) {
                throw new IllegalArgumentException(
                        "Vehicle " + vehicleId + " has more than one active access link."
                );
            }
            found = link;
        }
        if (found == null) {
            throw new IllegalArgumentException(
                    "Vehicle " + vehicleId + " has no active access link."
            );
        }
        return found;
    }

    public AccessGatewaySnapshot requireGateway(
            SystemSnapshot snapshot,
            String gatewayId
    ) {
        Objects.requireNonNull(snapshot, "snapshot must not be null.");
        requireText(gatewayId, "gatewayId");
        for (AccessGatewaySnapshot gateway : snapshot.getAccessGateways()) {
            if (gatewayId.equals(gateway.getGatewayId())) {
                return gateway;
            }
        }
        throw new IllegalArgumentException("Gateway not found in snapshot: " + gatewayId);
    }

    public VehicleSnapshot requireVehicle(SystemSnapshot snapshot, String vehicleId) {
        Objects.requireNonNull(snapshot, "snapshot must not be null.");
        requireText(vehicleId, "vehicleId");
        for (VehicleSnapshot vehicle : snapshot.getVehicles()) {
            if (vehicleId.equals(vehicle.getVehicleId())) {
                return vehicle;
            }
        }
        throw new IllegalArgumentException("Vehicle not found in snapshot: " + vehicleId);
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank.");
        }
    }
}
