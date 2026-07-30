package com.delivera.fms.routing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Estado de disponibilidad del motor que implementa el solver", enumAsRef = true)
public enum SolverStatus {

    // El motor responde correctamente a su sonda de salud.
    UP,

    // El motor esta configurado pero no responde.
    DOWN,

    // No se ha comprobado el estado (parametro includeStatus a false).
    UNKNOWN
}
