package io.snapshot.dto;

/**
 * DTO grezzo di un task nello snapshot di input.
 */
public final class TaskInputDto {

    public String taskId;
    public String sourceVehicleId;
    public Double inputSizeBits;
    public Double outputSizeBits;
    public Double cpuCycles;
    public Double deadlineSeconds;
}
