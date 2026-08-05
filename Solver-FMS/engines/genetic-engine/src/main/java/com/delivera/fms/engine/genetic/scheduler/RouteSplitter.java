package com.delivera.fms.engine.genetic.scheduler;

import com.delivera.fms.engine.genetic.dto.CustomerDto;
import com.delivera.fms.engine.genetic.dto.DepotDto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Trocea la secuencia de clientes de un deposito en rutas respetando capacidad, duracion maxima y
 * numero de vehiculos disponibles.
 *
 * Usa la programacion dinamica de Prins: para un orden dado devuelve el corte de coste minimo, no
 * el primero que cabe. Es el unico punto donde se convierte un cromosoma en rutas, de forma que la
 * funcion objetivo del algoritmo y la respuesta que se devuelve al cliente son el mismo calculo.
 *
 * Ofrece ademas los deltas de insercion y eliminacion sobre esas secuencias, que todos los
 * operadores deben usar para no valorar aristas entre clientes de depositos distintos.
 */
public class RouteSplitter {

    /**
     * Penalizacion por cada ruta que excede la flota de un deposito. Muy por encima del coste de una
     * ruta tipica, para que una solucion infactible nunca gane a una factible. La comparten la
     * funcion objetivo y la busqueda inter-deposito: si solo la aplicara la primera, la busqueda
     * podria deshacer una reparacion por ganar unos metros de distancia.
     */
    public static final double FLEET_PENALTY = 1000.0;

    /** Penalizacion por una ruta que excede la duracion maxima y no se puede partir mas. */
    private static final double DURATION_PENALTY = 1000.0;

    private final List<CustomerDto> customers;
    private final double[][] distanceMatrix;
    private final Map<DepotDto, Integer> capacityByDepot;
    private final Map<DepotDto, Integer> fleetByDepot;

    public RouteSplitter(List<CustomerDto> customers,
                         double[][] distanceMatrix,
                         Map<DepotDto, Integer> capacityByDepot,
                         Map<DepotDto, Integer> fleetByDepot) {
        this.customers = customers;
        this.distanceMatrix = distanceMatrix;
        this.capacityByDepot = capacityByDepot;
        this.fleetByDepot = fleetByDepot;
    }

    public int capacity(DepotDto depot) {
        return capacityByDepot.getOrDefault(depot, Integer.MAX_VALUE);
    }

    public int fleet(DepotDto depot) {
        return fleetByDepot.getOrDefault(depot, Integer.MAX_VALUE);
    }

    public static double fleetPenalty(int routeCount, int fleet) {
        return (fleet != Integer.MAX_VALUE && routeCount > fleet)
                ? (routeCount - fleet) * FLEET_PENALTY
                : 0.0;
    }

    // Coste y numero de rutas del mejor troceado, sin materializarlo. 
    public Split evaluate(DepotDto depot, List<Integer> order) {
        if (order.isEmpty()) {
            return new Split(0.0, 0);
        }
        State state = solve(depot, order);
        return new Split(state.cost[order.size()], state.routeCount[order.size()]);
    }

    public List<List<Integer>> split(DepotDto depot, List<Integer> order) {
        List<List<Integer>> routes = new ArrayList<>();
        if (order.isEmpty()) {
            return routes;
        }

        State state = solve(depot, order);
        int end = order.size();
        while (end > 0) {
            int start = state.predecessor[end];
            routes.add(new ArrayList<>(order.subList(start, end)));
            end = start;
        }
        Collections.reverse(routes);
        return routes;
    }

