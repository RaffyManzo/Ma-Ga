package model;

public class TaskInstance {

    private String taskId;
    private String sourceVehicleId;
    private double inputSizeBits;
    private double outputSizeBits;
    private double cpuCycles;
    private double deadlineSeconds;

    public TaskInstance() {
    }

    public TaskInstance(
            String taskId,
            String sourceVehicleId,
            double inputSizeBits,
            double outputSizeBits,
            double cpuCycles,
            double deadlineSeconds
    ) {
        this.taskId = taskId;
        this.sourceVehicleId = sourceVehicleId;
        this.inputSizeBits = inputSizeBits;
        this.outputSizeBits = outputSizeBits;
        this.cpuCycles = cpuCycles;
        this.deadlineSeconds = deadlineSeconds;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getSourceVehicleId() {
        return sourceVehicleId;
    }

    public void setSourceVehicleId(String sourceVehicleId) {
        this.sourceVehicleId = sourceVehicleId;
    }

    public double getInputSizeBits() {
        return inputSizeBits;
    }

    public void setInputSizeBits(double inputSizeBits) {
        this.inputSizeBits = inputSizeBits;
    }

    public double getOutputSizeBits() {
        return outputSizeBits;
    }

    public void setOutputSizeBits(double outputSizeBits) {
        this.outputSizeBits = outputSizeBits;
    }

    public double getCpuCycles() {
        return cpuCycles;
    }

    public void setCpuCycles(double cpuCycles) {
        this.cpuCycles = cpuCycles;
    }

    public double getDeadlineSeconds() {
        return deadlineSeconds;
    }

    public void setDeadlineSeconds(double deadlineSeconds) {
        this.deadlineSeconds = deadlineSeconds;
    }

    @Override
    public String toString() {
        return "TaskInstance{" +
                "taskId='" + taskId + '\'' +
                ", sourceVehicleId='" + sourceVehicleId + '\'' +
                ", inputSizeBits=" + inputSizeBits +
                ", outputSizeBits=" + outputSizeBits +
                ", cpuCycles=" + cpuCycles +
                ", deadlineSeconds=" + deadlineSeconds +
                '}';
    }
}