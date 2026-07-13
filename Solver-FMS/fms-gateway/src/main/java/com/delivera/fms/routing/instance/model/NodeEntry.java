package com.delivera.fms.routing.instance.model;

public record NodeEntry(
        int id,
        double x,
        double y,
        double serviceDuration,
        int demand,
        int frequency,
        int visitCombinationsCount,
        int[] visitCombinations,
        double timeWindowStart,
        double timeWindowEnd
) {
}