    /**
     * Trocea al minimo coste y, si eso necesita mas vehiculos de los que hay, reintenta acotando el
     * numero de rutas.
     *
     * Sin ese reintento el troceado no puede cambiar algo de distancia por una ruta menos: la
     * penalizacion de flota se aplicaria fuera, cuando el corte ya esta decidido, y cualquier
     * re-troceado posterior desharia la reparacion que hubiera hecho la busqueda inter-deposito.
     */
    private State solve(DepotDto depot, List<Integer> order) {
        // Los tramos de ruta se calculan una sola vez: el DP acotado los recorre una vez por cada
        // numero de vehiculos, y recalcularlos ahi multiplicaba el coste del troceado.
        double[][] segments = buildSegments(depot, order);

        State unlimited = solveUnlimited(segments, order.size());
        int fleet = fleet(depot);

        if (fleet == Integer.MAX_VALUE || unlimited.routeCount[order.size()] <= fleet) {
            return unlimited;
        }

        State limited = solveWithRouteLimit(segments, order.size(), fleet);
        return (limited != null) ? limited : unlimited;
    }

    /**
     * {@code segments[i][k]} es el coste de la ruta que sirve las posiciones {@code i..i+k}, o
     * {@link Double#MAX_VALUE} si esa ruta no cumple duracion.
     */
    private double[][] buildSegments(DepotDto depot, List<Integer> order) {
        int n = order.size();
        double[][] segments = new double[n][];
        double[] buffer = new double[n];

        for (int i = 0; i < n; i++) {
            int length = 0;
            RouteScan scan = new RouteScan(depot, order, i);
            while (scan.next()) {
                while (length < scan.position() - i) {
                    buffer[length++] = Double.MAX_VALUE;
                }
                buffer[length++] = scan.routeCost();
            }
            segments[i] = Arrays.copyOf(buffer, length);
        }

        return segments;
    }

    private State solveUnlimited(double[][] segments, int n) {
        double[] cost = new double[n + 1];
        int[] routeCount = new int[n + 1];
        int[] predecessor = new int[n + 1];
        Arrays.fill(cost, Double.MAX_VALUE);
        cost[0] = 0.0;

        for (int i = 0; i < n; i++) {
            if (cost[i] == Double.MAX_VALUE) {
                continue;
            }
            double[] row = segments[i];
            for (int k = 0; k < row.length; k++) {
                if (row[k] == Double.MAX_VALUE) {
                    continue;
                }
                int j = i + k;
                double candidate = cost[i] + row[k];
                if (candidate < cost[j + 1]) {
                    cost[j + 1] = candidate;
                    routeCount[j + 1] = routeCount[i] + 1;
                    predecessor[j + 1] = i;
                }
            }
        }

        return new State(cost, routeCount, predecessor);
    }

    // Mejor troceado que usa como mucho {@code maxRoutes} rutas, o {@code null} si no existe.
    private State solveWithRouteLimit(double[][] segments, int n, int maxRoutes) {
        double[][] cost = new double[maxRoutes + 1][n + 1];
        int[][] predecessor = new int[maxRoutes + 1][n + 1];
        for (double[] row : cost) {
            Arrays.fill(row, Double.MAX_VALUE);
        }
        cost[0][0] = 0.0;

        for (int routes = 1; routes <= maxRoutes; routes++) {
            for (int i = 0; i < n; i++) {
                if (cost[routes - 1][i] == Double.MAX_VALUE) {
                    continue;
                }
                double[] row = segments[i];
                for (int k = 0; k < row.length; k++) {
                    if (row[k] == Double.MAX_VALUE) {
                        continue;
                    }
                    int j = i + k;
                    double candidate = cost[routes - 1][i] + row[k];
                    if (candidate < cost[routes][j + 1]) {
                        cost[routes][j + 1] = candidate;
                        predecessor[routes][j + 1] = i;
                    }
                }
            }
        }

        int bestRoutes = -1;
        double bestCost = Double.MAX_VALUE;
        for (int routes = 1; routes <= maxRoutes; routes++) {
            if (cost[routes][n] < bestCost) {
                bestCost = cost[routes][n];
                bestRoutes = routes;
            }
        }
        if (bestRoutes < 0) {
            return null;
        }

        double[] flatCost = new double[n + 1];
        int[] flatRoutes = new int[n + 1];
        int[] flatPredecessor = new int[n + 1];
        int end = n;
        int routes = bestRoutes;
        while (end > 0) {
            int start = predecessor[routes][end];
            flatPredecessor[end] = start;
            end = start;
            routes--;
        }
        flatCost[n] = bestCost;
        flatRoutes[n] = bestRoutes;

        return new State(flatCost, flatRoutes, flatPredecessor);
    }

