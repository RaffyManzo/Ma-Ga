package ga.constraints;

/**
 * Risultato della valutazione temporale di un gene rispetto alla deadline.
 *
 * <p>La valutazione separa due condizioni:</p>
 *
 * <ul>
 *   <li>rispetto della deadline del task;</li>
 *   <li>sostenibilità mobility-aware della scelta remota.</li>
 * </ul>
 *
 * <p>Una scelta è ammissibile per il repair ordinario soltanto quando entrambe
 * le condizioni sono rispettate. Il valore di lateness viene usato nella sola
 * modalità degradata best-effort per confrontare alternative tardive.</p>
 */
public final class DeadlineEvaluation {
    private final double completionTimeSeconds;
    private final double deadlineSeconds;
    private final double latenessSeconds;
    private final boolean deadlineRespected;
    private final boolean mobilitySustainable;
    private final boolean valid;

    private DeadlineEvaluation(
            double completionTimeSeconds,
            double deadlineSeconds,
            double latenessSeconds,
            boolean deadlineRespected,
            boolean mobilitySustainable,
            boolean valid
    ) {
        this.completionTimeSeconds = completionTimeSeconds;
        this.deadlineSeconds = deadlineSeconds;
        this.latenessSeconds = latenessSeconds;
        this.deadlineRespected = deadlineRespected;
        this.mobilitySustainable = mobilitySustainable;
        this.valid = valid;
    }

    public static DeadlineEvaluation valid(
            double completionTimeSeconds,
            double deadlineSeconds,
            boolean deadlineRespected,
            boolean mobilitySustainable
    ) {
        double lateness = deadlineSeconds <= 0.0
                ? 0.0
                : Math.max(0.0, completionTimeSeconds - deadlineSeconds);
        return new DeadlineEvaluation(
                completionTimeSeconds,
                deadlineSeconds,
                lateness,
                deadlineRespected,
                mobilitySustainable,
                true
        );
    }

    public static DeadlineEvaluation invalid(double deadlineSeconds) {
        return new DeadlineEvaluation(
                Double.POSITIVE_INFINITY,
                deadlineSeconds,
                Double.POSITIVE_INFINITY,
                false,
                false,
                false
        );
    }

    public double getCompletionTimeSeconds() {
        return completionTimeSeconds;
    }

    public double getDeadlineSeconds() {
        return deadlineSeconds;
    }

    public double getLatenessSeconds() {
        return latenessSeconds;
    }

    public boolean isDeadlineRespected() {
        return deadlineRespected;
    }

    public boolean isMobilitySustainable() {
        return mobilitySustainable;
    }

    public boolean isValid() {
        return valid;
    }

    /**
     * Restituisce true soltanto per una scelta utilizzabile dal repair ordinario.
     */
    public boolean isAdmissible() {
        return valid && deadlineRespected && mobilitySustainable;
    }
}
