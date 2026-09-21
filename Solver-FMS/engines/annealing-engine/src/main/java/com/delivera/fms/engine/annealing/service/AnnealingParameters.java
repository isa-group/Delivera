package com.delivera.fms.engine.annealing.service;

import java.util.Map;

/**
 * Configuracion de una ejecucion del recocido simulado.
 *
 * Los valores de {@link #DEFAULTS} son los que la pasarela declara como valores por defecto en los
 * metadatos del solver: ambos lados deben coincidir para que el descriptor no anuncie una ejecucion
 * distinta de la que ocurre.
 *
 * Ninguna temperatura se fija en absoluto: se derivan del tamano tipico de un empeoramiento en la
 * instancia, sondeado al arrancar. Asi la misma configuracion vale para instancias de escalas
 * distintas.
 *
 * @param timeLimitMs               presupuesto de tiempo; es el criterio de parada principal
 * @param maxLevels                 tope de niveles de temperatura; 0 significa que solo manda el
 *                                  tiempo. Es lo que hace reproducible una ejecucion con semilla
 * @param calibrationQuantile       que cuantil de los empeoramientos sondeados se toma como
 *                                  "empeoramiento pequeno" para calibrar la temperatura
 * @param initialAcceptanceRate     probabilidad con la que se acepta ese empeoramiento al arrancar;
 *                                  de aqui sale la temperatura inicial
 * @param finalAcceptanceRate       probabilidad de aceptar ese mismo empeoramiento por debajo de la
 *                                  cual se considera que el sistema esta frio y se recalienta
 * @param coolingRate               factor geometrico de enfriamiento por nivel
 * @param movesPerTemperatureFactor movimientos por nivel y por cliente
 * @param interDepotMoveProbability fraccion de movimientos que cambian un cliente de deposito
 * @param depotCandidateRatio       cuanto mas lejos que el deposito mas cercano puede estar un
 *                                  deposito destino para proponerlo
 * @param localSearchFrequency      cada cuantos niveles se aplica la busqueda local a nivel de
 *                                  ruta; 0 la deja solo para el pulido final (recocido puro)
 * @param rebalanceFrequency        cada cuantos niveles se reequilibra entre depositos; 0 lo deja
 *                                  solo para el arranque y el final
 */
public record AnnealingParameters(
        long timeLimitMs,
        int maxLevels,
        double calibrationQuantile,
        double initialAcceptanceRate,
        double finalAcceptanceRate,
        double coolingRate,
        int movesPerTemperatureFactor,
        double interDepotMoveProbability,
        double depotCandidateRatio,
        int localSearchFrequency,
        int rebalanceFrequency
) {

    public static final AnnealingParameters DEFAULTS = new AnnealingParameters(
            5000, 0, 0.05, 0.4, 0.001, 0.96, 12, 0.25, 1.3, 8, 20);

    public static AnnealingParameters from(Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return DEFAULTS;
        }
        return new AnnealingParameters(
                integer(parameters, "timeLimitMs", DEFAULTS.timeLimitMs),
                (int) integer(parameters, "maxLevels", DEFAULTS.maxLevels),
                decimal(parameters, "calibrationQuantile", DEFAULTS.calibrationQuantile),
                decimal(parameters, "initialAcceptanceRate", DEFAULTS.initialAcceptanceRate),
                decimal(parameters, "finalAcceptanceRate", DEFAULTS.finalAcceptanceRate),
                decimal(parameters, "coolingRate", DEFAULTS.coolingRate),
                (int) integer(parameters, "movesPerTemperatureFactor", DEFAULTS.movesPerTemperatureFactor),
                decimal(parameters, "interDepotMoveProbability", DEFAULTS.interDepotMoveProbability),
                decimal(parameters, "depotCandidateRatio", DEFAULTS.depotCandidateRatio),
                (int) integer(parameters, "localSearchFrequency", DEFAULTS.localSearchFrequency),
                (int) integer(parameters, "rebalanceFrequency", DEFAULTS.rebalanceFrequency));
    }

    private static long integer(Map<String, Object> parameters, String name, long fallback) {
        Number value = number(parameters, name);
        return value != null ? value.longValue() : fallback;
    }

    private static double decimal(Map<String, Object> parameters, String name, double fallback) {
        Number value = number(parameters, name);
        return value != null ? value.doubleValue() : fallback;
    }

    private static Number number(Map<String, Object> parameters, String name) {
        Object raw = parameters.get(name);
        if (raw instanceof Number value) {
            return value;
        }
        if (raw == null) {
            return null;
        }
        try {
            return Double.valueOf(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
