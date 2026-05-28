package config.mobility;

/**
 * Configurazione dei parametri usati per stimare il tempo di copertura.
 *
 * La classe non calcola direttamente la copertura.
 * Fornisce solo i valori necessari a CoverageEstimator.
 */
public final class MobilityConfig {

    private final double epsilonSpeedMetersPerSecond;
    private final double v2vCommunicationRadiusMeters;

    private final double localCoverageTimeSeconds;
    private final double cloudCoverageTimeSeconds;
    private final double maxCoverageTimeSeconds;

    /**
     * Costruisce la configurazione di mobilità.
     *
     * @param epsilonSpeedMetersPerSecond velocità minima usata per evitare divisioni per zero
     * @param v2vCommunicationRadiusMeters raggio massimo del collegamento V2V
     * @param localCoverageTimeSeconds tempo convenzionale per esecuzione locale
     * @param cloudCoverageTimeSeconds tempo convenzionale per esecuzione cloud
     * @param maxCoverageTimeSeconds limite massimo per i tempi di copertura stimati
     */
    public MobilityConfig(
            double epsilonSpeedMetersPerSecond,
            double v2vCommunicationRadiusMeters,
            double localCoverageTimeSeconds,
            double cloudCoverageTimeSeconds,
            double maxCoverageTimeSeconds
    ) {
        this.epsilonSpeedMetersPerSecond = validatePositive(
                "epsilonSpeedMetersPerSecond",
                epsilonSpeedMetersPerSecond
        );

        this.v2vCommunicationRadiusMeters = validatePositive(
                "v2vCommunicationRadiusMeters",
                v2vCommunicationRadiusMeters
        );

        this.localCoverageTimeSeconds = validatePositive(
                "localCoverageTimeSeconds",
                localCoverageTimeSeconds
        );

        this.cloudCoverageTimeSeconds = validatePositive(
                "cloudCoverageTimeSeconds",
                cloudCoverageTimeSeconds
        );

        this.maxCoverageTimeSeconds = validatePositive(
                "maxCoverageTimeSeconds",
                maxCoverageTimeSeconds
        );

        if (localCoverageTimeSeconds > maxCoverageTimeSeconds) {
            throw new IllegalArgumentException(
                    "localCoverageTimeSeconds must be <= maxCoverageTimeSeconds."
            );
        }

        if (cloudCoverageTimeSeconds > maxCoverageTimeSeconds) {
            throw new IllegalArgumentException(
                    "cloudCoverageTimeSeconds must be <= maxCoverageTimeSeconds."
            );
        }
    }

    /**
     * Configurazione iniziale per il prototipo statico.
     *
     * I valori sono volutamente conservativi e possono essere raffinati
     * quando verranno introdotti dati da MOSAIC/SUMO.
     */
    public static MobilityConfig defaultConfig() {
        return new MobilityConfig(
                0.1,    // epsilonSpeedMetersPerSecond
                250.0,  // v2vCommunicationRadiusMeters
                300.0,  // localCoverageTimeSeconds
                300.0,  // cloudCoverageTimeSeconds
                300.0   // maxCoverageTimeSeconds
        );
    }

    public double getEpsilonSpeedMetersPerSecond() {
        return epsilonSpeedMetersPerSecond;
    }

    public double getV2vCommunicationRadiusMeters() {
        return v2vCommunicationRadiusMeters;
    }

    public double getLocalCoverageTimeSeconds() {
        return localCoverageTimeSeconds;
    }

    public double getCloudCoverageTimeSeconds() {
        return cloudCoverageTimeSeconds;
    }

    public double getMaxCoverageTimeSeconds() {
        return maxCoverageTimeSeconds;
    }

    /**
     * Limita un tempo di copertura al range usato dal modello.
     *
     * @param coverageTimeSeconds tempo di copertura stimato
     * @return tempo limitato tra 0 e maxCoverageTimeSeconds
     */
    public double clampCoverageTime(double coverageTimeSeconds) {
        if (!Double.isFinite(coverageTimeSeconds)) {
            return maxCoverageTimeSeconds;
        }

        if (coverageTimeSeconds < 0.0) {
            return 0.0;
        }

        return Math.min(coverageTimeSeconds, maxCoverageTimeSeconds);
    }

    private static double validatePositive(String fieldName, double value) {
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
        return "MobilityConfig{"
                + "epsilonSpeedMetersPerSecond=" + epsilonSpeedMetersPerSecond
                + ", v2vCommunicationRadiusMeters=" + v2vCommunicationRadiusMeters
                + ", localCoverageTimeSeconds=" + localCoverageTimeSeconds
                + ", cloudCoverageTimeSeconds=" + cloudCoverageTimeSeconds
                + ", maxCoverageTimeSeconds=" + maxCoverageTimeSeconds
                + '}';
    }
}