    /**
     * Recorre las rutas que pueden empezar en una posicion dada, parando cuando ya no cabe ni por
     * capacidad ni por duracion.
     */
    private final class RouteScan {

        private final List<Integer> order;
        private final int depotIndex;
        private final int capacity;
        private final double durationLimit;
        private final int from;

        private int position;
        private int load;
        private double legs;
        private double service;
        private int previous;
        private double routeCost;

        RouteScan(DepotDto depot, List<Integer> order, int from) {
            this.order = order;
            this.from = from;
            this.depotIndex = depot.matrixIndex();
            this.capacity = capacity(depot);
            this.durationLimit = depot.durationLimit();
            this.position = from - 1;
            this.previous = depotIndex;
        }

        boolean next() {
            while (++position < order.size()) {
                CustomerDto customer = customers.get(order.get(position));
                if (load > 0 && load + customer.demand() > capacity) {
                    return false;
                }
                load += customer.demand();
                legs += distanceMatrix[previous][customer.matrixIndex()];
                service += customer.service();
                previous = customer.matrixIndex();

                // Ida y servicio solo crecen: pasados del limite ningun corte posterior cabe.
                if (position > from && legs + service > durationLimit) {
                    return false;
                }

                // El coste es distancia pura; el tiempo de servicio solo consume duracion de ruta.
                double distance = legs + distanceMatrix[previous][depotIndex];
                if (distance + service > durationLimit) {
                    if (position > from) {
                        continue;
                    }
                    // Cliente que no cabe ni solo: se tolera para no dejar la secuencia sin
                    // troceado, y la penalizacion empuja a reasignarlo a otro deposito.
                    routeCost = distance + DURATION_PENALTY;
                    return true;
                }

                routeCost = distance;
                return true;
            }
            return false;
        }

        int position() {
            return position;
        }

        double routeCost() {
            return routeCost;
        }
    }

    // Coste de insertar el cliente en la posicion dada, con el deposito como extremo de secuencia.
    public double insertionCost(DepotDto depot, List<Integer> order, int position, int customer) {
        int depotIndex = depot.matrixIndex();
        int previous = (position > 0) ? matrixIndex(order.get(position - 1)) : depotIndex;
        int next = (position < order.size()) ? matrixIndex(order.get(position)) : depotIndex;
        int current = matrixIndex(customer);

        return distanceMatrix[previous][current] + distanceMatrix[current][next]
                - distanceMatrix[previous][next];
    }

    // Ahorro de sacar de la secuencia el cliente que ocupa la posicion dada.
    public double removalGain(DepotDto depot, List<Integer> order, int position) {
        int depotIndex = depot.matrixIndex();
        int previous = (position > 0) ? matrixIndex(order.get(position - 1)) : depotIndex;
        int next = (position < order.size() - 1) ? matrixIndex(order.get(position + 1)) : depotIndex;
        int current = matrixIndex(order.get(position));

        return distanceMatrix[previous][current] + distanceMatrix[current][next]
                - distanceMatrix[previous][next];
    }
    
    public int bestPosition(DepotDto depot, List<Integer> order, int customer) {
        double bestCost = Double.MAX_VALUE;
        int bestPosition = 0;

        for (int position = 0; position <= order.size(); position++) {
            double cost = insertionCost(depot, order, position, customer);
            if (cost < bestCost) {
                bestCost = cost;
                bestPosition = position;
            }
        }

        return bestPosition;
    }

    private int matrixIndex(int customer) {
        return customers.get(customer).matrixIndex();
    }

    public record Split(double cost, int routeCount) {
    }

    private record State(double[] cost, int[] routeCount, int[] predecessor) {
    }
}
