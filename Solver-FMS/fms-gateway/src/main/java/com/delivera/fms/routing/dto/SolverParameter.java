package com.delivera.fms.routing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Declaracion de un parametro de invocacion de un solver.
 *
 * El valor por defecto forma parte del contrato publico: es el que aplica la
 * pasarela cuando el cliente no envia el parametro, de modo que el motor siempre
 * ejecuta una configuracion completa y conocida.
 */
@Schema(description = "Parametro configurable de un solver, con su valor por defecto y su rango valido")
public record SolverParameter(

        @Schema(description = "Nombre del parametro, tal y como se envia en el mapa 'parameters'",
                example = "populationSize")
        String name,

        @Schema(description = "Que controla el parametro y como afecta al comportamiento del solver",
                example = "Individuos por generacion")
        String description,

        @Schema(description = "Tipo de dato. Indica si el parametro admite decimales")
        ParameterType type,

        @Schema(description = "Valor aplicado si el cliente no envia el parametro. " +
                "Ausente significa que el solver decide internamente",
                example = "150")
        Object defaultValue,

        @Schema(description = "Valor minimo admitido. Ausente significa sin cota inferior", example = "10")
        Double min,

        @Schema(description = "Valor maximo admitido. Ausente significa sin cota superior", example = "2000")
        Double max,

        @Schema(description = "Si es true, el cliente debe enviarlo porque no hay valor por defecto",
                example = "false")
        boolean required
) {

    // Un parametro tiene valor por defecto util si esta declarado explicitamente.
    public boolean hasDefault() {
        return defaultValue != null;
    }
}
