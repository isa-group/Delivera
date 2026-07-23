package com.delivera.fms.engine.genetic.operator.crossover;

import com.delivera.fms.engine.genetic.dto.CustomerDto;
import com.delivera.fms.engine.genetic.dto.DepotDto;
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

public class BestCostRouteCrossover implements CrossoverOperator<PermutationSolution<Integer>> {

    private final double probability;
    private final JMetalRandom random;
    private final List<CustomerDto> customers;
    private final List<DepotDto> depots;
    private final double[][] distanceMatrix;

    public BestCostRouteCrossover(double probability,
                                   List<CustomerDto> customers,
                                   List<DepotDto> depots,
                                   double[][] distanceMatrix) {
        this.probability = probability;
        this.random = JMetalRandom.getInstance();
        this.customers = customers;
        this.depots = depots;
        this.distanceMatrix = distanceMatrix;
    }

    @Override
    public double crossoverProbability() {
        return probability;
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

    private PermutationSolution<Integer> performCrossover(PermutationSolution<Integer> parent1,
                                                           PermutationSolution<Integer> parent2) {
        @SuppressWarnings("unchecked")
        Map<Integer, DepotDto> map1 = (Map<Integer, DepotDto>) parent1.attributes().get("depotMap");
        @SuppressWarnings("unchecked")
        Map<Integer, DepotDto> map2 = (Map<Integer, DepotDto>) parent2.attributes().get("depotMap");

        Map<DepotDto, List<Integer>> routes1 = buildRoutes(parent1, map1);

        List<Integer> extractedCustomers = extractRandomRoute(routes1);

        PermutationSolution<Integer> child = copySolution(parent2);
        reinsertCustomers(child, extractedCustomers, map2);

        return child;
    }

    private Map<DepotDto, List<Integer>> buildRoutes(PermutationSolution<Integer> solution,
                                                      Map<Integer, DepotDto> localDepotMap) {
        Map<DepotDto, List<Integer>> routes = new HashMap<>();
        for (DepotDto d : depots) {
            routes.put(d, new ArrayList<>());
        }
        for (int i = 0; i < solution.variables().size(); i++) {
            int cIdx = solution.variables().get(i);
            DepotDto depot = localDepotMap.get(cIdx);
            if (depot != null) {
                routes.get(depot).add(cIdx);
            }
        }
        return routes;
    }

    @Override
    public int numberOfRequiredParents() {
        return 2;
    }

    @Override
    public int numberOfGeneratedChildren() {
        return 2;
    }

    private List<Integer> extractRandomRoute(Map<DepotDto, List<Integer>> routes) {
        List<DepotDto> nonEmptyDepots = routes.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .toList();

        if (nonEmptyDepots.isEmpty()) {
            return new ArrayList<>();
        }

        DepotDto selectedDepot = nonEmptyDepots.get(random.nextInt(0, nonEmptyDepots.size() - 1));
        return new ArrayList<>(routes.get(selectedDepot));
    }

    private void reinsertCustomers(PermutationSolution<Integer> solution, List<Integer> customersToInsert,
                                   Map<Integer, DepotDto> localDepotMap) {
        List<Integer> newPermutation = new ArrayList<>();
        for (int i = 0; i < solution.variables().size(); i++) {
            int cIdx = solution.variables().get(i);
            if (!customersToInsert.contains(cIdx)) {
                newPermutation.add(cIdx);
            }
        }

        for (int customer : customersToInsert) {
            int bestPosition = findBestInsertionPosition(newPermutation, customer, localDepotMap);
            newPermutation.add(bestPosition, customer);
        }

        for (int i = 0; i < newPermutation.size(); i++) {
            solution.variables().set(i, newPermutation.get(i));
        }
    }

    private int findBestInsertionPosition(List<Integer> currentPermutation, int customerToInsert,
                                           Map<Integer, DepotDto> localDepotMap) {
        double bestCost = Double.MAX_VALUE;
        int bestPosition = 0;

        CustomerDto customer = customers.get(customerToInsert);
        DepotDto depot = localDepotMap.get(customerToInsert);

        for (int pos = 0; pos <= currentPermutation.size(); pos++) {
            double insertionCost = calculateInsertionCost(currentPermutation, pos, customerToInsert, depot, localDepotMap);
            if (insertionCost < bestCost) {
                bestCost = insertionCost;
                bestPosition = pos;
            }
        }

        return bestPosition;
    }

    private double calculateInsertionCost(List<Integer> permutation, int position, int customerIdx,
                                           DepotDto depot, Map<Integer, DepotDto> localDepotMap) {
        CustomerDto customer = customers.get(customerIdx);
        int customerMatrixIdx = customer.matrixIndex();
        int depotMatrixIdx = depot.matrixIndex();

        if (permutation.isEmpty()) {
            return distanceMatrix[depotMatrixIdx][customerMatrixIdx] +
                   distanceMatrix[customerMatrixIdx][depotMatrixIdx];
        }

        if (position == 0) {
            int nextCustomer = permutation.get(0);
            int nextMatrixIdx = customers.get(nextCustomer).matrixIndex();
            return distanceMatrix[depotMatrixIdx][customerMatrixIdx] +
                   distanceMatrix[customerMatrixIdx][nextMatrixIdx] -
                   distanceMatrix[depotMatrixIdx][nextMatrixIdx];
        }

        if (position == permutation.size()) {
            int prevCustomer = permutation.get(permutation.size() - 1);
            int prevMatrixIdx = customers.get(prevCustomer).matrixIndex();
            DepotDto prevDepot = localDepotMap.get(prevCustomer);
            return distanceMatrix[prevMatrixIdx][customerMatrixIdx] +
                   distanceMatrix[customerMatrixIdx][depotMatrixIdx] -
                   distanceMatrix[prevMatrixIdx][prevDepot.matrixIndex()];
        }

        int prevCustomer = permutation.get(position - 1);
        int nextCustomer = permutation.get(position);
        int prevMatrixIdx = customers.get(prevCustomer).matrixIndex();
        int nextMatrixIdx = customers.get(nextCustomer).matrixIndex();

        return distanceMatrix[prevMatrixIdx][customerMatrixIdx] +
               distanceMatrix[customerMatrixIdx][nextMatrixIdx] -
               distanceMatrix[prevMatrixIdx][nextMatrixIdx];
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
        
        @SuppressWarnings("unchecked")
        Map<Integer, DepotDto> sourceMap = (Map<Integer, DepotDto>) source.attributes().get("depotMap");
        if (sourceMap != null) {
            copy.attributes().put("depotMap", new HashMap<>(sourceMap));
        }
        
        return copy;
    }
}
