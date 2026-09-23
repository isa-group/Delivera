package com.delivera.fms.engine.genetic.operator.mutation;

import com.delivera.fms.engine.core.model.Customer;
import com.delivera.fms.engine.core.model.Depot;
import com.delivera.fms.engine.core.split.RouteSplitter;
import com.delivera.fms.engine.genetic.scheduler.PermutationCodec;
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
    private final List<Customer> customers;
    private final List<Depot> depots;
    private final double[][] distanceMatrix;
    private final RouteSplitter splitter;

    public InterDepotMutation(double probability,
                               List<Customer> customers,
                               List<Depot> depots,
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

        Map<Integer, Depot> depotMap = PermutationCodec.depotMap(solution);
        if (depotMap == null) {
            return solution;
        }

        Map<Depot, List<Integer>> depotOrder = PermutationCodec.depotOrder(solution, depots, depotMap);

        List<Integer> border = findBorderCustomers(solution, depotMap);
        if (border.isEmpty()) {
            return solution;
        }

        int customer = border.get(random.nextInt(0, border.size() - 1));
        Depot currentDepot = depotMap.get(customer);
        Depot targetDepot = pickNearbyDepot(customer, currentDepot);
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
                                               Map<Integer, Depot> depotMap) {
        List<Integer> border = new ArrayList<>();

        for (int i = 0; i < solution.variables().size(); i++) {
            int customer = solution.variables().get(i);
            Depot assigned = depotMap.get(customer);
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
    private Depot pickNearbyDepot(int customer, Depot currentDepot) {
        double nearest = nearestOtherDistance(customer, currentDepot);
        if (nearest == Double.MAX_VALUE) {
            return null;
        }

        List<Depot> candidates = new ArrayList<>();
        for (Depot depot : depots) {
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
    private boolean fits(List<Integer> targetOrder, Depot depot, int customer) {
        int capacity = depot.capacity();
        int fleet = depot.fleet();
        if (fleet == Depot.UNLIMITED || capacity == Depot.UNLIMITED) {
            return true;
        }

        int load = customers.get(customer).demand();
        for (int assigned : targetOrder) {
            load += customers.get(assigned).demand();
        }
        return load <= (long) capacity * fleet;
    }

    private double nearestOtherDistance(int customer, Depot excluded) {
        double nearest = Double.MAX_VALUE;
        for (Depot depot : depots) {
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
