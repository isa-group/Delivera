package com.delivera.fms.routing.config;

import com.delivera.fms.routing.dto.TypeSolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.EnumMap;
import java.util.Map;

/**
 * Catalogo de motores declarado en configuracion (fms.engines.*).
 *
 * Es la unica fuente de verdad sobre que solvers existen: los clientes HTTP,
 * el despachador y el endpoint GET de solvers se construyen a partir de este
 * mapa. Anadir un motor nuevo consiste en anadir su constante a
 * {@link TypeSolver} y su bloque de configuracion en application.yml; ningun
 * codigo Java necesita cambiar.
 */
@ConfigurationProperties(prefix = "fms")
@Validated
public class EngineProperties {
    /** Motores disponibles, indexados por tipo de solver. */
    private Map<TypeSolver, @Valid Engine> engines = new EnumMap<>(TypeSolver.class);

    public Map<TypeSolver, Engine> getEngines() {
        return engines;
    }

    public void setEngines(Map<TypeSolver, Engine> engines) {
        this.engines = engines;
    }

    /**
     * Metadatos y ubicacion de un motor concreto. Solo {@code url} es
     * obligatorio; el resto de campos enriquecen la respuesta del catalogo.
     */
    public static class Engine {

        /** URL base del microservicio del motor. */
        @NotBlank
        private String url;

        /** Nombre legible del solver. Por defecto, el nombre de la constante. */
        private String displayName;

        /** Descripcion funcional del algoritmo. */
        private String description;

        /** Familia algoritmica (constructivo, metaheuristica, exacto...). */
        private String strategy;

        /** Indica si dos ejecuciones con la misma entrada dan el mismo resultado. */
        private boolean deterministic = true;

        /** Peso de ordenacion en el catalogo (menor aparece antes). */
        private int order = 100;

        /** Permite ocultar un motor del catalogo sin borrar su configuracion. */
        private boolean enabled = true;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getStrategy() {
            return strategy;
        }

        public void setStrategy(String strategy) {
            this.strategy = strategy;
        }

        public boolean isDeterministic() {
            return deterministic;
        }

        public void setDeterministic(boolean deterministic) {
            this.deterministic = deterministic;
        }

        public int getOrder() {
            return order;
        }

        public void setOrder(int order) {
            this.order = order;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
