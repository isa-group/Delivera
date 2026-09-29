package com.delivera.fms.engine.core.split;

import com.delivera.fms.engine.core.model.Customer;
import com.delivera.fms.engine.core.model.Depot;
import com.delivera.fms.engine.core.model.Route;
import com.delivera.fms.engine.core.model.RoutingProblem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteSplitterTest {

    private static final double EPSILON = 1e-9;

    /**
     * Un deposito en el origen y cuatro clientes en linea a distancias 1, 2, 3 y 4, con demanda 1
     * cada uno. Con capacidad 2 el corte optimo es {1,2}{3,4}: 4 + 8 = 12.
     */
    @Test
    void splitsAtMinimumCostRespectingCapacity() {
        RoutingProblem problem = lineProblem(2, Depot.UNLIMITED, Double.MAX_VALUE);
        RouteSplitter splitter = new RouteSplitter(problem);
        Depot depot = problem.depot(0);
        List<Integer> order = List.of(0, 1, 2, 3);

        RouteSplitter.Split split = splitter.evaluate(depot, order);
        assertEquals(12.0, split.cost(), EPSILON);
        assertEquals(2, split.routeCount());

        List<List<Integer>> routes = splitter.split(depot, order);
        assertEquals(List.of(List.of(0, 1), List.of(2, 3)), routes);
    }

    @Test
    void materializedRoutesAddUpToTheEvaluatedCost() {
        RoutingProblem problem = lineProblem(2, Depot.UNLIMITED, Double.MAX_VALUE);
        RouteSplitter splitter = new RouteSplitter(problem);
        Depot depot = problem.depot(0);
        List<Integer> order = List.of(3, 0, 2, 1);

        double total = 0.0;
        int load = 0;
        for (Route route : splitter.routes(depot, order)) {
            total += route.distance();
            load += route.load();
        }
        assertEquals(splitter.evaluate(depot, order).cost(), total, EPSILON);
        assertEquals(4, load);
    }

    @Test
    void fleetLimitIsAppliedInsideTheSplitWhenPossible() {
        // Con capacidad 3 y un solo vehiculo no cabe todo: la mejor opcion factible no existe, asi
        // que se penaliza. Con dos vehiculos, cabe usando {1,2,3}{4} o {1}{2,3,4}; el DP acotado
        // elige la mas barata (6 + 8 = 14 frente a 2 + 8 = 10 -> {1}{2,3,4} = 10).
        RoutingProblem problem = lineProblem(3, 2, Double.MAX_VALUE);
        RouteSplitter splitter = new RouteSplitter(problem);
        Depot depot = problem.depot(0);
        List<Integer> order = List.of(0, 1, 2, 3);

        RouteSplitter.Split split = splitter.evaluate(depot, order);
        assertEquals(2, split.routeCount());
        assertEquals(0.0, RouteSplitter.fleetPenalty(split.routeCount(), depot.fleet()), EPSILON);
        assertTrue(splitter.isFeasible(depot, order));

        RoutingProblem tight = lineProblem(3, 1, Double.MAX_VALUE);
        RouteSplitter tightSplitter = new RouteSplitter(tight);
        RouteSplitter.Split tightSplit = tightSplitter.evaluate(tight.depot(0), order);
        assertTrue(tightSplit.routeCount() > 1);
        assertEquals(RouteSplitter.FLEET_PENALTY * (tightSplit.routeCount() - 1),
                RouteSplitter.fleetPenalty(tightSplit.routeCount(), tight.depot(0).fleet()), EPSILON);
        assertFalse(tightSplitter.isFeasible(tight.depot(0), order));
    }

    @Test
    void durationLimitIsRespectedAndAnUnservableCustomerIsTolerated() {
        // El cliente mas lejano esta a 4: ir y volver son 8. Con limite 8 cabe todo en una ruta.
        RoutingProblem problem = lineProblem(10, Depot.UNLIMITED, 8.0);
        RouteSplitter splitter = new RouteSplitter(problem);
        Depot depot = problem.depot(0);
        List<Integer> order = List.of(0, 1, 2, 3);

        RouteSplitter.Split split = splitter.evaluate(depot, order);
        assertEquals(1, split.routeCount());
        assertEquals(8.0, split.cost(), EPSILON);
        assertTrue(splitter.isFeasible(depot, order));

        // Con limite 7 ese cliente no cabe ni solo: se tolera con penalizacion para que la secuencia
        // siga teniendo troceado, pero la solucion no es factible.
        RoutingProblem tight = lineProblem(10, Depot.UNLIMITED, 7.0);
        RouteSplitter tightSplitter = new RouteSplitter(tight);
        RouteSplitter.Split tightSplit = tightSplitter.evaluate(tight.depot(0), order);
        assertTrue(tightSplit.cost() >= 1000.0, "la penalizacion por duracion debe aparecer en el coste");
        assertFalse(tightSplitter.isFeasible(tight.depot(0), order));
        for (Route route : tightSplitter.routes(tight.depot(0), order)) {
            if (!route.customers().equals(List.of(3))) {
                assertTrue(route.duration() <= 7.0 + EPSILON, "ruta demasiado larga: " + route);
            }
        }
    }

    @Test
    void insertionAndRemovalDeltasAreConsistent() {
        RoutingProblem problem = lineProblem(10, Depot.UNLIMITED, Double.MAX_VALUE);
        RouteSplitter splitter = new RouteSplitter(problem);
        Depot depot = problem.depot(0);
        List<Integer> order = new ArrayList<>(List.of(0, 2, 3));

        double insertion = splitter.insertionCost(depot, order, 1, 1);
        order.add(1, 1);
        double removal = splitter.removalGain(depot, order, 1);
        assertEquals(insertion, removal, EPSILON);
        assertEquals(0.0, insertion, EPSILON, "insertar en linea entre 0 y 2 no anade distancia");
        assertEquals(1, splitter.bestPosition(depot, List.of(0, 2, 3), 1));
    }

    private static RoutingProblem lineProblem(int capacity, int fleet, double durationLimit) {
        int nodes = 5;
        double[][] matrix = new double[nodes][nodes];
        for (int i = 0; i < nodes; i++) {
            for (int j = 0; j < nodes; j++) {
                matrix[i][j] = Math.abs(i - j);
            }
        }
        Depot depot = new Depot(0, "D", 0, capacity, fleet, durationLimit);
        List<Customer> customers = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            customers.add(new Customer(i, "C" + (i + 1), i + 1, 1, 0.0));
        }
        return new RoutingProblem(List.of(depot), customers, matrix);
    }
}
