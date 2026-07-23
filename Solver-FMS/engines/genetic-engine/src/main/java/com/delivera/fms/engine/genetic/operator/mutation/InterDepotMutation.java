package com.delivera.fms.engine.genetic.operator.mutation;

import com.delivera.fms.engine.genetic.dto.CustomerDto;
import com.delivera.fms.engine.genetic.dto.DepotDto;
import org.uma.jmetal.solution.permutationsolution.PermutationSolution;
import org.uma.jmetal.util.pseudorandom.JMetalRandom;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class InterDepotMutation {

    private final double probability;
    private final JMetalRandom random;
    private final List<CustomerDto> customers;
    private final List<DepotDto> depots;
    private final double[][] distanceMatrix;

    public InterDepotMutation(double probability,
                               List<CustomerDto> customers,
                               List<DepotDto> depots,
                               double[][] distanceMatrix) {
        this.probability = probability;
        this.random = JMetalRandom.getInstance();
        this.customers = customers;
        this.depots = depots;
        this.distanceMatrix = distanceMatrix;
    }

    public PermutationSolution<Integer> execute(PermutationSolution<Integer> solution) {
        if (depots.size() < 2) return solution;
        if (random.nextDouble() >= probability) return solution;

        @SuppressWarnings("unchecked")
        Map<Integer, DepotDto> localDepotMap = (Map<Integer, DepotDto>) solution.attributes().get("depotMap");
        if (localDepotMap == null) return solution;

        List<Integer> borderCustomers = findBorderCustomers(solution, localDepotMap);
        if (borderCustomers.isEmpty()) return solution;

        int customerIdx = borderCustomers.get(random.nextInt(0, borderCustomers.size() - 1));
        DepotDto currentDepot = localDepotMap.get(customerIdx);
        DepotDto bestDepot = findBestDepotForCustomer(customerIdx, currentDepot);

        if (bestDepot != null && !bestDepot.equals(currentDepot)) {
            reassignCustomer(solution, customerIdx, currentDepot, bestDepot, localDepotMap);
            localDepotMap.put(customerIdx, bestDepot);
        }

        return solution;
    }

    private List<Integer> findBorderCustomers(PermutationSolution<Integer> solution,
                                               Map<Integer, DepotDto> localDepotMap) {
        List<Integer> borderCustomers = new ArrayList<>();

        for (int i = 0; i < solution.variables().size(); i++) {
            int cIdx = solution.variables().get(i);
            CustomerDto customer = customers.get(cIdx);
            DepotDto assignedDepot = localDepotMap.get(cIdx);

            double distToAssigned = distanceMatrix[assignedDepot.matrixIndex()][customer.matrixIndex()];

            double minOtherDist = Double.MAX_VALUE;
            for (DepotDto d : depots) {
                if (!d.equals(assignedDepot)) {
                    double dist = distanceMatrix[d.matrixIndex()][customer.matrixIndex()];
                    if (dist < minOtherDist) {
                        minOtherDist = dist;
                    }
                }
            }

            double ratio = minOtherDist / (distToAssigned + 1e-10);
            if (ratio < 1.3) {
                borderCustomers.add(cIdx);
            }
        }

        return borderCustomers;
    }

    private DepotDto findBestDepotForCustomer(int customerIdx, DepotDto excludeDepot) {
        CustomerDto customer = customers.get(customerIdx);
        DepotDto bestDepot = null;
        double bestDist = Double.MAX_VALUE;

        for (DepotDto d : depots) {
            if (d.equals(excludeDepot)) continue;
            double dist = distanceMatrix[d.matrixIndex()][customer.matrixIndex()];
            if (dist < bestDist) {
                bestDist = dist;
                bestDepot = d;
            }
        }

        return bestDepot;
    }

    private void reassignCustomer(PermutationSolution<Integer> solution, int customerIdx,
                                   DepotDto oldDepot, DepotDto newDepot,
                                   Map<Integer, DepotDto> localDepotMap) {
        int oldPos = -1;
        for (int i = 0; i < solution.variables().size(); i++) {
            if (solution.variables().get(i) == customerIdx) {
                oldPos = i;
                break;
            }
        }
        if (oldPos == -1) return;

        solution.variables().remove(oldPos);

        int insertPos = findBestInsertionPosition(solution, customerIdx, newDepot, localDepotMap);
        solution.variables().add(insertPos, customerIdx);
    }

    private int findBestInsertionPosition(PermutationSolution<Integer> solution, int customerIdx,
                                           DepotDto depot, Map<Integer, DepotDto> localDepotMap) {
        int bestPos = 0;
        double bestCost = Double.MAX_VALUE;

        for (int i = 0; i <= solution.variables().size(); i++) {
            boolean inDepotSegment = isInDepotSegment(solution, i, depot, localDepotMap);
            if (!inDepotSegment && i < solution.variables().size()) continue;

            double cost = insertionCost(solution, i, customerIdx, depot);
            if (cost < bestCost) {
                bestCost = cost;
                bestPos = i;
            }
        }

        return bestPos;
    }

    private boolean isInDepotSegment(PermutationSolution<Integer> solution, int pos,
                                      DepotDto depot, Map<Integer, DepotDto> localDepotMap) {
        if (pos == 0 || pos == solution.variables().size()) return true;
        int prevCustomer = solution.variables().get(pos - 1);
        return localDepotMap.get(prevCustomer).equals(depot);
    }

    private double insertionCost(PermutationSolution<Integer> solution, int pos, int customerIdx, DepotDto depot) {
        CustomerDto customer = customers.get(customerIdx);
        int cMatrix = customer.matrixIndex();
        int dMatrix = depot.matrixIndex();

        if (solution.variables().size() == 0) {
            return distanceMatrix[dMatrix][cMatrix] + distanceMatrix[cMatrix][dMatrix];
        }

        if (pos == 0) {
            int next = solution.variables().get(0);
            int nextMatrix = customers.get(next).matrixIndex();
            return distanceMatrix[dMatrix][cMatrix] + distanceMatrix[cMatrix][nextMatrix]
                    - distanceMatrix[dMatrix][nextMatrix];
        }

        if (pos == solution.variables().size()) {
            int prev = solution.variables().get(pos - 1);
            int prevMatrix = customers.get(prev).matrixIndex();
            return distanceMatrix[prevMatrix][cMatrix] + distanceMatrix[cMatrix][dMatrix]
                    - distanceMatrix[prevMatrix][dMatrix];
        }

        int prev = solution.variables().get(pos - 1);
        int next = solution.variables().get(pos);
        int prevMatrix = customers.get(prev).matrixIndex();
        int nextMatrix = customers.get(next).matrixIndex();

        return distanceMatrix[prevMatrix][cMatrix] + distanceMatrix[cMatrix][nextMatrix]
                - distanceMatrix[prevMatrix][nextMatrix];
    }
}
