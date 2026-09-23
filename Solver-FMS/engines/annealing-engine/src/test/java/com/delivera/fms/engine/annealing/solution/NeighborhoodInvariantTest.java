package com.delivera.fms.engine.annealing.solution;

import com.delivera.fms.engine.core.model.Customer;
import com.delivera.fms.engine.core.model.Depot;
import com.delivera.fms.engine.core.model.RoutingProblem;
import com.delivera.fms.engine.core.split.RouteSplitter;
import org.junit.jupiter.api.Test;
import org.uma.jmetal.util.pseudorandom.PseudoRandomGenerator;
import org.uma.jmetal.util.pseudorandom.impl.JavaRandomGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lo que el recocido da por hecho de su representacion y que ningun movimiento puede romper:
 * cada cliente esta exactamente en un deposito, la asignacion cacheada coincide con las
 * secuencias, el coste mantenido por diferencias es el mismo que el recalculado desde cero y
 * deshacer un movimiento devuelve exactamente al estado anterior.
 */
class NeighborhoodInvariantTest {

    private static final double EPSILON = 1e-7;
    private static final int MOVES = 5000;

    @Test
    void movesAndUndosPreserveTheRepresentation() {
        RoutingProblem problem = randomProblem(new Random(7), 3, 60);
        RouteSplitter splitter = new RouteSplitter(problem);
        PseudoRandomGenerator random = new JavaRandomGenerator(11);
        Neighborhood neighborhood = new Neighborhood(problem, splitter, random, 0.4, 1.5);

        AnnealingSolution solution = AnnealingSolution.of(problem, splitter, nearestDepotOrders(problem));
        assertConsistent(solution);

        int applied = 0;
        for (int i = 0; i < MOVES; i++) {
            AnnealingSolution before = solution.copy();
            Neighborhood.Move move = neighborhood.propose(solution);
            if (move == null) {
                continue;
            }
            applied++;
            assertConsistent(solution);
            assertEquals(solution.cost() - before.cost(), move.delta(), EPSILON, "delta informado");

            // La mitad se deshacen: el estado tiene que volver a ser exactamente el de antes.
            if (i % 2 == 0) {
                neighborhood.undo(solution, move);
                assertSame(before, solution);
            }
        }
        assertTrue(applied > MOVES / 2, "casi todos los movimientos deben ser aplicables");
    }

    @Test
    void depotBackupRestoresSequenceCostAndAssignment() {
        RoutingProblem problem = randomProblem(new Random(3), 2, 20);
        RouteSplitter splitter = new RouteSplitter(problem);
        AnnealingSolution solution = AnnealingSolution.of(problem, splitter, nearestDepotOrders(problem));
        AnnealingSolution before = solution.copy();

        int customer = solution.order(0).get(0);
        AnnealingSolution.DepotBackup source = solution.backup(0);
        AnnealingSolution.DepotBackup target = solution.backup(1);
        solution.order(0).remove(Integer.valueOf(customer));
        solution.order(1).add(customer);
        solution.evaluateDepot(0);
        solution.evaluateDepot(1);
        assertEquals(1, solution.depotOf(customer));
        assertConsistent(solution);

        solution.restore(target);
        solution.restore(source);
        assertSame(before, solution);
    }

    private static void assertConsistent(AnnealingSolution solution) {
        RoutingProblem problem = solution.problem();
        int[] seen = new int[problem.customerCount()];
        double recomputed = 0.0;
        for (Depot depot : problem.depots()) {
            List<Integer> order = solution.order(depot.index());
            for (int customer : order) {
                seen[customer]++;
                assertEquals(depot.index(), solution.depotOf(customer), "depotOf del cliente " + customer);
            }
            double depotCost = new RouteSplitter(problem).penalizedCost(depot, order);
            assertEquals(depotCost, solution.depotCost(depot.index()), EPSILON, "coste cacheado del deposito " + depot.index());
            recomputed += depotCost;
        }
        for (int customer = 0; customer < seen.length; customer++) {
            assertEquals(1, seen[customer], "el cliente " + customer + " debe aparecer exactamente una vez");
        }
        assertEquals(recomputed, solution.cost(), EPSILON, "coste total mantenido por diferencias");
    }

    private static void assertSame(AnnealingSolution expected, AnnealingSolution actual) {
        assertEquals(expected.orders(), actual.orders(), "secuencias tras deshacer");
        assertEquals(expected.cost(), actual.cost(), EPSILON, "coste tras deshacer");
        for (int customer = 0; customer < expected.problem().customerCount(); customer++) {
            assertEquals(expected.depotOf(customer), actual.depotOf(customer));
        }
    }

    private static List<List<Integer>> nearestDepotOrders(RoutingProblem problem) {
        List<List<Integer>> orders = new ArrayList<>();
        for (int depot = 0; depot < problem.depotCount(); depot++) {
            orders.add(new ArrayList<>());
        }
        for (Customer customer : problem.customers()) {
            int nearest = 0;
            for (Depot depot : problem.depots()) {
                if (problem.distance(depot, customer.index()) < problem.distance(problem.depot(nearest), customer.index())) {
                    nearest = depot.index();
                }
            }
            orders.get(nearest).add(customer.index());
        }
        return orders;
    }

    /** Depositos y clientes al azar en un cuadrado de 100x100, con flota apretada. */
    static RoutingProblem randomProblem(Random random, int depotCount, int customerCount) {
        int nodes = depotCount + customerCount;
        double[] x = new double[nodes];
        double[] y = new double[nodes];
        for (int i = 0; i < nodes; i++) {
            x[i] = random.nextDouble() * 100;
            y[i] = random.nextDouble() * 100;
        }
        double[][] matrix = new double[nodes][nodes];
        for (int i = 0; i < nodes; i++) {
            for (int j = 0; j < nodes; j++) {
                matrix[i][j] = Math.hypot(x[i] - x[j], y[i] - y[j]);
            }
        }

        List<Depot> depots = new ArrayList<>();
        for (int i = 0; i < depotCount; i++) {
            depots.add(new Depot(i, "D" + i, i, 50, 4, 250.0));
        }
        List<Customer> customers = new ArrayList<>();
        for (int i = 0; i < customerCount; i++) {
            customers.add(new Customer(i, "C" + i, depotCount + i, 1 + random.nextInt(15), random.nextInt(5)));
        }
        return new RoutingProblem(depots, customers, matrix);
    }
}
