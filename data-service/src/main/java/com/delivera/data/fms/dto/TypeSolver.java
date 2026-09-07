package com.delivera.data.fms.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Tipo de algoritmo de resolucion de rutas",
        enumAsRef = true)
public enum TypeSolver {
    RANDOM,
    GREEDY,
    GENETIC
}
