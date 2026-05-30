package config;

import config.fitness.FitnessWeights;
import config.fitness.NormalizationConfig;
import config.fitness.PenaltyConfig;
import config.ga.GaParameterScaler;
import config.ga.GaParameterScalingMode;
import config.ga.GaParameterScalingResult;
import config.ga.GeneticAlgorithmConfig;
import config.mobility.MobilityConfig;
import model.snapshot.SystemSnapshot;

import java.util.Objects;

/**
 * Configurazione complessiva del Mobility-Aware Genetic Algorithm.
 *
 * <p>Aggrega pesi della fitness, penalità, normalizzazione, configurazione GA,
 * parametri di mobilità e modalità di scaling. I main normalmente scelgono solo
 * la modalità di scaling; la configurazione GA effettiva viene risolta quando
 * è disponibile lo snapshot.</p>
 */
public final class MaGaConfig {

    private final FitnessWeights fitnessWeights;
    private final PenaltyConfig penaltyConfig;
    private final NormalizationConfig normalizationConfig;
    private final GeneticAlgorithmConfig geneticAlgorithmConfig;
    private final MobilityConfig mobilityConfig;

    private final GaParameterScalingMode gaParameterScalingMode;
    private final GaParameterScaler gaParameterScaler;

    /**
     * Overload storico in modalità {@link GaParameterScalingMode#STATIC}.
     */
    public MaGaConfig(
            FitnessWeights fitnessWeights,
            PenaltyConfig penaltyConfig,
            NormalizationConfig normalizationConfig,
            GeneticAlgorithmConfig geneticAlgorithmConfig
    ) {
        this(
                fitnessWeights,
                penaltyConfig,
                normalizationConfig,
                geneticAlgorithmConfig,
                MobilityConfig.defaultConfig(),
                GaParameterScalingMode.STATIC
        );
    }

    /**
     * Overload con configurazione di mobilità esplicita in modalità
     * {@link GaParameterScalingMode#STATIC}.
     */
    public MaGaConfig(
            FitnessWeights fitnessWeights,
            PenaltyConfig penaltyConfig,
            NormalizationConfig normalizationConfig,
            GeneticAlgorithmConfig geneticAlgorithmConfig,
            MobilityConfig mobilityConfig
    ) {
        this(
                fitnessWeights,
                penaltyConfig,
                normalizationConfig,
                geneticAlgorithmConfig,
                mobilityConfig,
                GaParameterScalingMode.STATIC
        );
    }

    /**
     * Costruttore principale.
     *
     * @param fitnessWeights pesi della funzione obiettivo
     * @param penaltyConfig configurazione delle penalità
     * @param normalizationConfig riferimenti di normalizzazione
     * @param geneticAlgorithmConfig configurazione GA di base
     * @param mobilityConfig configurazione di mobilità/copertura
     * @param gaParameterScalingMode modalità STATIC o ADAPTIVE
     */
    public MaGaConfig(
            FitnessWeights fitnessWeights,
            PenaltyConfig penaltyConfig,
            NormalizationConfig normalizationConfig,
            GeneticAlgorithmConfig geneticAlgorithmConfig,
            MobilityConfig mobilityConfig,
            GaParameterScalingMode gaParameterScalingMode
    ) {
        this.fitnessWeights = Objects.requireNonNull(
                fitnessWeights,
                "fitnessWeights must not be null."
        );

        this.penaltyConfig = Objects.requireNonNull(
                penaltyConfig,
                "penaltyConfig must not be null."
        );

        this.normalizationConfig = Objects.requireNonNull(
                normalizationConfig,
                "normalizationConfig must not be null."
        );

        this.geneticAlgorithmConfig = Objects.requireNonNull(
                geneticAlgorithmConfig,
                "geneticAlgorithmConfig must not be null."
        );

        this.mobilityConfig = Objects.requireNonNull(
                mobilityConfig,
                "mobilityConfig must not be null."
        );

        this.gaParameterScalingMode = Objects.requireNonNull(
                gaParameterScalingMode,
                "gaParameterScalingMode must not be null."
        );

        this.gaParameterScaler = createScaler(gaParameterScalingMode);
    }

    /**
     * Configurazione default in modalità STATIC.
     */
    public static MaGaConfig defaultConfig() {
        return defaultConfig(GaParameterScalingMode.STATIC);
    }

    /**
     * Configurazione default scegliendo solo la modalità di scaling.
     *
     * Questo è il metodo consigliato nel main.
     *
     * @param scalingMode modalità STATIC o ADAPTIVE
     * @return configurazione MA-GA completa
     */
    public static MaGaConfig defaultConfig(
            GaParameterScalingMode scalingMode
    ) {
        return new MaGaConfig(
                FitnessWeights.defaultWeights(),
                PenaltyConfig.defaultConfig(),
                NormalizationConfig.neutral(),
                GeneticAlgorithmConfig.defaultConfig(),
                MobilityConfig.defaultConfig(),
                scalingMode
        );
    }

    public FitnessWeights getFitnessWeights() {
        return fitnessWeights;
    }

    public PenaltyConfig getPenaltyConfig() {
        return penaltyConfig;
    }

    public NormalizationConfig getNormalizationConfig() {
        return normalizationConfig;
    }

    /**
     * Restituisce la configurazione GA di base.
     *
     * Attenzione: in modalità ADAPTIVE questa non è necessariamente
     * la configurazione usata durante una specifica esecuzione.
     * Per ottenere la configurazione effettiva usare
     * resolveGeneticAlgorithmConfig(snapshot).
     */
    public GeneticAlgorithmConfig getGeneticAlgorithmConfig() {
        return geneticAlgorithmConfig;
    }

    public MobilityConfig getMobilityConfig() {
        return mobilityConfig;
    }

    public GaParameterScalingMode getGaParameterScalingMode() {
        return gaParameterScalingMode;
    }

    /**
     * Risolve la configurazione GA effettiva per lo snapshot corrente.
     *
     * In modalità STATIC restituisce geneticAlgorithmConfig.
     * In modalità ADAPTIVE restituisce una configurazione scalata.
     *
     * @param snapshot snapshot da ottimizzare
     * @return configurazione GA effettiva
     */
    public GeneticAlgorithmConfig resolveGeneticAlgorithmConfig(
            SystemSnapshot snapshot
    ) {
        return resolveGaParameterScaling(snapshot).getScaledConfig();
    }

    /**
     * Risolve la configurazione GA effettiva e restituisce anche
     * informazioni diagnostiche.
     *
     * @param snapshot snapshot da ottimizzare
     * @return risultato dello scaling
     */
    public GaParameterScalingResult resolveGaParameterScaling(
            SystemSnapshot snapshot
    ) {
        Objects.requireNonNull(snapshot, "snapshot must not be null.");

        return gaParameterScaler.scaleDetailed(
                snapshot,
                geneticAlgorithmConfig
        );
    }

    private static GaParameterScaler createScaler(
            GaParameterScalingMode mode
    ) {
        if (mode == GaParameterScalingMode.ADAPTIVE) {
            return GaParameterScaler.adaptiveDefault();
        }

        return GaParameterScaler.staticScaler();
    }

    @Override
    public String toString() {
        return "MaGaConfig{"
                + "fitnessWeights=" + fitnessWeights
                + ", penaltyConfig=" + penaltyConfig
                + ", normalizationConfig=" + normalizationConfig
                + ", geneticAlgorithmConfig=" + geneticAlgorithmConfig
                + ", mobilityConfig=" + mobilityConfig
                + ", gaParameterScalingMode=" + gaParameterScalingMode
                + '}';
    }
}
