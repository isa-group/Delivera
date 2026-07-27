package com.delivera.fms.engine.genetic.operator.search;

import com.delivera.fms.engine.genetic.dto.CustomerDto;
import com.delivera.fms.engine.genetic.dto.DepotDto;
import com.delivera.fms.engine.genetic.scheduler.PermutationCodec;
import com.delivera.fms.engine.genetic.scheduler.RouteSplitter;
import org.uma.jmetal.solution.permutationsolution.PermutationSolution;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reasigna clientes entre depositos. Hace dos cosas distintas y las mantiene separadas:
 *
 * <ol>
 *   <li><b>Reparar</b>: si un deposito necesita mas rutas que vehiculos tiene, se le saca carga
 *       aunque cueste distancia. Como criterio de mejora de coste puede no existir ningun
 *       movimiento individual que compense, la reparacion se acepta sin condicion de coste.
 *   <li><b>Mejorar</b>: reubicar clientes frontera cuando reduce el coste real de los dos
 *       depositos implicados.
 * </ol>
 *
 * <p>Los deltas se miden dentro de la secuencia de cada deposito y todo movimiento se confirma con
 * el troceado real, de modo que nunca se acepta por una arista que no existe en ninguna ruta.
 */
public class InterDepotLocalSearch {

    private static final double BORDER_RATIO = 1.3;
    private static final int MAX_ITERATIONS = 10;
    private static final int MAX_REPAIR_MOVES = 20;
    private static final int MAX_CANDIDATES = 25;
    private static final double EPSILON = 1e-10;

    private final List<CustomerDto> customers;
    private final List<DepotDto> depots;
    private final double[][] distanceMatrix;
    private final RouteSplitter splitter;

    public InterDepotLocalSearch(List<CustomerDto> customers,
                                  List<DepotDto> depots,
                                  double[][] distanceMatrix,
                                  RouteSplitter splitter) {
        this.customers = customers;
        this.depots = depots;
        this.distanceMatrix = distanceMatrix;
        this.splitter = splitter;
    }

    public boolean optimize(PermutationSolution<Integer> solution) {
        if (depots.size() < 2) {
            return false;
        }

        Map<Integer, DepotDto> depotMap = PermutationCodec.depotMap(solution);
        if (depotMap == null) {
            return false;
        }

        Map<DepotDto, List<Integer>> depotOrder = PermutationCodec.depotOrder(solution, depots, depotMap);
        Map<DepotDto, Double> costs = new HashMap<>();
        Map<DepotDto, Integer> routeCounts = new HashMap<>();
        for (var entry : depotOrder.entrySet()) {
            measure(entry.getKey(), entry.getValue(), costs, routeCounts);
        }

        boolean changed = repairFleet(depotOrder, depotMap, costs, routeCounts);

        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            if (!applyBestMove(depotOrder, depotMap, costs, routeCounts)) {
                break;
            }
            changed = true;
        }

