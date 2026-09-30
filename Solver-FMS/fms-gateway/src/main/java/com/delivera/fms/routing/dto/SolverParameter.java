package com.delivera.fms.routing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Declaracion de un parametro de invocacion de un solver.
 *
 * El valor por defecto forma parte del contrato publico: es el que aplica la
 * pasarela cuando el cliente no envia el parametro, de modo que el motor siempre
 * ejecuta una configuracion completa y conocida.
 */
@Schema(description = "Configurable solver parameter, with its default value and valid range")
public record SolverParameter(

        @Schema(description = "Parameter name, as sent in the 'parameters' map",
                example = "populationSize")
        String name,

        @Schema(description = "What the parameter controls and how it affects the solver's behaviour",
                example = "Individuals per generation")
        String description,

        @Schema(description = "Data type. Tells whether the parameter accepts decimals")
        ParameterType type,

        @Schema(description = "Value applied if the client does not send the parameter. " +
                "Absent means the solver decides internally",
                example = "150")
        Object defaultValue,

        @Schema(description = "Minimum accepted value. Absent means no lower bound", example = "10")
        Double min,

        @Schema(description = "Maximum accepted value. Absent means no upper bound", example = "2000")
        Double max,

        @Schema(description = "If true, the client must send it because there is no default value",
                example = "false")
        boolean required
) {

    // Un parametro tiene valor por defecto util si esta declarado explicitamente.
    public boolean hasDefault() {
        return defaultValue != null;
    }
}
