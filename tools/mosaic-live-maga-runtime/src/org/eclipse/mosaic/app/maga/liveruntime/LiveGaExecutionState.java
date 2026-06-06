package org.eclipse.mosaic.app.maga.liveruntime;

public enum LiveGaExecutionState {
    IDLE,
    GA_RUNNING,
    RESULT_READY_WITHIN_BOUND,
    RESULT_APPLIED,
    WAIT_CAP_REACHED,
    STALE_RESULT_DISCARDED,
    FRESH_REOPTIMIZATION_REQUESTED
}
