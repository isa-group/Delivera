package com.delivera.fms.engine.core.split;

import com.delivera.fms.engine.core.model.Customer;
import com.delivera.fms.engine.core.model.Depot;
import com.delivera.fms.engine.core.model.Route;
import com.delivera.fms.engine.core.model.RoutingProblem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Trocea la secuencia de clientes de un deposito en rutas respetando capacidad, duracion maxima y
 * numero de vehiculos disponibles.
 *
 * Usa la programacion dinamica de Prins: para un orden dado devuelve el corte de coste minimo, no
 * el primero que cabe. Es el unico punto donde una secuencia se convierte en rutas, de forma que
 * la funcion objetivo de cualquier motor y la respuesta que devuelve son el mismo calculo.
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

    private final RoutingProblem problem;
    private final double[][] distanceMatrix;

    public RouteSplitter(RoutingProblem problem) {
        this.problem = problem;
        this.distanceMatrix = problem.distanceMatrix();
    }

    public RoutingProblem problem() {
        return problem;
    }

    public static double fleetPenalty(int routeCount, int fleet) {
        return (fleet != Depot.UNLIMITED && routeCount > fleet)
                ? (routeCount - fleet) * FLEET_PENALTY
                : 0.0;
    }

    // Coste y numero de rutas del mejor troceado, sin materializarlo.
    public Split evaluate(Depot depot, List<Integer> order) {
        if (order.isEmpty()) {
            return new Split(0.0, 0);
        }
        State state = solve(depot, order);
        return new Split(state.cost[order.size()], state.routeCount[order.size()]);
    }

    /**
     * Coste del deposito tal y como lo ve la funcion objetivo: el del mejor troceado mas la
     * penalizacion por las rutas que excedan la flota.
     */
    public double penalizedCost(Depot depot, List<Integer> order) {
        Split split = evaluate(depot, order);
        return split.cost() + fleetPenalty(split.routeCount(), depot.fleet());
    }

    public List<List<Integer>> split(Depot depot, List<Integer> order) {
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

    /** Las rutas del mejor troceado, con su distancia, carga y servicio ya calculados. */
    public List<Route> routes(Depot depot, List<Integer> order) {
        List<Route> routes = new ArrayList<>();
        for (List<Integer> customers : split(depot, order)) {
            routes.add(materialize(depot, customers));
        }
        return routes;
    }

    /**
     * Si el mejor troceado cumple de verdad capacidad, duracion y flota.
     *
     * La penalizacion orienta la busqueda pero no sirve como criterio de aceptacion: una solucion
     * infactible barata puede quedar por debajo de una factible cara. Esto es lo que hay que
     * comprobar antes de devolver un resultado.
     */
    public boolean isFeasible(Depot depot, List<Integer> order) {
        List<Route> routes = routes(depot, order);
        if (depot.hasFleetLimit() && routes.size() > depot.fleet()) {
            return false;
        }
        for (Route route : routes) {
            if (route.load() > depot.capacity() || route.duration() > depot.durationLimit()) {
                return false;
            }
        }
        return true;
    }

    private Route materialize(Depot depot, List<Integer> customers) {
        double distance = 0.0;
        int load = 0;
        double service = 0.0;
        int current = depot.matrixIndex();
        for (int index : customers) {
            Customer customer = problem.customer(index);
            distance += distanceMatrix[current][customer.matrixIndex()];
            load += customer.demand();
            service += customer.service();
            current = customer.matrixIndex();
        }
        distance += distanceMatrix[current][depot.matrixIndex()];
        return new Route(depot, List.copyOf(customers), distance, load, service);
    }

    /**
     * Trocea al minimo coste y, si eso necesita mas vehiculos de los que hay, reintenta acotando el
     * numero de rutas.
     *
     * Sin ese reintento el troceado no puede cambiar algo de distancia por una ruta menos: la
     * penalizacion de flota se aplicaria fuera, cuando el corte ya esta decidido, y cualquier
     * re-troceado posterior desharia la reparacion que hubiera hecho la busqueda inter-deposito.
     */
    private State solve(Depot depot, List<Integer> order) {
        // Los tramos de ruta se calculan una sola vez: el DP acotado los recorre una vez por cada
        // numero de vehiculos, y recalcularlos ahi multiplicaba el coste del troceado.
        double[][] segments = buildSegments(depot, order);

        State unlimited = solveUnlimited(segments, order.size());
        int fleet = depot.fleet();

        if (!depot.hasFleetLimit() || unlimited.routeCount[order.size()] <= fleet) {
            return unlimited;
        }

        State limited = solveWithRouteLimit(segments, order.size(), fleet);
        return (limited != null) ? limited : unlimited;
    }

    /**
     * {@code segments[i][k]} es el coste de la ruta que sirve las posiciones {@code i..i+k}, o
     * {@link Double#MAX_VALUE} si esa ruta no cumple duracion.
     */
    private double[][] buildSegments(Depot depot, List<Integer> order) {
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

        RouteScan(Depot depot, List<Integer> order, int from) {
            this.order = order;
            this.from = from;
            this.depotIndex = depot.matrixIndex();
            this.capacity = depot.capacity();
            this.durationLimit = depot.durationLimit();
            this.position = from - 1;
            this.previous = depotIndex;
        }

        boolean next() {
            while (++position < order.size()) {
                Customer customer = problem.customer(order.get(position));
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
    public double insertionCost(Depot depot, List<Integer> order, int position, int customer) {
        int depotIndex = depot.matrixIndex();
        int previous = (position > 0) ? matrixIndex(order.get(position - 1)) : depotIndex;
        int next = (position < order.size()) ? matrixIndex(order.get(position)) : depotIndex;
        int current = matrixIndex(customer);

        return distanceMatrix[previous][current] + distanceMatrix[current][next]
                - distanceMatrix[previous][next];
    }

    // Ahorro de sacar de la secuencia el cliente que ocupa la posicion dada.
    public double removalGain(Depot depot, List<Integer> order, int position) {
        int depotIndex = depot.matrixIndex();
        int previous = (position > 0) ? matrixIndex(order.get(position - 1)) : depotIndex;
        int next = (position < order.size() - 1) ? matrixIndex(order.get(position + 1)) : depotIndex;
        int current = matrixIndex(order.get(position));

        return distanceMatrix[previous][current] + distanceMatrix[current][next]
                - distanceMatrix[previous][next];
    }

    public int bestPosition(Depot depot, List<Integer> order, int customer) {
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
        return problem.customer(customer).matrixIndex();
    }

    public record Split(double cost, int routeCount) {
    }

    private record State(double[] cost, int[] routeCount, int[] predecessor) {
    }
}
