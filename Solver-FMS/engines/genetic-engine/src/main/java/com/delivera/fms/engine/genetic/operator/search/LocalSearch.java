package com.delivera.fms.engine.genetic.operator.search;

import com.delivera.fms.engine.genetic.dto.CustomerDto;
import com.delivera.fms.engine.genetic.dto.DepotDto;
import com.delivera.fms.engine.genetic.scheduler.PermutationCodec;
import com.delivera.fms.engine.genetic.scheduler.RouteSplitter;
import org.uma.jmetal.solution.permutationsolution.PermutationSolution;

import java.util.List;
import java.util.Map;

/**
 * Busqueda local a nivel de ruta.
 *
 * Trocea la secuencia de cada deposito en rutas reales antes de optimizar, de modo que los
 * movimientos se evaluan contra el coste que realmente tendra la solucion. Optimizar la gira
 * gigante como si fuera un TSP y trocear despues puede reducir la gira y aumentar el coste final.
 */
public class LocalSearch {

    private static final int MAX_PASSES = 8;
    private static final double EPSILON = 1e-10;

    private final List<CustomerDto> customers;
    private final List<DepotDto> depots;
    private final double[][] distanceMatrix;
    private final RouteSplitter splitter;

    public LocalSearch(List<CustomerDto> customers,
                       List<DepotDto> depots,
                       double[][] distanceMatrix,
                       RouteSplitter splitter) {
        this.customers = customers;
        this.depots = depots;
        this.distanceMatrix = distanceMatrix;
        this.splitter = splitter;
    }

    public void improveSolution(PermutationSolution<Integer> solution) {
        Map<Integer, DepotDto> depotMap = PermutationCodec.depotMap(solution);
        if (depotMap == null) {
            return;
        }

        Map<DepotDto, List<Integer>> depotOrder = PermutationCodec.depotOrder(solution, depots, depotMap);

        for (var entry : depotOrder.entrySet()) {
            DepotDto depot = entry.getKey();
            List<Integer> order = entry.getValue();
            if (order.size() < 2) {
                continue;
            }

            int capacity = splitter.capacity(depot);
            List<List<Integer>> routes = splitter.split(depot, order);
            improveRoutes(depot, routes, capacity);

            order.clear();
            for (List<Integer> route : routes) {
                order.addAll(route);
            }
        }

        PermutationCodec.writeBack(solution, depotOrder, depotMap);
    }

    private void improveRoutes(DepotDto depot, List<List<Integer>> routes, int capacity) {
        boolean improved = true;
        int pass = 0;
        while (improved && pass++ < MAX_PASSES) {
            improved = false;
            for (List<Integer> route : routes) {
                improved |= twoOpt(depot, route);
            }
            improved |= relocate(depot, routes, capacity);
        }
        routes.removeIf(List::isEmpty);
    }

    /**
     * 2-opt dentro de una ruta. Tras aceptar una inversion se reinicia el barrido: las aristas
     * cacheadas dejan de ser validas en cuanto el segmento se invierte.
     */
    private boolean twoOpt(DepotDto depot, List<Integer> route) {
        int n = route.size();
        if (n < 3) {
            return false;
        }

        int depotIndex = depot.matrixIndex();
        boolean anyImprovement = false;
        boolean improved = true;

        while (improved) {
            improved = false;
            for (int i = -1; i < n - 2 && !improved; i++) {
                int previous = (i >= 0) ? matrixIndex(route.get(i)) : depotIndex;
                int start = matrixIndex(route.get(i + 1));

                for (int j = i + 2; j < n; j++) {
                    int end = matrixIndex(route.get(j));
                    int next = (j + 1 < n) ? matrixIndex(route.get(j + 1)) : depotIndex;

                    double delta = distanceMatrix[previous][end] + distanceMatrix[start][next]
                            - distanceMatrix[previous][start] - distanceMatrix[end][next];

                    if (delta < -EPSILON) {
                        reverse(route, i + 1, j);
                        improved = true;
                        anyImprovement = true;
                        break;
                    }
                }
            }
        }

        return anyImprovement;
    }

    // Mueve clientes a la mejor posicion de otra ruta del mismo deposito, respetando capacidad.
    private boolean relocate(DepotDto depot, List<List<Integer>> routes, int capacity) {
        boolean improved = false;

        for (int from = 0; from < routes.size(); from++) {
            List<Integer> source = routes.get(from);
            int position = 0;

            while (position < source.size()) {
                int customer = source.get(position);
                int demand = customers.get(customer).demand();
                double bestCost = splitter.removalGain(depot, source, position) - EPSILON;
                int bestRoute = -1;
                int bestPosition = -1;

                for (int to = 0; to < routes.size(); to++) {
                    if (to == from) {
                        continue;
                    }
                    List<Integer> target = routes.get(to);
                    if (load(target) + demand > capacity) {
                        continue;
                    }
                    for (int at = 0; at <= target.size(); at++) {
                        double cost = splitter.insertionCost(depot, target, at, customer);
                        if (cost < bestCost) {
                            bestCost = cost;
                            bestRoute = to;
                            bestPosition = at;
                        }
                    }
                }

                if (bestRoute >= 0) {
                    source.remove(position);
                    routes.get(bestRoute).add(bestPosition, customer);
                    improved = true;
                } else {
                    position++;
                }
            }
        }

        return improved;
    }

    private int load(List<Integer> route) {
        int load = 0;
        for (int customer : route) {
            load += customers.get(customer).demand();
        }
        return load;
    }

    private void reverse(List<Integer> route, int from, int to) {
        while (from < to) {
            int tmp = route.get(from);
            route.set(from, route.get(to));
            route.set(to, tmp);
            from++;
            to--;
        }
    }

    private int matrixIndex(int customer) {
        return customers.get(customer).matrixIndex();
    }
}
