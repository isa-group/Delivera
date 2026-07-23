package com.delivera.fms.engine.genetic.operator.mutation;

import com.delivera.fms.engine.genetic.dto.CustomerDto;
import com.delivera.fms.engine.genetic.dto.DepotDto;
import org.uma.jmetal.operator.mutation.MutationOperator;
import org.uma.jmetal.solution.permutationsolution.PermutationSolution;
import org.uma.jmetal.util.pseudorandom.JMetalRandom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IntraDepotMutation implements MutationOperator<PermutationSolution<Integer>> {

    private final double probability;
    private final JMetalRandom random;
    private final List<CustomerDto> customers;
    private final List<DepotDto> depots;

    public IntraDepotMutation(double probability,
                               List<CustomerDto> customers,
                               List<DepotDto> depots) {
        this.probability = probability;
        this.random = JMetalRandom.getInstance();
        this.customers = customers;
        this.depots = depots;
    }

    @Override
    public double mutationProbability() {
        return probability;
    }

    @Override
    public PermutationSolution<Integer> execute(PermutationSolution<Integer> solution) {
        if (random.nextDouble() >= probability) {
            return solution;
        }

        @SuppressWarnings("unchecked")
        Map<Integer, DepotDto> localDepotMap = (Map<Integer, DepotDto>) solution.attributes().get("depotMap");
        if (localDepotMap == null) return solution;

        Map<DepotDto, List<int[]>> depotSegments = buildDepotSegments(solution, localDepotMap);

        for (var entry : depotSegments.entrySet()) {
            List<int[]> segments = entry.getValue();
            if (segments.size() < 2) continue;

            int op = random.nextInt(0, 2);
            switch (op) {
                case 0 -> swapCustomers(solution, segments);
                case 1 -> invertSegment(solution, segments);
                case 2 -> relocateCustomer(solution, segments);
            }
        }

        return solution;
    }

    private Map<DepotDto, List<int[]>> buildDepotSegments(PermutationSolution<Integer> solution,
                                                           Map<Integer, DepotDto> localDepotMap) {
        Map<DepotDto, List<int[]>> depotSegments = new HashMap<>();
        for (DepotDto d : depots) {
            depotSegments.put(d, new ArrayList<>());
        }

        int start = 0;
        DepotDto currentDepot = null;

        for (int i = 0; i < solution.variables().size(); i++) {
            int cIdx = solution.variables().get(i);
            DepotDto depot = localDepotMap.get(cIdx);

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
        if (currentDepot != null && solution.variables().size() > start) {
            depotSegments.get(currentDepot).add(new int[]{start, solution.variables().size() - 1});
        }

        return depotSegments;
    }

    private void swapCustomers(PermutationSolution<Integer> solution, List<int[]> segments) {
        int[] seg1 = segments.get(random.nextInt(0, segments.size() - 1));
        int[] seg2 = segments.get(random.nextInt(0, segments.size() - 1));

        int i = seg1[0] + random.nextInt(0, seg1[1] - seg1[0]);
        int j = seg2[0] + random.nextInt(0, seg2[1] - seg2[0]);

        if (i != j) {
            int tmp = solution.variables().get(i);
            solution.variables().set(i, solution.variables().get(j));
            solution.variables().set(j, tmp);
        }
    }

    private void invertSegment(PermutationSolution<Integer> solution, List<int[]> segments) {
        int[] seg = segments.get(random.nextInt(0, segments.size() - 1));
        if (seg[1] - seg[0] < 2) return;

        int i = seg[0] + random.nextInt(0, seg[1] - seg[0] - 1);
        int j = i + 1 + random.nextInt(0, seg[1] - i - 1);
        if (j > seg[1]) j = seg[1];

        while (i < j) {
            int tmp = solution.variables().get(i);
            solution.variables().set(i, solution.variables().get(j));
            solution.variables().set(j, tmp);
            i++;
            j--;
        }
    }

    private void relocateCustomer(PermutationSolution<Integer> solution, List<int[]> segments) {
        int[] srcSeg = segments.get(random.nextInt(0, segments.size() - 1));
        int srcPos = srcSeg[0] + random.nextInt(0, srcSeg[1] - srcSeg[0]);

        int[] dstSeg = segments.get(random.nextInt(0, segments.size() - 1));
        int dstPos = dstSeg[0] + random.nextInt(0, dstSeg[1] - dstSeg[0]);

        if (srcPos == dstPos) return;

        int customer = solution.variables().get(srcPos);
        solution.variables().remove(srcPos);
        int adjustedDst = dstPos > srcPos ? dstPos - 1 : dstPos;
        solution.variables().add(adjustedDst, customer);
    }
}
