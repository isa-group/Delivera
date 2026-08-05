package com.delivera.fms.routing.config;

import com.delivera.fms.routing.dto.ParameterType;
import com.delivera.fms.routing.dto.TypeSolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Catalogo de motores declarado en configuracion (fms.engines.*).
 *
 * Es la unica fuente de verdad sobre que solvers existen y que saben hacer: los
 * clientes HTTP, el despachador, la resolucion de parametros y el endpoint GET
 * de solvers se construyen a partir de este mapa. Anadir un motor nuevo consiste
 * en anadir su constante a {@link TypeSolver} y su bloque de configuracion en
 * application.yml; ningun codigo Java necesita cambiar.
 *
 * Que los metadatos vivan en la configuracion de la pasarela, y no dentro del
 * motor, es lo que permite integrar solvers de terceros o escritos en otra
 * tecnologia: basta con que expongan el contrato HTTP de resolucion, y quien los
 * integra declara aqui su descripcion, sus parametros y sus restricciones.
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
     * obligatorio; el resto de campos componen el descriptor que publica el
     * catalogo.
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

        /** Tecnologia de implementacion. Informativo, no afecta al despacho. */
        private String technology;

        /** Version del solver, para poder citar resultados de benchmark. */
        private String version;

        /** Indica si dos ejecuciones con la misma entrada dan el mismo resultado. */
        private boolean deterministic = true;

        /** Peso de ordenacion en el catalogo (menor aparece antes). */
        private int order = 100;

        /** Permite ocultar un motor del catalogo sin borrar su configuracion. */
        private boolean enabled = true;

        /** Parametros de invocacion que acepta el motor. */
        private List<@Valid Parameter> parameters = new ArrayList<>();

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

        public String getTechnology() {
            return technology;
        }

        public void setTechnology(String technology) {
            this.technology = technology;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
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

        public List<Parameter> getParameters() {
            return parameters;
        }

        public void setParameters(List<Parameter> parameters) {
            this.parameters = parameters;
        }
    }

    /** Declaracion de un parametro de invocacion. */
    public static class Parameter {

        @NotBlank
        private String name;

        private String description;

        /** Sin valor por defecto: un parametro sin tipo declarado aborta el arranque. */
        @NotNull
        private ParameterType type;

        /** Valor aplicado si el cliente no envia el parametro. */
        private Object defaultValue;

        private Double min;

        private Double max;

        private boolean required;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public ParameterType getType() {
            return type;
        }

        public void setType(ParameterType type) {
            this.type = type;
        }

        public Object getDefaultValue() {
            return defaultValue;
        }

        public void setDefaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
        }

        public Double getMin() {
            return min;
        }

        public void setMin(Double min) {
            this.min = min;
        }

        public Double getMax() {
            return max;
        }

        public void setMax(Double max) {
            this.max = max;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }
    }

}
