package model.mobility;

import model.snapshot.AccessGatewaySnapshot;
import model.snapshot.AccessLinkSnapshot;
import model.snapshot.SystemSnapshot;
import model.snapshot.VehicleSnapshot;

import java.util.Objects;
import java.util.Optional;

/**
 * Risolve il collegamento radio attivo di un veicolo nello snapshot corrente.
 */
public final class AccessLinkResolver {

    /**
     * Cerca l'access link attivo del veicolo senza imporne la presenza.
     *
     * <p>Zero link attivi rappresentano un veicolo presente ma senza gateway
     * infrastrutturale nella finestra corrente. Piu' di un link attivo resta un
     * errore strutturale dello snapshot.</p>
     *
     * @param snapshot snapshot da interrogare
     * @param vehicleId veicolo sorgente
     * @return access link attivo, se presente
     * @throws IllegalArgumentException se il veicolo ha piu' di un link attivo
     */
    public Optional<AccessLinkSnapshot> findActiveAccessLink(
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
        return Optional.ofNullable(found);
    }

    /**
     * Restituisce l'unico access link attivo del veicolo.
     *
     * <p>Questo metodo mantiene la semantica strict usata dai componenti che
     * richiedono un gateway infrastrutturale, ad esempio EDGE e CLOUD.</p>
     *
     * @param snapshot snapshot da interrogare
     * @param vehicleId veicolo sorgente
     * @return unico access link attivo
     * @throws IllegalArgumentException se il link manca o non e' univoco
     */
    public AccessLinkSnapshot requireActiveAccessLink(
            SystemSnapshot snapshot,
            String vehicleId
    ) {
        return findActiveAccessLink(snapshot, vehicleId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Vehicle " + vehicleId + " has no active access link."
                ));
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
