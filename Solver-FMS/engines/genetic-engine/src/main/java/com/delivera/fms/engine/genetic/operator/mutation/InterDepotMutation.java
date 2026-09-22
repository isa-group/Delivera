package com.delivera.fms.engine.genetic.operator.mutation;

import com.delivera.fms.engine.genetic.dto.CustomerDto;
import com.delivera.fms.engine.genetic.dto.DepotDto;
import com.delivera.fms.engine.genetic.scheduler.PermutationCodec;
import com.delivera.fms.engine.genetic.scheduler.RouteSplitter;
import org.uma.jmetal.solution.permutationsolution.PermutationSolution;
import org.uma.jmetal.util.pseudorandom.PseudoRandomGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reasigna un cliente frontera a otro deposito cercano.
 *
 * Es un operador de diversificacion: el deposito destino se elige al azar entre los cercanos, no
 * siempre el segundo mas proximo, que daba un movimiento deterministico y casi siempre igual. La
 * seleccion posterior decide si el cambio sobrevive; lo que si se comprueba aqui es que el deposito
 * destino tenga flota suficiente para absorber la demanda.
 */
public class InterDepotMutation {

    private static final double BORDER_RATIO = 1.3;

    private final double probability;
    private final PseudoRandomGenerator random;
    private final List<CustomerDto> customers;
    private final List<DepotDto> depots;
    private final double[][] distanceMatrix;
    private final RouteSplitter splitter;

    public InterDepotMutation(double probability,
                               List<CustomerDto> customers,
                               List<DepotDto> depots,
                               double[][] distanceMatrix,
                               RouteSplitter splitter,
                               PseudoRandomGenerator random) {
        this.probability = probability;
        this.random = random;
        this.customers = customers;
        this.depots = depots;
        this.distanceMatrix = distanceMatrix;
        this.splitter = splitter;
    }

    public PermutationSolution<Integer> execute(PermutationSolution<Integer> solution) {
        if (depots.size() < 2 || random.nextDouble() >= probability) {
            return solution;
        }

        Map<Integer, DepotDto> depotMap = PermutationCodec.depotMap(solution);
        if (depotMap == null) {
            return solution;
        }

        Map<DepotDto, List<Integer>> depotOrder = PermutationCodec.depotOrder(solution, depots, depotMap);

        List<Integer> border = findBorderCustomers(solution, depotMap);
        if (border.isEmpty()) {
            return solution;
        }

        int customer = border.get(random.nextInt(0, border.size() - 1));
        DepotDto currentDepot = depotMap.get(customer);
        DepotDto targetDepot = pickNearbyDepot(customer, currentDepot);
        if (targetDepot == null || !fits(depotOrder.get(targetDepot), targetDepot, customer)) {
            return solution;
        }

        depotOrder.get(currentDepot).remove(Integer.valueOf(customer));
        List<Integer> target = depotOrder.get(targetDepot);
        target.add(splitter.bestPosition(targetDepot, target, customer), customer);
        depotMap.put(customer, targetDepot);

        PermutationCodec.writeBackContiguous(solution, depotOrder);
        return solution;
    }

    private List<Integer> findBorderCustomers(PermutationSolution<Integer> solution,
                                               Map<Integer, DepotDto> depotMap) {
        List<Integer> border = new ArrayList<>();

        for (int i = 0; i < solution.variables().size(); i++) {
            int customer = solution.variables().get(i);
            DepotDto assigned = depotMap.get(customer);
            if (assigned == null) {
                continue;
            }
            double distanceToAssigned = distanceMatrix[assigned.matrixIndex()][matrixIndex(customer)];
            if (nearestOtherDistance(customer, assigned) / (distanceToAssigned + 1e-10) < BORDER_RATIO) {
                border.add(customer);
            }
        }

        return border;
    }

    // Deposito destino aleatorio entre los que estan a distancia comparable del cliente.
    private DepotDto pickNearbyDepot(int customer, DepotDto currentDepot) {
        double nearest = nearestOtherDistance(customer, currentDepot);
        if (nearest == Double.MAX_VALUE) {
            return null;
        }

        List<DepotDto> candidates = new ArrayList<>();
        for (DepotDto depot : depots) {
            if (depot.equals(currentDepot)) {
                continue;
            }
            if (distanceMatrix[depot.matrixIndex()][matrixIndex(customer)] <= nearest * BORDER_RATIO) {
                candidates.add(depot);
            }
        }

        return candidates.isEmpty() ? null : candidates.get(random.nextInt(0, candidates.size() - 1));
    }

    // El deposito destino debe poder servir la demanda con los vehiculos de los que dispone.
    private boolean fits(List<Integer> targetOrder, DepotDto depot, int customer) {
        int capacity = splitter.capacity(depot);
        int fleet = splitter.fleet(depot);
        if (fleet == Integer.MAX_VALUE || capacity == Integer.MAX_VALUE) {
            return true;
        }

        int load = customers.get(customer).demand();
        for (int assigned : targetOrder) {
            load += customers.get(assigned).demand();
        }
        return load <= (long) capacity * fleet;
    }

    private double nearestOtherDistance(int customer, DepotDto excluded) {
        double nearest = Double.MAX_VALUE;
        for (DepotDto depot : depots) {
            if (depot.equals(excluded)) {
                continue;
            }
            nearest = Math.min(nearest, distanceMatrix[depot.matrixIndex()][matrixIndex(customer)]);
        }
        return nearest;
    }

    private int matrixIndex(int customer) {
        return customers.get(customer).matrixIndex();
    }
}
