package com.delivera.fms.engine.annealing.dto;

/**
 * Un punto de la curva anytime: el instante, desde el arranque, en el que la mejor solucion
 * factible paso a costar {@code cost}.
 */
public record TracePointDto(long elapsedMs, double cost) {
}
