package com.delivera.fms.engine.genetic.operator.search;

import com.delivera.fms.engine.genetic.dto.CustomerDto;
import com.delivera.fms.engine.genetic.dto.DepotDto;
import org.uma.jmetal.solution.permutationsolution.PermutationSolution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LocalSearch {

    private final List<CustomerDto> customers;
    private final List<DepotDto> depots;
    private final double[][] distanceMatrix;

    public LocalSearch(List<CustomerDto> customers, List<DepotDto> depots, double[][] distanceMatrix) {
        this.customers = customers;
        this.depots = depots;
        this.distanceMatrix = distanceMatrix;
    }

    private static final int MAX_2OPT_PASSES = 2;

    public void improveSolution(PermutationSolution<Integer> solution) {
        @SuppressWarnings("unchecked")
        Map<Integer, DepotDto> depotMap = (Map<Integer, DepotDto>) solution.attributes().get("depotMap");
        if (depotMap == null) return;

        Map<DepotDto, List<Integer>> depotOrder = buildDepotOrder(solution, depotMap);

        for (int pass = 0; pass < MAX_2OPT_PASSES; pass++) {
            boolean improved = false;
            for (var entry : depotOrder.entrySet()) {
                DepotDto depot = entry.getKey();
                List<Integer> customerList = entry.getValue();
                if (customerList.size() < 3) continue;
                if (twoOpt(customerList, depot)) improved = true;
            }
            if (!improved) break;
            rebuildPermutation(solution, depotOrder, depotMap);
        }
    }

    Map<DepotDto, List<Integer>> buildDepotOrder(PermutationSolution<Integer> solution,
                                                  Map<Integer, DepotDto> depotMap) {
        Map<DepotDto, List<Integer>> depotOrder = new LinkedHashMap<>();
        for (DepotDto d : depots) {
            depotOrder.put(d, new ArrayList<>());
        }
        for (int i = 0; i < solution.variables().size(); i++) {
            int cIdx = solution.variables().get(i);
            DepotDto depot = depotMap.get(cIdx);
            if (depot != null) {
                depotOrder.get(depot).add(cIdx);
            }
        }
        return depotOrder;
    }

    private boolean twoOpt(List<Integer> customerList, DepotDto depot) {
        int n = customerList.size();
        if (n < 3) return false;

        boolean improved = false;
        int depotIdx = depot.matrixIndex();
        int[] mi = buildMatrixIndices(customerList);

        for (int i = -1; i < n - 1; i++) {
            int iPrev = (i >= 0) ? mi[i] : depotIdx;
            int iNext = mi[i + 1];
            double edgeICost = distanceMatrix[iPrev][iNext];

            for (int j = i + 2; j < n; j++) {
                int jCurr = mi[j];
                int jNext = (j + 1 < n) ? mi[j + 1] : depotIdx;
                double edgeJCost = distanceMatrix[jCurr][jNext];

                double oldCost = edgeICost + edgeJCost;
                double newCost = distanceMatrix[iPrev][jCurr] + distanceMatrix[iNext][jNext];

                if (newCost < oldCost - 1e-10) {
                    reverseSegment(customerList, i + 1, j);
                    rebuildMatrixIndices(customerList, mi);
                    improved = true;
                }
            }
        }

        return improved;
    }

    private void reverseSegment(List<Integer> list, int from, int to) {
        while (from < to) {
            int tmp = list.get(from);
            list.set(from, list.get(to));
            list.set(to, tmp);
            from++;
            to--;
        }
    }

    private int[] buildMatrixIndices(List<Integer> customerList) {
        int[] mi = new int[customerList.size()];
        for (int i = 0; i < customerList.size(); i++) {
            mi[i] = getMatrixIndex(customerList.get(i));
        }
        return mi;
    }

    private void rebuildMatrixIndices(List<Integer> customerList, int[] mi) {
        for (int i = 0; i < customerList.size(); i++) {
            mi[i] = getMatrixIndex(customerList.get(i));
        }
    }

    private int getMatrixIndex(int customerIdx) {
        return customers.get(customerIdx).matrixIndex();
    }

    private void rebuildPermutation(PermutationSolution<Integer> solution,
                                     Map<DepotDto, List<Integer>> depotOrder,
                                     Map<Integer, DepotDto> depotMap) {
        Map<DepotDto, Iterator<Integer>> iterators = new HashMap<>();
        for (var entry : depotOrder.entrySet()) {
            iterators.put(entry.getKey(), entry.getValue().iterator());
        }

        for (int i = 0; i < solution.variables().size(); i++) {
            int cIdx = solution.variables().get(i);
            DepotDto depot = depotMap.get(cIdx);
            if (depot != null) {
                Iterator<Integer> it = iterators.get(depot);
                if (it != null && it.hasNext()) {
                    solution.variables().set(i, it.next());
                }
            }
        }
    }
}
