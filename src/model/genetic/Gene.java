package model.genetic;

/**
 * Rappresenta la decisione di offloading per un singolo task.
 *
 * Formalmente:
 *
 * g_i = (n_i, p_i, f_i, b_i)
 *
 * dove:
 * - n_i è il candidato di esecuzione scelto;
 * - p_i è la quota di offloading;
 * - f_i è la CPU assegnata;
 * - b_i è la banda assegnata.
 *
 * Nel nuovo modello n_i non è più un nodo globale, ma un candidateId
 * source-aware valido per il veicolo che ha generato il task.
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
     * Parametri in ingresso:
     * - taskId: task a cui il gene si riferisce;
     * - selectedCandidateId: candidato di esecuzione scelto;
     * - offloadingRatio: quota di offloading p_i;
     * - allocatedCpu: CPU assegnata f_i;
     * - allocatedBandwidth: banda assegnata b_i.
     *
     * Output:
     * - nuova istanza immutabile di Gene.
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
     *
     * Output:
     * - taskId.
     */
    public String getTaskId() {
        return taskId;
    }

    /**
     * Restituisce il candidato di esecuzione scelto.
     *
     * Output:
     * - selectedCandidateId.
     */
    public String getSelectedCandidateId() {
        return selectedCandidateId;
    }

    /**
     * Metodo di compatibilità temporanea.
     *
     * Output:
     * - selectedCandidateId.
     *
     * Nota:
     * nel nuovo modello è preferibile usare getSelectedCandidateId().
     */
    public String getSelectedNodeId() {
        return selectedCandidateId;
    }

    /**
     * Restituisce la quota di offloading.
     *
     * Output:
     * - p_i.
     */
    public double getOffloadingRatio() {
        return offloadingRatio;
    }

    /**
     * Restituisce la CPU assegnata.
     *
     * Output:
     * - f_i.
     */
    public double getAllocatedCpu() {
        return allocatedCpu;
    }

    /**
     * Restituisce la banda assegnata.
     *
     * Output:
     * - b_i.
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

