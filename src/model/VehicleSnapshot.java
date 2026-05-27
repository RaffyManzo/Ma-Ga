package model;

public class VehicleSnapshot {

    private String vehicleId;
    private double x;
    private double y;
    private double speed;
    private double localCpu;

    public VehicleSnapshot() {
    }

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