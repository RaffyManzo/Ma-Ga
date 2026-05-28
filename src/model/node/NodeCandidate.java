package model.node;

import java.util.Objects;

/**
 * Rappresenta una possibile opzione di esecuzione per un task generato
 * da uno specifico veicolo sorgente.
 *
 * Il candidato è source-aware:
 * - sourceVehicleId indica il veicolo che può usare questo candidato;
 * - executionNodeId indica il nodo fisico che eseguirà il task.
 *
 * Il tempo di copertura non è memorizzato in questa classe.
 * Verrà calcolato da una classe dedicata usando veicoli, posizione del nodo,
 * raggio di copertura e, nel caso V2V, veicolo target.
 */
public final class NodeCandidate {

    private final String candidateId;
    private final String sourceVehicleId;
    private final String executionNodeId;
    private final NodeType type;

    private final double availableCpu;
    private final double availableBandwidth;
    private final double baseLatencySeconds;

    private final Double nodeX;
    private final Double nodeY;
    private final Double coverageRadiusMeters;

    /**
     * Costruisce un candidato di esecuzione source-aware.
     *
     * @param candidateId identificativo univoco del candidato
     * @param sourceVehicleId veicolo sorgente per cui il candidato è valido
     * @param executionNodeId nodo fisico che esegue il task
     * @param type tipo del candidato
     * @param availableCpu CPU disponibile sul nodo di esecuzione
     * @param availableBandwidth banda disponibile sul link sorgente-destinazione
     * @param baseLatencySeconds latenza base del collegamento
     * @param nodeX coordinata X del nodo infrastrutturale, se applicabile
     * @param nodeY coordinata Y del nodo infrastrutturale, se applicabile
     * @param coverageRadiusMeters raggio di copertura del nodo, se applicabile
     */
    public NodeCandidate(
            String candidateId,
            String sourceVehicleId,
            String executionNodeId,
            NodeType type,
            double availableCpu,
            double availableBandwidth,
            double baseLatencySeconds,
            Double nodeX,
            Double nodeY,
            Double coverageRadiusMeters
    ) {
        this.candidateId = requireText(candidateId, "candidateId");
        this.sourceVehicleId = requireText(sourceVehicleId, "sourceVehicleId");
        this.executionNodeId = requireText(executionNodeId, "executionNodeId");

        this.type = Objects.requireNonNull(
                type,
                "type must not be null."
        );

        this.availableCpu = validateFiniteNonNegative(
                "availableCpu",
                availableCpu
        );

        this.availableBandwidth = validateFiniteNonNegative(
                "availableBandwidth",
                availableBandwidth
        );

        this.baseLatencySeconds = validateFiniteNonNegative(
                "baseLatencySeconds",
                baseLatencySeconds
        );

        this.nodeX = validateOptionalFinite("nodeX", nodeX);
        this.nodeY = validateOptionalFinite("nodeY", nodeY);

        this.coverageRadiusMeters = validateOptionalPositive(
                "coverageRadiusMeters",
                coverageRadiusMeters
        );

        validateLocalCandidate();
        validateVehicleCandidate();
        validateInfrastructureCandidate();
    }

    public String getCandidateId() {
        return candidateId;
    }

    /**
     * Metodo di compatibilità con vecchie parti del codice.
     *
     * @return identificativo del candidato
     */
    public String getNodeId() {
        return candidateId;
    }

    public String getSourceVehicleId() {
        return sourceVehicleId;
    }

    public String getExecutionNodeId() {
        return executionNodeId;
    }

    public NodeType getType() {
        return type;
    }

    public double getAvailableCpu() {
        return availableCpu;
    }

    public double getAvailableBandwidth() {
        return availableBandwidth;
    }

    public double getBaseLatencySeconds() {
        return baseLatencySeconds;
    }

    /**
     * @return coordinata X del nodo, se presente
     */
    public Double getNodeX() {
        return nodeX;
    }

    /**
     * @return coordinata Y del nodo, se presente
     */
    public Double getNodeY() {
        return nodeY;
    }

    /**
     * @return raggio di copertura del nodo, se presente
     */
    public Double getCoverageRadiusMeters() {
        return coverageRadiusMeters;
    }

    public boolean isLocal() {
        return type == NodeType.LOCAL;
    }

    public boolean isVehicle() {
        return type == NodeType.VEHICLE;
    }

    public boolean isEdge() {
        return type == NodeType.EDGE;
    }

    public boolean isCloud() {
        return type == NodeType.CLOUD;
    }

    public boolean isRemote() {
        return type != NodeType.LOCAL;
    }

    /**
     * @return true se il candidato rappresenta un nodo con posizione e raggio fisici
     */
    public boolean isInfrastructureCandidate() {
        return type == NodeType.EDGE;
    }

    /**
     * @return true se posizione e raggio sono disponibili
     */
    public boolean hasCoverageGeometry() {
        return nodeX != null
                && nodeY != null
                && coverageRadiusMeters != null;
    }

    /**
     * Verifica se il candidato è utilizzabile dal veicolo sorgente indicato.
     *
     * @param vehicleId veicolo sorgente da controllare
     * @return true se il candidato è valido per quel veicolo
     */
    public boolean isValidForSourceVehicle(String vehicleId) {
        return sourceVehicleId.equals(vehicleId);
    }

    /**
     * Verifica la regola dei candidati locali.
     */
    private void validateLocalCandidate() {
        if (type == NodeType.LOCAL && !sourceVehicleId.equals(executionNodeId)) {
            throw new IllegalArgumentException(
                    "LOCAL candidate must have sourceVehicleId == executionNodeId."
            );
        }
    }

    /**
     * Verifica la regola dei candidati V2V.
     */
    private void validateVehicleCandidate() {
        if (type == NodeType.VEHICLE && sourceVehicleId.equals(executionNodeId)) {
            throw new IllegalArgumentException(
                    "VEHICLE candidate must have sourceVehicleId != executionNodeId."
            );
        }
    }

    /**
     * Verifica che i candidati infrastrutturali abbiano dati geometrici.
     */
    private void validateInfrastructureCandidate() {
        if (type == NodeType.EDGE && !hasCoverageGeometry()) {
            throw new IllegalArgumentException(
                    "EDGE candidate must define nodeX, nodeY and coverageRadiusMeters."
            );
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be null or blank."
            );
        }

        return value;
    }

    private static double validateFiniteNonNegative(
            String fieldName,
            double value
    ) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite.");
        }

        if (value < 0.0) {
            throw new IllegalArgumentException(fieldName + " must be >= 0.");
        }

        return value;
    }

    private static Double validateOptionalFinite(
            String fieldName,
            Double value
    ) {
        if (value == null) {
            return null;
        }

        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite.");
        }

        return value;
    }

    private static Double validateOptionalPositive(
            String fieldName,
            Double value
    ) {
        if (value == null) {
            return null;
        }

        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite.");
        }

        if (value <= 0.0) {
            throw new IllegalArgumentException(fieldName + " must be > 0.");
        }

        return value;
    }

    @Override
    public String toString() {
        return "NodeCandidate{"
                + "candidateId='" + candidateId + '\''
                + ", sourceVehicleId='" + sourceVehicleId + '\''
                + ", executionNodeId='" + executionNodeId + '\''
                + ", type=" + type
                + ", availableCpu=" + availableCpu
                + ", availableBandwidth=" + availableBandwidth
                + ", baseLatencySeconds=" + baseLatencySeconds
                + ", nodeX=" + nodeX
                + ", nodeY=" + nodeY
                + ", coverageRadiusMeters=" + coverageRadiusMeters
                + '}';
    }
}