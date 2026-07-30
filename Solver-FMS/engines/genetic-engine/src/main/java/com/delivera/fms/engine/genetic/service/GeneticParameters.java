package com.delivera.fms.engine.genetic.service;

import java.util.Map;

/**
 * Configuracion de una ejecucion del algoritmo genetico.
 *
 * Los valores de {@link #DEFAULTS} son los que la pasarela declara como valores
 * por defecto en los metadatos del solver: ambos lados deben coincidir para que
 * el informe de validacion describa la ejecucion que realmente ocurre.
 *
 * La conversion es tolerante con el tipo numerico recibido porque un mismo
 * parametro puede llegar como entero JSON, decimal o cadena segun el cliente; un
 * valor no convertible se rechaza antes, en la validacion previa de la pasarela.
 */
public record GeneticParameters(
        int populationSize,
        int maxEvaluations,
        int minGenerations,
        double crossoverProbability,
        double intraDepotMutationProbability,
        double interDepotMutationProbability,
        int elitismCount,
        int tournamentSize,
        int localSearchFrequency,
        int interDepotFrequency,
        int restartStagnantGenerations,
        int maxRestarts,
        double heuristicSeedRatio,
        Long seed
) {

    public static final GeneticParameters DEFAULTS = new GeneticParameters(
            150, 75000, 100, 0.9, 0.2, 0.3, 5, 3, 10, 5, 20, 3, 0.2, null);

    public static GeneticParameters from(Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return DEFAULTS;
        }
        return new GeneticParameters(
                integer(parameters, "populationSize", DEFAULTS.populationSize),
                integer(parameters, "maxEvaluations", DEFAULTS.maxEvaluations),
                integer(parameters, "minGenerations", DEFAULTS.minGenerations),
                decimal(parameters, "crossoverProbability", DEFAULTS.crossoverProbability),
                decimal(parameters, "intraDepotMutationProbability", DEFAULTS.intraDepotMutationProbability),
                decimal(parameters, "interDepotMutationProbability", DEFAULTS.interDepotMutationProbability),
                integer(parameters, "elitismCount", DEFAULTS.elitismCount),
                integer(parameters, "tournamentSize", DEFAULTS.tournamentSize),
                integer(parameters, "localSearchFrequency", DEFAULTS.localSearchFrequency),
                integer(parameters, "interDepotFrequency", DEFAULTS.interDepotFrequency),
                integer(parameters, "restartStagnantGenerations", DEFAULTS.restartStagnantGenerations),
                integer(parameters, "maxRestarts", DEFAULTS.maxRestarts),
                decimal(parameters, "heuristicSeedRatio", DEFAULTS.heuristicSeedRatio),
                seed(parameters));
    }

    // Individuos preservados en un reinicio: nunca menos de uno, o se perderia el mejor encontrado.
    public int survivorsOnRestart() {
        return Math.max(1, elitismCount);
    }

    private static int integer(Map<String, Object> parameters, String name, int fallback) {
        Number value = number(parameters, name);
        return value != null ? value.intValue() : fallback;
    }

    private static double decimal(Map<String, Object> parameters, String name, double fallback) {
        Number value = number(parameters, name);
        return value != null ? value.doubleValue() : fallback;
    }

    private static Long seed(Map<String, Object> parameters) {
        Number value = number(parameters, "seed");
        return value != null ? value.longValue() : null;
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
