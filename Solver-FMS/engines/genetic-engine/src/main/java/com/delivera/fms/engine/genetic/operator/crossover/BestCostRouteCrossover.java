package com.delivera.fms.engine.genetic.operator.crossover;

import com.delivera.fms.engine.genetic.dto.DepotDto;
import com.delivera.fms.engine.genetic.scheduler.PermutationCodec;
import com.delivera.fms.engine.genetic.scheduler.RouteSplitter;
import org.uma.jmetal.operator.crossover.CrossoverOperator;
import org.uma.jmetal.solution.permutationsolution.PermutationSolution;
import org.uma.jmetal.solution.permutationsolution.impl.IntegerPermutationSolution;
import org.uma.jmetal.util.pseudorandom.JMetalRandom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Best Cost Route Crossover.
 *
 * Extrae una ruta del primer padre, la elimina del segundo y reinserta cada cliente en
 * la posicion mas barata dentro de la secuencia del deposito que le asigna el segundo padre. El
 * hijo hereda la asignacion cliente-deposito del segundo padre, que es la coherente con la
 * permutacion sobre la que se construye.
 */
public class BestCostRouteCrossover implements CrossoverOperator<PermutationSolution<Integer>> {

    private final double probability;
    private final JMetalRandom random;
    private final List<DepotDto> depots;
    private final RouteSplitter splitter;

    public BestCostRouteCrossover(double probability,
                                   List<DepotDto> depots,
                                   RouteSplitter splitter) {
        this.probability = probability;
        this.random = JMetalRandom.getInstance();
        this.depots = depots;
        this.splitter = splitter;
    }

    @Override
    public double crossoverProbability() {
        return probability;
    }

    @Override
    public int numberOfRequiredParents() {
        return 2;
    }

    @Override
    public int numberOfGeneratedChildren() {
        return 2;
    }

    @Override
    public List<PermutationSolution<Integer>> execute(List<PermutationSolution<Integer>> parents) {
        if (parents.size() != 2) {
            throw new IllegalArgumentException("BCRC requires exactly 2 parents");
        }

        List<PermutationSolution<Integer>> offspring = new ArrayList<>(2);

        if (random.nextDouble() < probability) {
            offspring.add(performCrossover(parents.get(0), parents.get(1)));
            offspring.add(performCrossover(parents.get(1), parents.get(0)));
        } else {
            offspring.add(copySolution(parents.get(0)));
            offspring.add(copySolution(parents.get(1)));
        }

        return offspring;
    }

    private PermutationSolution<Integer> performCrossover(PermutationSolution<Integer> donor,
                                                           PermutationSolution<Integer> receiver) {
        PermutationSolution<Integer> child = copySolution(receiver);

        List<Integer> extracted = extractRandomRoute(donor);
        if (extracted.isEmpty()) {
            return child;
        }

        Map<Integer, DepotDto> childDepotMap = PermutationCodec.depotMap(child);
        Set<Integer> removed = new HashSet<>(extracted);

        Map<DepotDto, List<Integer>> depotOrder = PermutationCodec.depotOrder(child, depots, childDepotMap);
        for (List<Integer> order : depotOrder.values()) {
            order.removeIf(removed::contains);
        }

        for (int customer : extracted) {
            DepotDto depot = childDepotMap.get(customer);
            if (depot == null) {
                continue;
            }
            List<Integer> order = depotOrder.get(depot);
            order.add(splitter.bestPosition(depot, order, customer), customer);
        }

        PermutationCodec.writeBack(child, depotOrder, childDepotMap);
        return child;
    }

    /** Una ruta real del donante, no todos los clientes de un deposito. */
    private List<Integer> extractRandomRoute(PermutationSolution<Integer> donor) {
        Map<Integer, DepotDto> donorDepotMap = PermutationCodec.depotMap(donor);
        if (donorDepotMap == null) {
            return List.of();
        }

        Map<DepotDto, List<Integer>> depotOrder = PermutationCodec.depotOrder(donor, depots, donorDepotMap);
        List<DepotDto> nonEmpty = depotOrder.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .toList();
        if (nonEmpty.isEmpty()) {
            return List.of();
        }

        DepotDto depot = nonEmpty.get(random.nextInt(0, nonEmpty.size() - 1));
        List<List<Integer>> routes = splitter.split(depot, depotOrder.get(depot));
        if (routes.isEmpty()) {
            return List.of();
        }

        return routes.get(random.nextInt(0, routes.size() - 1));
    }

    private PermutationSolution<Integer> copySolution(PermutationSolution<Integer> source) {
        IntegerPermutationSolution copy = new IntegerPermutationSolution(
                source.variables().size(), source.objectives().length, 0);
        for (int i = 0; i < source.variables().size(); i++) {
            copy.variables().set(i, source.variables().get(i));
        }
        for (int i = 0; i < source.objectives().length; i++) {
            copy.objectives()[i] = source.objectives()[i];
        }

        Map<Integer, DepotDto> sourceMap = PermutationCodec.depotMap(source);
        if (sourceMap != null) {
            copy.attributes().put(PermutationCodec.DEPOT_MAP, new HashMap<>(sourceMap));
        }

        return copy;
    }
}
