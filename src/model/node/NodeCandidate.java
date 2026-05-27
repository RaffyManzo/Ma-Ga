package model.node;

import java.util.Objects;

/**
 * Rappresenta una possibile opzione di esecuzione per un task generato
 * da uno specifico veicolo sorgente.
 *
 * Nel modello precedente il candidato era trattato come nodo globale.
 * In questa versione, invece, il candidato è source-aware:
 *
 * candidateId      = identificativo dell'opzione di esecuzione
 * sourceVehicleId  = veicolo per cui questo candidato è valido
 * executionNodeId  = nodo fisico che esegue il task
 *
 * Esempi:
 *
 * LOCAL:
 * candidateId     = local_vehicle_003
 * sourceVehicleId = vehicle_003
 * executionNodeId = vehicle_003
 *
 * V2V:
 * candidateId     = v2v_vehicle_001_to_vehicle_002
 * sourceVehicleId = vehicle_001
 * executionNodeId = vehicle_002
 *
 * EDGE:
 * candidateId     = edge_001_for_vehicle_003
 * sourceVehicleId = vehicle_003
 * executionNodeId = edge_001
 */
public final class NodeCandidate {

    private final String candidateId;
    private final String sourceVehicleId;
    private final String executionNodeId;
    private final NodeType type;

    private final double availableCpu;
    private final double availableBandwidth;
    private final double baseLatencySeconds;
    private final double coverageTimeSeconds;

    /**
     * Costruisce un candidato di esecuzione source-aware.
     *
     * Parametri in ingresso:
     * - candidateId: identificativo univoco del candidato;
     * - sourceVehicleId: veicolo sorgente per cui il candidato è valido;
     * - executionNodeId: nodo fisico che eseguirà il task;
     * - type: tipo del candidato, cioè LOCAL, VEHICLE, EDGE o CLOUD;
     * - availableCpu: CPU disponibile sul nodo di esecuzione;
     * - availableBandwidth: banda stimata sul link sorgente-destinazione;
     * - baseLatencySeconds: latenza base stimata;
     * - coverageTimeSeconds: tempo di copertura stimato.
     *
     * Output:
     * - nuova istanza immutabile di NodeCandidate.
     */
    public NodeCandidate(
            String candidateId,
            String sourceVehicleId,
            String executionNodeId,
            NodeType type,
            double availableCpu,
            double availableBandwidth,
            double baseLatencySeconds,
            double coverageTimeSeconds
    ) {
        this.candidateId = requireText(candidateId, "candidateId");
        this.sourceVehicleId = requireText(sourceVehicleId, "sourceVehicleId");
        this.executionNodeId = requireText(executionNodeId, "executionNodeId");
        this.type = Objects.requireNonNull(type, "type must not be null.");
        this.availableCpu = validateFiniteNonNegative("availableCpu", availableCpu);
        this.availableBandwidth = validateFiniteNonNegative("availableBandwidth", availableBandwidth);
        this.baseLatencySeconds = validateFiniteNonNegative("baseLatencySeconds", baseLatencySeconds);
        this.coverageTimeSeconds = validateFiniteNonNegative("coverageTimeSeconds", coverageTimeSeconds);

        validateLocalCandidate();
        validateVehicleCandidate();
    }

    /**
     * Restituisce l'identificativo del candidato.
     *
     * Output:
     * - candidateId.
     */
    public String getCandidateId() {
        return candidateId;
    }

    /**
     * Metodo di compatibilità temporanea.
     *
     * Output:
     * - candidateId.
     *
     * Nota:
     * nel nuovo modello è preferibile usare getCandidateId().
     */
    public String getNodeId() {
        return candidateId;
    }

    /**
     * Restituisce il veicolo sorgente per cui il candidato è valido.
     *
     * Output:
     * - sourceVehicleId.
     */
    public String getSourceVehicleId() {
        return sourceVehicleId;
    }

    /**
     * Restituisce il nodo fisico che esegue il task.
     *
     * Output:
     * - executionNodeId.
     */
    public String getExecutionNodeId() {
        return executionNodeId;
    }

    /**
     * Restituisce il tipo del candidato.
     *
     * Output:
     * - NodeType.
     */
    public NodeType getType() {
        return type;
    }

