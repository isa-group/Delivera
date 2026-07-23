package com.delivera.fms.engine.genetic.operator.search;

import com.delivera.fms.engine.genetic.dto.CustomerDto;
import com.delivera.fms.engine.genetic.dto.DepotDto;
import org.uma.jmetal.solution.permutationsolution.PermutationSolution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InterDepotLocalSearch {

    private static final double BORDER_RATIO = 1.3;
    private static final int MAX_ITERATIONS = 3;

    private final List<CustomerDto> customers;
    private final List<DepotDto> depots;
    private final double[][] distanceMatrix;

    public InterDepotLocalSearch(List<CustomerDto> customers, List<DepotDto> depots, double[][] distanceMatrix) {
        this.customers = customers;
        this.depots = depots;
        this.distanceMatrix = distanceMatrix;
    }

    public boolean optimize(PermutationSolution<Integer> solution) {
        if (depots.size() < 2) return false;

        @SuppressWarnings("unchecked")
        Map<Integer, DepotDto> depotMap = (Map<Integer, DepotDto>) solution.attributes().get("depotMap");
        if (depotMap == null) return false;

        boolean anyImprovement = false;

        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            List<Integer> borderCustomers = findBorderCustomers(solution, depotMap);
            if (borderCustomers.isEmpty()) break;

            BestMove best = findBestReassignment(solution, depotMap, borderCustomers);

            if (best != null && best.delta < -1e-10) {
                applyReassignment(solution, depotMap, best);
                anyImprovement = true;
            } else {
                break;
            }
        }

        return anyImprovement;
    }

    private List<Integer> findBorderCustomers(PermutationSolution<Integer> solution,
                                               Map<Integer, DepotDto> depotMap) {
        List<Integer> borderCustomers = new ArrayList<>();
        for (int i = 0; i < solution.variables().size(); i++) {
            int cIdx = solution.variables().get(i);
            CustomerDto customer = customers.get(cIdx);
            DepotDto assignedDepot = depotMap.get(cIdx);
            if (assignedDepot == null) continue;

            double distToAssigned = distanceMatrix[assignedDepot.matrixIndex()][customer.matrixIndex()];
            double minOtherDist = Double.MAX_VALUE;
            for (DepotDto d : depots) {
                if (d.equals(assignedDepot)) continue;
                double dist = distanceMatrix[d.matrixIndex()][customer.matrixIndex()];
                if (dist < minOtherDist) minOtherDist = dist;
            }

            if (minOtherDist / (distToAssigned + 1e-10) < BORDER_RATIO) {
                borderCustomers.add(cIdx);
            }
        }
        return borderCustomers;
    }

    private BestMove findBestReassignment(PermutationSolution<Integer> solution,
                                           Map<Integer, DepotDto> depotMap,
                                           List<Integer> borderCustomers) {
        BestMove best = null;
        Map<DepotDto, List<int[]>> depotSegments = buildDepotSegments(solution, depotMap);

        for (int cIdx : borderCustomers) {
            DepotDto currentDepot = depotMap.get(cIdx);
            int currentPos = findCustomerPosition(solution, cIdx);
            if (currentPos < 0) continue;

            double oldContribution = customerContributionInRoute(solution, cIdx, currentDepot, depotMap, currentPos);

            for (DepotDto newDepot : depots) {
                if (newDepot.equals(currentDepot)) continue;

                List<int[]> segments = depotSegments.get(newDepot);
                if (segments == null) continue;

                int bestInsertPos = -1;
                double bestInsertCost = Double.MAX_VALUE;

                int size = solution.variables().size();
                double startCost = insertionCostInRoute(solution, 0, cIdx, newDepot);
                if (startCost < bestInsertCost) {
                    bestInsertCost = startCost;
                    bestInsertPos = 0;
                }

                for (int[] seg : segments) {
                    for (int pos = seg[0] + 1; pos <= seg[1] + 1 && pos <= size; pos++) {
                        double cost = insertionCostInRoute(solution, pos, cIdx, newDepot);
                        if (cost < bestInsertCost) {
                            bestInsertCost = cost;
                            bestInsertPos = pos;
                        }
                    }
                }

                double endCost = insertionCostInRoute(solution, size, cIdx, newDepot);
                if (endCost < bestInsertCost) {
                    bestInsertCost = endCost;
                    bestInsertPos = size;
                }

                double newContribution = bestInsertCost;
                double delta = newContribution - oldContribution;

                if (best == null || delta < best.delta) {
                    best = new BestMove(cIdx, currentDepot, newDepot, currentPos, bestInsertPos, delta);
                }
            }
        }

        return best;
    }

    private Map<DepotDto, List<int[]>> buildDepotSegments(PermutationSolution<Integer> solution,
                                                            Map<Integer, DepotDto> depotMap) {
        Map<DepotDto, List<int[]>> depotSegments = new HashMap<>();
        for (DepotDto d : depots) {
            depotSegments.put(d, new ArrayList<>());
        }

        int start = 0;
        DepotDto currentDepot = null;
        int n = solution.variables().size();

        for (int i = 0; i < n; i++) {
            int cIdx = solution.variables().get(i);
            DepotDto depot = depotMap.get(cIdx);

            if (currentDepot == null) {
                currentDepot = depot;
                start = i;
            } else if (!currentDepot.equals(depot)) {
                if (i > start) {
                    depotSegments.get(currentDepot).add(new int[]{start, i - 1});
                }
                currentDepot = depot;
                start = i;
            }
        }
        if (currentDepot != null && n > start) {
            depotSegments.get(currentDepot).add(new int[]{start, n - 1});
        }

        return depotSegments;
    }

    private void applyReassignment(PermutationSolution<Integer> solution,
                                    Map<Integer, DepotDto> depotMap,
                                    BestMove move) {
        int adjustedPos = move.currentPos;
        if (move.currentPos < move.bestInsertPos) {
            adjustedPos = move.currentPos;
        }
        solution.variables().remove(adjustedPos);

        int insertPos = move.bestInsertPos;
        if (move.currentPos < move.bestInsertPos) {
            insertPos = move.bestInsertPos - 1;
        }
        if (insertPos > solution.variables().size()) {
            insertPos = solution.variables().size();
        }
        solution.variables().add(insertPos, move.customerIdx);
        depotMap.put(move.customerIdx, move.newDepot);
    }

    private int findCustomerPosition(PermutationSolution<Integer> solution, int cIdx) {
        for (int i = 0; i < solution.variables().size(); i++) {
            if (solution.variables().get(i) == cIdx) return i;
        }
        return -1;
    }

    private boolean isInDepotSegment(PermutationSolution<Integer> solution, int pos,
                                      DepotDto depot, Map<Integer, DepotDto> depotMap) {
        if (pos == 0 || pos == solution.variables().size()) return true;
        int prevCustomer = solution.variables().get(pos - 1);
        DepotDto prevDepot = depotMap.get(prevCustomer);
        return prevDepot != null && prevDepot.equals(depot);
    }

    private double customerContributionInRoute(PermutationSolution<Integer> solution, int cIdx,
                                                DepotDto depot, Map<Integer, DepotDto> depotMap,
                                                int position) {
        CustomerDto customer = customers.get(cIdx);
        int cMatrix = customer.matrixIndex();
        int depotMatrix = depot.matrixIndex();
        int n = solution.variables().size();

        int prevCustomer = -1;
        for (int i = position - 1; i >= 0; i--) {
            int pC = solution.variables().get(i);
            if (depot.equals(depotMap.get(pC))) {
                prevCustomer = pC;
                break;
            }
        }
        int nextCustomer = -1;
        for (int i = position + 1; i < n; i++) {
            int nC = solution.variables().get(i);
            if (depot.equals(depotMap.get(nC))) {
                nextCustomer = nC;
                break;
            }
        }

        int prevMatrix = (prevCustomer >= 0) ? customers.get(prevCustomer).matrixIndex() : depotMatrix;
        int nextMatrix = (nextCustomer >= 0) ? customers.get(nextCustomer).matrixIndex() : depotMatrix;

        return distanceMatrix[prevMatrix][cMatrix]
                + distanceMatrix[cMatrix][nextMatrix]
                - distanceMatrix[prevMatrix][nextMatrix];
    }

    private double insertionCostInRoute(PermutationSolution<Integer> solution, int pos,
                                         int cIdx, DepotDto depot) {
        CustomerDto customer = customers.get(cIdx);
        int cMatrix = customer.matrixIndex();
        int depotMatrix = depot.matrixIndex();
        int n = solution.variables().size();

        if (n == 0) {
            return distanceMatrix[depotMatrix][cMatrix] + distanceMatrix[cMatrix][depotMatrix];
        }
        if (pos == 0) {
            int next = solution.variables().get(0);
            int nextMatrix = customers.get(next).matrixIndex();
            return distanceMatrix[depotMatrix][cMatrix] + distanceMatrix[cMatrix][nextMatrix]
                    - distanceMatrix[depotMatrix][nextMatrix];
        }
        if (pos == n) {
            int prev = solution.variables().get(n - 1);
            int prevMatrix = customers.get(prev).matrixIndex();
            return distanceMatrix[prevMatrix][cMatrix] + distanceMatrix[cMatrix][depotMatrix]
                    - distanceMatrix[prevMatrix][depotMatrix];
        }

        int prev = solution.variables().get(pos - 1);
        int next = solution.variables().get(pos);
        int prevMatrix = customers.get(prev).matrixIndex();
        int nextMatrix = customers.get(next).matrixIndex();

        return distanceMatrix[prevMatrix][cMatrix] + distanceMatrix[cMatrix][nextMatrix]
                - distanceMatrix[prevMatrix][nextMatrix];
    }

    private static class BestMove {
        final int customerIdx;
        final DepotDto currentDepot;
        final DepotDto newDepot;
        final int currentPos;
        final int bestInsertPos;
        final double delta;

        BestMove(int customerIdx, DepotDto currentDepot, DepotDto newDepot,
                 int currentPos, int bestInsertPos, double delta) {
            this.customerIdx = customerIdx;
            this.currentDepot = currentDepot;
            this.newDepot = newDepot;
            this.currentPos = currentPos;
            this.bestInsertPos = bestInsertPos;
            this.delta = delta;
        }
    }
}
