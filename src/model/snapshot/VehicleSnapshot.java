package model.snapshot;

/**
 * Stato sintetico di un veicolo nello snapshot.
 *
 * <p>La posizione e la velocità alimentano le stime di copertura, mentre
 * {@code localCpu} rappresenta la capacità disponibile per esecuzione locale.</p>
 */
public class VehicleSnapshot {

    private String vehicleId;
    private double x;
    private double y;
    private double speed;
    private double localCpu;

    public VehicleSnapshot() {
    }

    /**
     * Crea lo stato di un veicolo.
     *
     * @param vehicleId identificativo del veicolo
     * @param x coordinata X
     * @param y coordinata Y
     * @param speed velocità scalare
     * @param localCpu CPU locale disponibile
     */
    public VehicleSnapshot(
            String vehicleId,
            double x,
            double y,
            double speed,
            double localCpu
    ) {
        this.vehicleId = vehicleId;
        this.x = x;
        this.y = y;
        this.speed = speed;
        this.localCpu = localCpu;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(String vehicleId) {
        this.vehicleId = vehicleId;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public double getLocalCpu() {
        return localCpu;
    }

    public void setLocalCpu(double localCpu) {
        this.localCpu = localCpu;
    }

    @Override
    public String toString() {
        return "VehicleSnapshot{" +
                "vehicleId='" + vehicleId + '\'' +
                ", x=" + x +
                ", y=" + y +
                ", speed=" + speed +
                ", localCpu=" + localCpu +
                '}';
    }
}

