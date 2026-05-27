package config;

/**
 * Configura i valori di riferimento usati per normalizzare i termini della fitness.
 *
 * La formalizzazione evidenzia che T(C), L(C), Pmob(C) e Pres(C) possono
 * avere scale numeriche diverse. Per evitare che un termine domini gli altri
 * solo per ordine di grandezza, il FitnessEvaluator potrà usare:
 *
 * T_hat(C)    = T(C) / T_ref
 * L_hat(C)    = L(C) / L_ref
 * Pmob_hat(C) = Pmob(C) / Pmob_ref
 * Pres_hat(C) = Pres(C) / Pres_ref
 *
 * Questa classe NON normalizza direttamente i valori.
 * Espone solo i riferimenti numerici.
 */
public final class NormalizationConfig {

    private final double completionTimeReferenceSeconds;
    private final double communicationLatencyReferenceSeconds;
    private final double mobilityPenaltyReference;
    private final double resourcePenaltyReference;

    public NormalizationConfig(
            double completionTimeReferenceSeconds,
            double communicationLatencyReferenceSeconds,
            double mobilityPenaltyReference,
            double resourcePenaltyReference
    ) {
        validateStrictlyPositive("completionTimeReferenceSeconds", completionTimeReferenceSeconds);
        validateStrictlyPositive("communicationLatencyReferenceSeconds", communicationLatencyReferenceSeconds);
        validateStrictlyPositive("mobilityPenaltyReference", mobilityPenaltyReference);
        validateStrictlyPositive("resourcePenaltyReference", resourcePenaltyReference);

        this.completionTimeReferenceSeconds = completionTimeReferenceSeconds;
        this.communicationLatencyReferenceSeconds = communicationLatencyReferenceSeconds;
        this.mobilityPenaltyReference = mobilityPenaltyReference;
        this.resourcePenaltyReference = resourcePenaltyReference;
    }

    /**
     * Configurazione neutra.
     *
     * Con tutti i riferimenti pari a 1.0, il FitnessEvaluator può già funzionare
     * senza alterare i valori originali. In seguito questi valori andranno tarati
     * usando simulazioni o soglie operative realistiche.
     */
    public static NormalizationConfig neutral() {
        return new NormalizationConfig(
                1.0,
                1.0,
                1.0,
                1.0
        );
    }

    public double getCompletionTimeReferenceSeconds() {
        return completionTimeReferenceSeconds;
    }

    public double getCommunicationLatencyReferenceSeconds() {
        return communicationLatencyReferenceSeconds;
    }

    public double getMobilityPenaltyReference() {
        return mobilityPenaltyReference;
    }

    public double getResourcePenaltyReference() {
        return resourcePenaltyReference;
    }

    public double getTRef() {
        return completionTimeReferenceSeconds;
    }

    public double getLRef() {
        return communicationLatencyReferenceSeconds;
    }

    public double getPmobRef() {
        return mobilityPenaltyReference;
    }

    public double getPresRef() {
        return resourcePenaltyReference;
    }

    private static void validateStrictlyPositive(String fieldName, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite.");
        }

        if (value <= 0.0) {
            throw new IllegalArgumentException(fieldName + " must be > 0.");
        }
    }

    @Override
    public String toString() {
        return "NormalizationConfig{" +
                "T_ref=" + completionTimeReferenceSeconds +
                ", L_ref=" + communicationLatencyReferenceSeconds +
                ", Pmob_ref=" + mobilityPenaltyReference +
                ", Pres_ref=" + resourcePenaltyReference +
                '}';
    }
}