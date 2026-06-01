package config.window;

import java.util.Locale;

/**
 * Profilo temporale usato per costruire i bounds della finestra adattiva.
 *
 * <p>{@link #OBSERVED_RUNTIME} segue il profilo operativo della
 * formalizzazione: dopo la prima finestra usa il runtime del GA osservato nella
 * finestra precedente. {@link #CONFIGURED_RUNTIME} mantiene invece una stima
 * configurata e resta disponibile per replay algoritmici astratti e
 * riproducibili.</p>
 */
public enum TemporalRuntimeProfile {
    OBSERVED_RUNTIME {
        @Override
        public TemporalWindowConfig createWindowConfig() {
            return TemporalWindowConfig.observedRuntimeBoundsConfig();
        }
    },

    CONFIGURED_RUNTIME {
        @Override
        public TemporalWindowConfig createWindowConfig() {
            return TemporalWindowConfig.defaultConfig();
        }
    };

    public abstract TemporalWindowConfig createWindowConfig();

    public static TemporalRuntimeProfile parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("runtime profile must not be null or blank.");
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "OBSERVED_RUNTIME", "OBSERVED_GA_RUNTIME", "OBSERVED" -> OBSERVED_RUNTIME;
            case "CONFIGURED_RUNTIME", "CONFIGURED_GA_ESTIMATE", "CONFIGURED" -> CONFIGURED_RUNTIME;
            default -> throw new IllegalArgumentException(
                    "Unsupported runtime profile: " + value
                            + ". Supported profiles: OBSERVED_RUNTIME, CONFIGURED_RUNTIME."
            );
        };
    }

    public static boolean isSupported(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            parse(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