        if (changed) {
            PermutationCodec.writeBackContiguous(solution, depotOrder);
        }
        return changed;
    }

    /** Vacia los depositos que superan su flota, al menor coste posible pero sin exigir mejora. */
    private boolean repairFleet(Map<DepotDto, List<Integer>> depotOrder,
                                 Map<Integer, DepotDto> depotMap,
                                 Map<DepotDto, Double> costs,
                                 Map<DepotDto, Integer> routeCounts) {
        boolean changed = false;

        for (int move = 0; move < MAX_REPAIR_MOVES; move++) {
            DepotDto source = overloadedDepot(routeCounts);
            if (source == null) {
                break;
            }

            Move relief = cheapestRelief(source, depotOrder, routeCounts);
            if (relief == null) {
                break;
            }

            List<Integer> from = depotOrder.get(source);
            List<Integer> to = depotOrder.get(relief.target());
            from.remove(Integer.valueOf(relief.customer()));
            to.add(relief.position(), relief.customer());
            depotMap.put(relief.customer(), relief.target());

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
    private Move cheapestRelief(DepotDto source,
                                 Map<DepotDto, List<Integer>> depotOrder,
                                 Map<DepotDto, Integer> routeCounts) {
        List<Integer> order = depotOrder.get(source);
        Map<DepotDto, Double> scratchCost = new HashMap<>();
        Map<DepotDto, Integer> scratchRoutes = new HashMap<>();

        Move best = null;
        double bestCost = Double.MAX_VALUE;

        for (int position = 0; position < order.size(); position++) {
            int customer = order.get(position);

            List<Integer> reduced = new ArrayList<>(order);
            reduced.remove(position);
            measure(source, reduced, scratchCost, scratchRoutes);
            double sourceCost = scratchCost.get(source);

            for (DepotDto target : depots) {
                if (target.equals(source)) {
                    continue;
                }
                int fleet = splitter.fleet(target);
                if (routeCounts.getOrDefault(target, 0) > fleet) {
                    continue;
                }

                List<Integer> targetOrder = depotOrder.get(target);
                for (int at = 0; at <= targetOrder.size(); at++) {
                    targetOrder.add(at, customer);
                    measure(target, targetOrder, scratchCost, scratchRoutes);
                    targetOrder.remove(at);

                    if (scratchRoutes.get(target) > fleet) {
                        continue;
                    }
                    double total = sourceCost + scratchCost.get(target);
                    if (total < bestCost) {
                        bestCost = total;
                        best = new Move(customer, source, target, at, total);
                    }
                }
            }
        }

        return best;
    }

    private DepotDto overloadedDepot(Map<DepotDto, Integer> routeCounts) {
        DepotDto worst = null;
        int worstExcess = 0;

        for (DepotDto depot : depots) {
            int excess = routeCounts.getOrDefault(depot, 0) - splitter.fleet(depot);
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
    private boolean applyBestMove(Map<DepotDto, List<Integer>> depotOrder,
                                   Map<Integer, DepotDto> depotMap,
                                   Map<DepotDto, Double> costs,
                                   Map<DepotDto, Integer> routeCounts) {
        List<Move> candidates = new ArrayList<>();

        for (var entry : depotOrder.entrySet()) {
            DepotDto source = entry.getKey();
            List<Integer> order = entry.getValue();

            for (int position = 0; position < order.size(); position++) {
                int customer = order.get(position);
                if (!isBorderCustomer(customer, source)) {
                    continue;
                }
                double gain = splitter.removalGain(source, order, position);

                for (DepotDto target : depots) {
                    if (target.equals(source)) {
                        continue;
                    }
                    List<Integer> targetOrder = depotOrder.get(target);
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
            if (tryApply(candidates.get(i), depotOrder, depotMap, costs, routeCounts)) {
                return true;
            }
        }
        return false;
    }

    /** Confirma el movimiento con el troceado real de ambos depositos y lo deshace si no mejora. */
    private boolean tryApply(Move move,
                              Map<DepotDto, List<Integer>> depotOrder,
                              Map<Integer, DepotDto> depotMap,
                              Map<DepotDto, Double> costs,
                              Map<DepotDto, Integer> routeCounts) {
        List<Integer> source = depotOrder.get(move.source());
        List<Integer> target = depotOrder.get(move.target());
        int sourcePosition = source.indexOf(move.customer());
        if (sourcePosition < 0) {
            return false;
        }

        double previousCost = costs.get(move.source()) + costs.get(move.target());
        source.remove(sourcePosition);
        target.add(Math.min(move.position(), target.size()), move.customer());

        Map<DepotDto, Double> newCosts = new HashMap<>();
        Map<DepotDto, Integer> newRouteCounts = new HashMap<>();
        measure(move.source(), source, newCosts, newRouteCounts);
        measure(move.target(), target, newCosts, newRouteCounts);

        if (newCosts.get(move.source()) + newCosts.get(move.target()) >= previousCost - EPSILON) {
            target.remove(Integer.valueOf(move.customer()));
            source.add(sourcePosition, move.customer());
            return false;
        }

        costs.putAll(newCosts);
        routeCounts.putAll(newRouteCounts);
        depotMap.put(move.customer(), move.target());
        return true;
    }

    private boolean isBorderCustomer(int customer, DepotDto assigned) {
        double distanceToAssigned = distanceMatrix[assigned.matrixIndex()][matrixIndex(customer)];
        double nearestOther = Double.MAX_VALUE;
        for (DepotDto depot : depots) {
            if (depot.equals(assigned)) {
                continue;
            }
            nearestOther = Math.min(nearestOther, distanceMatrix[depot.matrixIndex()][matrixIndex(customer)]);
        }
        return nearestOther / (distanceToAssigned + 1e-10) < BORDER_RATIO;
    }

    /** Coste penalizado y numero de rutas del deposito, la misma cuenta que hace la funcion objetivo. */
    private void measure(DepotDto depot, List<Integer> order,
                         Map<DepotDto, Double> costs, Map<DepotDto, Integer> routeCounts) {
        RouteSplitter.Split split = splitter.evaluate(depot, order);
        costs.put(depot, split.cost()
                + RouteSplitter.fleetPenalty(split.routeCount(), splitter.fleet(depot)));
        routeCounts.put(depot, split.routeCount());
    }

    private int matrixIndex(int customer) {
        return customers.get(customer).matrixIndex();
    }

    private record Move(int customer, DepotDto source, DepotDto target, int position, double delta) {
    }
}