    /**
     * Restituisce la CPU disponibile sul nodo di esecuzione.
     *
     * Output:
     * - CPU disponibile in cycles/s.
     */
    public double getAvailableCpu() {
        return availableCpu;
    }

    /**
     * Restituisce la banda disponibile sul link sorgente-destinazione.
     *
     * Output:
     * - banda disponibile in bit/s.
     */
    public double getAvailableBandwidth() {
        return availableBandwidth;
    }

    /**
     * Restituisce la latenza base stimata.
     *
     * Output:
     * - latenza in secondi.
     */
    public double getBaseLatencySeconds() {
        return baseLatencySeconds;
    }

    /**
     * Restituisce il tempo di copertura stimato.
     *
     * Output:
     * - tempo di copertura in secondi.
     */
    public double getCoverageTimeSeconds() {
        return coverageTimeSeconds;
    }

    /**
     * Verifica se il candidato è locale.
     *
     * Output:
     * - true se type == LOCAL;
     * - false altrimenti.
     */
    public boolean isLocal() {
        return type == NodeType.LOCAL;
    }

    /**
     * Verifica se il candidato è remoto.
     *
     * Output:
     * - true se type != LOCAL;
     * - false altrimenti.
     */
    public boolean isRemote() {
        return type != NodeType.LOCAL;
    }

    /**
     * Verifica se il candidato è valido per un certo veicolo sorgente.
     *
     * Parametri in ingresso:
     * - vehicleId: veicolo sorgente da controllare.
     *
     * Output:
     * - true se il candidato è valido per quel veicolo;
     * - false altrimenti.
     */
    public boolean isValidForSourceVehicle(String vehicleId) {
        return sourceVehicleId.equals(vehicleId);
    }

    /**
     * Valida la regola dei candidati locali.
     *
     * Parametri in ingresso:
     * - nessuno.
     *
     * Output:
     * - nessun valore restituito;
     * - solleva eccezione se LOCAL non rispetta sourceVehicleId == executionNodeId.
     */
    private void validateLocalCandidate() {
        if (type == NodeType.LOCAL && !sourceVehicleId.equals(executionNodeId)) {
            throw new IllegalArgumentException(
                    "LOCAL candidate must have sourceVehicleId == executionNodeId."
            );
        }
    }

    /**
     * Valida la regola dei candidati V2V.
     *
     * Parametri in ingresso:
     * - nessuno.
     *
     * Output:
     * - nessun valore restituito;
     * - solleva eccezione se VEHICLE rappresenta lo stesso veicolo sorgente.
     */
    private void validateVehicleCandidate() {
        if (type == NodeType.VEHICLE && sourceVehicleId.equals(executionNodeId)) {
            throw new IllegalArgumentException(
                    "VEHICLE candidate should represent a remote vehicle, not the source vehicle itself."
            );
        }
    }

    /**
     * Verifica che una stringa sia valorizzata.
     *
     * Parametri in ingresso:
     * - value: stringa da validare;
     * - fieldName: nome del campo.
     *
     * Output:
     * - stringa validata.
     */
    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank.");
        }

        return value;
    }

    /**
     * Verifica che un valore numerico sia finito e non negativo.
     *
     * Parametri in ingresso:
     * - fieldName: nome del campo;
     * - value: valore da validare.
     *
     * Output:
     * - valore validato.
     */
    private static double validateFiniteNonNegative(String fieldName, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite.");
        }

        if (value < 0.0) {
            throw new IllegalArgumentException(fieldName + " must be >= 0.");
        }

        return value;
    }

    @Override
    public String toString() {
        return "NodeCandidate{" +
                "candidateId='" + candidateId + '\'' +
                ", sourceVehicleId='" + sourceVehicleId + '\'' +
                ", executionNodeId='" + executionNodeId + '\'' +
                ", type=" + type +
                ", availableCpu=" + availableCpu +
                ", availableBandwidth=" + availableBandwidth +
                ", baseLatencySeconds=" + baseLatencySeconds +
                ", coverageTimeSeconds=" + coverageTimeSeconds +
                '}';
    }
}

