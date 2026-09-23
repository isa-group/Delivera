package com.delivera.fms.engine.core.search;

import com.delivera.fms.engine.core.model.Depot;
import com.delivera.fms.engine.core.model.RoutingProblem;
import com.delivera.fms.engine.core.split.RouteSplitter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Reasigna clientes entre depositos. Hace dos cosas distintas y las mantiene separadas:
 *
 * <ul>
 *   <li><b>Reparar</b>: si un deposito necesita mas rutas que vehiculos tiene, se le saca carga
 *       aunque cueste distancia. Como criterio de mejora de coste puede no existir ningun
 *       movimiento individual que compense, la reparacion se acepta sin condicion de coste.</li>
 *   <li><b>Mejorar</b>: reubicar clientes frontera cuando reduce el coste real de los dos
 *       depositos implicados.</li>
 * </ul>
 *
 * Los deltas se miden dentro de la secuencia de cada deposito y todo movimiento se confirma con
 * el troceado real, de modo que nunca se acepta por una arista que no existe en ninguna ruta.
 *
 * Trabaja sobre las secuencias por deposito, indexadas por {@link Depot#index()}. La asignacion
 * cliente-deposito no se recibe aparte: es la que esas secuencias describen.
 *
 * La reparacion confirma cada insercion candidata con un troceado del destino. Barrer todas las
 * posiciones es exacto pero cuadratico en el tamano del deposito, y en instancias grandes una
 * pasada cuesta segundos; {@code insertionCandidates} permite limitar el barrido a las posiciones
 * mas baratas por distancia, a cambio de poder pasar por alto la que menos rutas anade.
 */
public class DepotRebalancer {

    private static final double BORDER_RATIO = 1.3;
    private static final int MAX_ITERATIONS = 10;
    private static final int MAX_REPAIR_MOVES = 20;
    private static final int MAX_CANDIDATES = 25;
    private static final double EPSILON = 1e-10;

    /** Barrer todas las posiciones de insercion al reparar. */
    public static final int ALL_POSITIONS = Integer.MAX_VALUE;

    private final RoutingProblem problem;
    private final List<Depot> depots;
    private final double[][] distanceMatrix;
    private final RouteSplitter splitter;
    private final int insertionCandidates;

    public DepotRebalancer(RoutingProblem problem, RouteSplitter splitter) {
        this(problem, splitter, ALL_POSITIONS);
    }

    /**
     * @param insertionCandidates posiciones de insercion, las mas baratas por distancia, que se
     *                            confirman con troceado al reparar; {@link #ALL_POSITIONS} para el
     *                            barrido exacto
     */
    public DepotRebalancer(RoutingProblem problem, RouteSplitter splitter, int insertionCandidates) {
        if (insertionCandidates < 1) {
            throw new IllegalArgumentException("insertionCandidates must be positive");
        }
        this.problem = problem;
        this.depots = problem.depots();
        this.distanceMatrix = problem.distanceMatrix();
        this.splitter = splitter;
        this.insertionCandidates = insertionCandidates;
    }

    /**
     * Repara la flota y despues mejora el reparto, modificando las secuencias en el sitio.
     *
     * @param orders secuencia de clientes de cada deposito, en el orden de {@code problem.depots()}
     * @return si alguna secuencia ha cambiado
     */
    public boolean rebalance(List<List<Integer>> orders) {
        return rebalance(orders, Long.MAX_VALUE);
    }

    /**
     * Como {@link #rebalance(List)}, pero se detiene en cuanto el reloj pasa de {@code deadline}
     * (en milisegundos de {@link System#currentTimeMillis()}), dejando las secuencias en un
     * estado coherente con los movimientos aplicados hasta entonces.
     *
     * En instancias grandes una pasada completa cuesta segundos, y un motor con presupuesto de
     * tiempo no puede permitirse que una fase auxiliar se lo coma entero.
     */
    public boolean rebalance(List<List<Integer>> orders, long deadline) {
        if (depots.size() < 2) {
            return false;
        }

        double[] costs = new double[depots.size()];
        int[] routeCounts = new int[depots.size()];
        for (Depot depot : depots) {
            measure(depot, orders.get(depot.index()), costs, routeCounts);
        }

        boolean changed = repairFleet(orders, costs, routeCounts, deadline);

        for (int iteration = 0; iteration < MAX_ITERATIONS && !expired(deadline); iteration++) {
            if (!applyBestMove(orders, costs, routeCounts)) {
                break;
            }
            changed = true;
        }

        return changed;
    }

    private static boolean expired(long deadline) {
        return deadline != Long.MAX_VALUE && System.currentTimeMillis() >= deadline;
    }

    // Vacia los depositos que superan su flota, al menor coste posible pero sin exigir mejora.
    private boolean repairFleet(List<List<Integer>> orders, double[] costs, int[] routeCounts, long deadline) {
        boolean changed = false;

        for (int move = 0; move < MAX_REPAIR_MOVES && !expired(deadline); move++) {
            Depot source = overloadedDepot(routeCounts);
            if (source == null) {
                break;
            }

            Move relief = cheapestRelief(source, orders, routeCounts, deadline);
            if (relief == null) {
                break;
            }

            List<Integer> from = orders.get(source.index());
            List<Integer> to = orders.get(relief.target().index());
            from.remove(Integer.valueOf(relief.customer()));
            to.add(relief.position(), relief.customer());

            measure(source, from, costs, routeCounts);
            measure(relief.target(), to, costs, routeCounts);
            changed = true;
        }

        return changed;
    }

    /**
     * Cliente, deposito destino y posicion que descargan el deposito al menor coste, entre los que
     * dejan al destino dentro de su flota. Se recorren todas las posiciones del destino: el numero
     * de rutas depende de donde se corte la secuencia, no solo de la carga, asi que la posicion mas
     * barata en distancia puede ser justo la que le anade una ruta.
     */
    private Move cheapestRelief(Depot source, List<List<Integer>> orders, int[] routeCounts, long deadline) {
        List<Integer> order = orders.get(source.index());
        double[] scratchCost = new double[depots.size()];
        int[] scratchRoutes = new int[depots.size()];

        Move best = null;
        double bestCost = Double.MAX_VALUE;

        // Si el plazo vence a mitad de barrido se devuelve el mejor visto hasta entonces: un
        // alivio parcial vale mas que ninguno.
        for (int position = 0; position < order.size() && !expired(deadline); position++) {
            int customer = order.get(position);

            List<Integer> reduced = new ArrayList<>(order);
            reduced.remove(position);
            measure(source, reduced, scratchCost, scratchRoutes);
            double sourceCost = scratchCost[source.index()];

            for (Depot target : depots) {
                if (target.equals(source)) {
                    continue;
                }
                int fleet = target.fleet();
                if (routeCounts[target.index()] > fleet) {
                    continue;
                }

                List<Integer> targetOrder = orders.get(target.index());
                for (int at : insertionPositions(target, targetOrder, customer)) {
                    targetOrder.add(at, customer);
                    measure(target, targetOrder, scratchCost, scratchRoutes);
                    targetOrder.remove(at);

                    if (scratchRoutes[target.index()] > fleet) {
                        continue;
                    }
                    double total = sourceCost + scratchCost[target.index()];
                    if (total < bestCost) {
                        bestCost = total;
                        best = new Move(customer, source, target, at, total);
                    }
                }
            }
        }

        return best;
    }

    /**
     * Posiciones de insercion a confirmar con troceado: todas en orden, o las
     * {@code insertionCandidates} mas baratas por distancia.
     */
    private int[] insertionPositions(Depot target, List<Integer> order, int customer) {
        int positions = order.size() + 1;
        if (insertionCandidates >= positions) {
            int[] all = new int[positions];
            for (int at = 0; at < positions; at++) {
                all[at] = at;
            }
            return all;
        }

        Integer[] ranked = new Integer[positions];
        double[] costs = new double[positions];
        for (int at = 0; at < positions; at++) {
            ranked[at] = at;
            costs[at] = splitter.insertionCost(target, order, at, customer);
        }
        Arrays.sort(ranked, Comparator.comparingDouble(at -> costs[at]));

        int[] best = new int[insertionCandidates];
        for (int i = 0; i < insertionCandidates; i++) {
            best[i] = ranked[i];
        }
        return best;
    }

    private Depot overloadedDepot(int[] routeCounts) {
        Depot worst = null;
        int worstExcess = 0;

        for (Depot depot : depots) {
            if (!depot.hasFleetLimit()) {
                continue;
            }
            int excess = routeCounts[depot.index()] - depot.fleet();
            if (excess > worstExcess) {
                worstExcess = excess;
                worst = depot;
            }
        }

        return worst;
    }

    /**
     * Aplica la mejor reubicacion de un cliente frontera que resista la comprobacion exacta. El
     * delta estimado solo ordena candidatos, por eso se verifican varios en lugar de rendirse
     * cuando el primero no mejora de verdad.
     */
    private boolean applyBestMove(List<List<Integer>> orders, double[] costs, int[] routeCounts) {
        List<Move> candidates = new ArrayList<>();

        for (Depot source : depots) {
            List<Integer> order = orders.get(source.index());

            for (int position = 0; position < order.size(); position++) {
                int customer = order.get(position);
                if (!isBorderCustomer(customer, source)) {
                    continue;
                }
                double gain = splitter.removalGain(source, order, position);

                for (Depot target : depots) {
                    if (target.equals(source)) {
                        continue;
                    }
                    List<Integer> targetOrder = orders.get(target.index());
                    int insertAt = splitter.bestPosition(target, targetOrder, customer);
                    double delta = splitter.insertionCost(target, targetOrder, insertAt, customer) - gain;

                    if (delta < -EPSILON) {
                        candidates.add(new Move(customer, source, target, insertAt, delta));
                    }
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(Move::delta));

        for (int i = 0; i < Math.min(MAX_CANDIDATES, candidates.size()); i++) {
            if (tryApply(candidates.get(i), orders, costs, routeCounts)) {
                return true;
            }
        }
        return false;
    }

    // Confirma el movimiento con el troceado real de ambos depositos y lo deshace si no mejora
    private boolean tryApply(Move move, List<List<Integer>> orders, double[] costs, int[] routeCounts) {
        List<Integer> source = orders.get(move.source().index());
        List<Integer> target = orders.get(move.target().index());
        int sourcePosition = source.indexOf(move.customer());
        if (sourcePosition < 0) {
            return false;
        }

        double previousCost = costs[move.source().index()] + costs[move.target().index()];
        source.remove(sourcePosition);
        target.add(Math.min(move.position(), target.size()), move.customer());

        double[] newCosts = new double[depots.size()];
        int[] newRouteCounts = new int[depots.size()];
        measure(move.source(), source, newCosts, newRouteCounts);
        measure(move.target(), target, newCosts, newRouteCounts);

        if (newCosts[move.source().index()] + newCosts[move.target().index()] >= previousCost - EPSILON) {
            target.remove(Integer.valueOf(move.customer()));
            source.add(sourcePosition, move.customer());
            return false;
        }

        for (Depot depot : List.of(move.source(), move.target())) {
            costs[depot.index()] = newCosts[depot.index()];
            routeCounts[depot.index()] = newRouteCounts[depot.index()];
        }
        return true;
    }

    private boolean isBorderCustomer(int customer, Depot assigned) {
        double distanceToAssigned = distanceMatrix[assigned.matrixIndex()][matrixIndex(customer)];
        double nearestOther = Double.MAX_VALUE;
        for (Depot depot : depots) {
            if (depot.equals(assigned)) {
                continue;
            }
            nearestOther = Math.min(nearestOther, distanceMatrix[depot.matrixIndex()][matrixIndex(customer)]);
        }
        return nearestOther / (distanceToAssigned + 1e-10) < BORDER_RATIO;
    }

    // Coste penalizado y numero de rutas del deposito, la misma cuenta que hace la funcion objetivo
    private void measure(Depot depot, List<Integer> order, double[] costs, int[] routeCounts) {
        RouteSplitter.Split split = splitter.evaluate(depot, order);
        costs[depot.index()] = split.cost() + RouteSplitter.fleetPenalty(split.routeCount(), depot.fleet());
        routeCounts[depot.index()] = split.routeCount();
    }

    private int matrixIndex(int customer) {
        return problem.customer(customer).matrixIndex();
    }

    private record Move(int customer, Depot source, Depot target, int position, double delta) {
    }
}
