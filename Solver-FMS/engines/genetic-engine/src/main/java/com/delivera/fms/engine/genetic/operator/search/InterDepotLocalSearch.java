package com.delivera.fms.engine.genetic.operator.search;

import com.delivera.fms.engine.core.model.Depot;
import com.delivera.fms.engine.core.search.DepotRebalancer;
import com.delivera.fms.engine.genetic.scheduler.PermutationCodec;
import org.uma.jmetal.solution.permutationsolution.PermutationSolution;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reasignacion de clientes entre depositos sobre un cromosoma: reparacion de flota y mejora.
 *
 * El trabajo lo hace {@link DepotRebalancer}, del nucleo compartido; aqui se traduce la
 * permutacion a secuencias por deposito y, como algun cliente puede cambiar de deposito, se
 * reconstruye el mapa de asignacion y se reescribe la permutacion de forma contigua.
 */
public class InterDepotLocalSearch {

    private final List<Depot> depots;
    private final DepotRebalancer rebalancer;

    public InterDepotLocalSearch(List<Depot> depots, DepotRebalancer rebalancer) {
        this.depots = depots;
        this.rebalancer = rebalancer;
    }

    public boolean optimize(PermutationSolution<Integer> solution) {
        Map<Integer, Depot> depotMap = PermutationCodec.depotMap(solution);
        if (depotMap == null) {
            return false;
        }

        Map<Depot, List<Integer>> depotOrder = PermutationCodec.depotOrder(solution, depots, depotMap);
        List<List<Integer>> orders = new ArrayList<>(depotOrder.values());

        if (!rebalancer.rebalance(orders)) {
            return false;
        }

        for (var entry : depotOrder.entrySet()) {
            for (int customer : entry.getValue()) {
                depotMap.put(customer, entry.getKey());
            }
        }
        PermutationCodec.writeBackContiguous(solution, depotOrder);
        return true;
    }
}
