package com.delivera.fms.engine.genetic.operator.search;

import com.delivera.fms.engine.core.model.Depot;
import com.delivera.fms.engine.core.search.RouteOptimizer;
import com.delivera.fms.engine.genetic.scheduler.PermutationCodec;
import org.uma.jmetal.solution.permutationsolution.PermutationSolution;

import java.util.List;
import java.util.Map;

/**
 * Busqueda local a nivel de ruta sobre un cromosoma.
 *
 * El trabajo lo hace {@link RouteOptimizer}, del nucleo compartido; aqui solo se traduce la
 * permutacion a secuencias por deposito y de vuelta.
 */
public class LocalSearch {

    private final List<Depot> depots;
    private final RouteOptimizer optimizer;

    public LocalSearch(List<Depot> depots, RouteOptimizer optimizer) {
        this.depots = depots;
        this.optimizer = optimizer;
    }

    public void improveSolution(PermutationSolution<Integer> solution) {
        Map<Integer, Depot> depotMap = PermutationCodec.depotMap(solution);
        if (depotMap == null) {
            return;
        }

        Map<Depot, List<Integer>> depotOrder = PermutationCodec.depotOrder(solution, depots, depotMap);
        for (var entry : depotOrder.entrySet()) {
            optimizer.improve(entry.getKey(), entry.getValue());
        }

        PermutationCodec.writeBack(solution, depotOrder, depotMap);
    }
}
