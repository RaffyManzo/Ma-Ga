package org.eclipse.mosaic.app.maga.livestate;

final class LiveTaskState {

    private final String taskId;
    private final String profileId;
    private final String sourceVehicleId;
    private final long activationTimeNs;
    private final long inputSizeBits;
    private final long outputSizeBits;
    private final long cpuCycles;
    private final double deadlineSeconds;
    private final LiveTaskStatus status;

    LiveTaskState(
            String taskId,
            String profileId,
            String sourceVehicleId,
            long activationTimeNs,
            long inputSizeBits,
            long outputSizeBits,
            long cpuCycles,
            double deadlineSeconds,
            LiveTaskStatus status
    ) {
        this.taskId = taskId;
        this.profileId = profileId;
        this.sourceVehicleId = sourceVehicleId;
        this.activationTimeNs = activationTimeNs;
        this.inputSizeBits = inputSizeBits;
        this.outputSizeBits = outputSizeBits;
        this.cpuCycles = cpuCycles;
        this.deadlineSeconds = deadlineSeconds;
        this.status = status;
    }

    String getTaskId() {
        return taskId;
    }

    String getProfileId() {
        return profileId;
    }

    String getSourceVehicleId() {
        return sourceVehicleId;
    }

    long getActivationTimeNs() {
        return activationTimeNs;
    }

    long getInputSizeBits() {
        return inputSizeBits;
    }

    long getOutputSizeBits() {
        return outputSizeBits;
    }

    long getCpuCycles() {
        return cpuCycles;
    }

    double getDeadlineSeconds() {
        return deadlineSeconds;
    }

    LiveTaskStatus getStatus() {
        return status;
    }

    LiveTaskState withStatus(LiveTaskStatus nextStatus) {
        return new LiveTaskState(
                taskId,
                profileId,
                sourceVehicleId,
                activationTimeNs,
                inputSizeBits,
                outputSizeBits,
                cpuCycles,
                deadlineSeconds,
                nextStatus
        );
    }
}
