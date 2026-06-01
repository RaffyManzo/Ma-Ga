package model.genetic;

/**
 * Rappresenta la decisione di offloading per un singolo task.
 *
 * <p>Formalmente:</p>
 *
 * <pre>
 * g_i = (n_i, p_i, f_i, b_i)
 * </pre>
 *
 * <ul>
 *     <li>{@code n_i}: candidato di esecuzione scelto;</li>
 *     <li>{@code p_i}: quota di offloading;</li>
 *     <li>{@code f_i}: CPU assegnata;</li>
 *     <li>{@code b_i}: banda assegnata.</li>
 * </ul>
 *
 * <p>Nel modello source-aware {@code n_i} è un {@code candidateId}, non un nodo
 * globale. Il candidato deve essere valido per il veicolo che ha generato il
 * task.</p>
 */
public final class Gene {

    private final String taskId;
    private final String selectedCandidateId;
    private final double offloadingRatio;
    private final double allocatedCpu;
    private final double allocatedBandwidth;

    /**
     * Costruisce un gene.
     *
     * @param taskId task a cui il gene si riferisce
     * @param selectedCandidateId candidato di esecuzione scelto
     * @param offloadingRatio quota remota {@code p_i}
     * @param allocatedCpu CPU assegnata {@code f_i}
     * @param allocatedBandwidth banda assegnata {@code b_i}
     */
    public Gene(
            String taskId,
            String selectedCandidateId,
            double offloadingRatio,
            double allocatedCpu,
            double allocatedBandwidth
    ) {
        this.taskId = requireText(taskId, "taskId");
        this.selectedCandidateId = requireText(selectedCandidateId, "selectedCandidateId");
        this.offloadingRatio = validateFinite("offloadingRatio", offloadingRatio);
        this.allocatedCpu = validateFinite("allocatedCpu", allocatedCpu);
        this.allocatedBandwidth = validateFinite("allocatedBandwidth", allocatedBandwidth);
    }

    /**
     * Restituisce il task associato al gene.
     */
    public String getTaskId() {
        return taskId;
    }

    /**
     * Restituisce il candidato di esecuzione scelto.
     */
    public String getSelectedCandidateId() {
        return selectedCandidateId;
    }

    /**
     * Metodo di compatibilità temporanea.
     *
     * <p>Nel modello source-aware è preferibile usare
     * {@link #getSelectedCandidateId()}.</p>
     */
    public String getSelectedNodeId() {
        return selectedCandidateId;
    }

    /**
     * Restituisce la quota di offloading.
     */
    public double getOffloadingRatio() {
        return offloadingRatio;
    }

    /**
     * Restituisce la CPU assegnata.
     */
    public double getAllocatedCpu() {
        return allocatedCpu;
    }

    /**
     * Restituisce la banda assegnata.
     */
    public double getAllocatedBandwidth() {
        return allocatedBandwidth;
    }

    /**
     * Verifica che una stringa sia valorizzata.
     */
    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank.");
        }

        return value;
    }

    /**
     * Verifica che un double sia finito.
     */
    private static double validateFinite(String fieldName, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite.");
        }

        return value;
    }

    @Override
    public String toString() {
        return "Gene{" +
                "taskId='" + taskId + '\'' +
                ", selectedCandidateId='" + selectedCandidateId + '\'' +
                ", offloadingRatio=" + offloadingRatio +
                ", allocatedCpu=" + allocatedCpu +
                ", allocatedBandwidth=" + allocatedBandwidth +
                '}';
    }
}

