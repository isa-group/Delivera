package com.delivera.fms.routing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Tipos de solver soportados por el gateway.
 *
 * Para dar de alta uno nuevo: anadir aqui la constante y su bloque
 * correspondiente bajo fms.engines en application.yml. El cliente HTTP, el
 * despacho y el catalogo GET /api/v1/fms/solvers se actualizan solos.
 */
@Schema(description = "Tipo de algoritmo de resolucion de rutas", enumAsRef = true)
public enum TypeSolver {
    RANDOM,
    GREEDY,
    GENETIC
}
