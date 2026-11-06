package com.delivera.data.fms.dto.solver.info;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Availability status of the engine implementing the solver", enumAsRef = true)
public enum SolverStatus {

    // El motor responde correctamente a su sonda de salud.
    UP,

    // El motor esta configurado pero no responde.
    DOWN,

    // No se ha comprobado el estado (parametro includeStatus a false).
    UNKNOWN
}
