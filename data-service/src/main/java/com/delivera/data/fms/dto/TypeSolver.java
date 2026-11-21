package com.delivera.data.fms.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Tipo de algoritmo de resolucion de rutas",
        enumAsRef = true)
public enum TypeSolver {
    RANDOM,
    GREEDY,
    GENETIC,
    ANNEALING,
    QISKIT,
    DWAVE,
    // El engine de PyVRP responde con solverUsed "HGS-ADC".
    @JsonAlias("HGS-ADC")
    HGS
}
