package com.delivera.fms.engine.core.search;

import com.delivera.fms.engine.core.model.Depot;
import com.delivera.fms.engine.core.model.RoutingProblem;
import com.delivera.fms.engine.core.split.RouteSplitter;

import java.util.List;

/**
 * Busqueda local a nivel de ruta sobre la secuencia de un deposito.
 *
 * Trocea la secuencia en rutas reales antes de optimizar, de modo que los movimientos se evaluan
 * contra el coste que realmente tendra la solucion. Optimizar la secuencia como si fuera un TSP y
 * trocear despues puede reducir la gira y aumentar el coste final.
 *
 * Dos movimientos: 2-opt dentro de cada ruta y reubicacion de clientes entre rutas del mismo
 * deposito. Al terminar, la secuencia se reescribe con las rutas concatenadas, de forma que el
 * siguiente troceado las recupera tal cual.
 */
public class RouteOptimizer {

    private static final int MAX_PASSES = 8;
    private static final double EPSILON = 1e-10;

    private final RoutingProblem problem;
    private final double[][] distanceMatrix;
    private final RouteSplitter splitter;

    public RouteOptimizer(RoutingProblem problem, RouteSplitter splitter) {
        this.problem = problem;
        this.distanceMatrix = problem.distanceMatrix();
        this.splitter = splitter;
    }

    // Mejora la secuencia en el sitio. Devuelve si ha cambiado algo
    public boolean improve(Depot depot, List<Integer> order) {
        if (order.size() < 2) {
            return false;
        }

        List<List<Integer>> routes = splitter.split(depot, order);
        boolean improved = improveRoutes(depot, routes, depot.capacity());

        order.clear();
        for (List<Integer> route : routes) {
            order.addAll(route);
        }
        return improved;
    }

    private boolean improveRoutes(Depot depot, List<List<Integer>> routes, int capacity) {
        boolean anyImprovement = false;
        boolean improved = true;
        int pass = 0;
        while (improved && pass++ < MAX_PASSES) {
            improved = false;
            for (List<Integer> route : routes) {
                improved |= twoOpt(depot, route);
            }
            improved |= relocate(depot, routes, capacity);
            anyImprovement |= improved;
        }
        routes.removeIf(List::isEmpty);
        return anyImprovement;
    }

    /**
     * 2-opt dentro de una ruta. Tras aceptar una inversion se reinicia el barrido: las aristas
     * cacheadas dejan de ser validas en cuanto el segmento se invierte.
     */
    private boolean twoOpt(Depot depot, List<Integer> route) {
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
    private boolean relocate(Depot depot, List<List<Integer>> routes, int capacity) {
        boolean improved = false;

        for (int from = 0; from < routes.size(); from++) {
            List<Integer> source = routes.get(from);
            int position = 0;

            while (position < source.size()) {
                int customer = source.get(position);
                int demand = problem.customer(customer).demand();
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
            load += problem.customer(customer).demand();
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
        return problem.customer(customer).matrixIndex();
    }
}
