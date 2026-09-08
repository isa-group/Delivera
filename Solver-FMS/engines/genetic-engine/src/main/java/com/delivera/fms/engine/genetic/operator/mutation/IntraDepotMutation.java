package com.delivera.fms.engine.genetic.operator.mutation;

import com.delivera.fms.engine.genetic.dto.DepotDto;
import com.delivera.fms.engine.genetic.scheduler.PermutationCodec;
import org.uma.jmetal.operator.mutation.MutationOperator;
import org.uma.jmetal.solution.permutationsolution.PermutationSolution;
import org.uma.jmetal.util.pseudorandom.PseudoRandomGenerator;

import java.util.List;
import java.util.Map;

/**
 * Reordena clientes dentro de la secuencia de cada deposito, sin cambiar su asignacion.
 *
 * Trabaja sobre la secuencia del deposito y no sobre los bloques contiguos de la permutacion:
 * un deposito cuyos clientes ya estan agrupados es justo el caso que hay que poder mutar.
 */
public class IntraDepotMutation implements MutationOperator<PermutationSolution<Integer>> {

    private final double probability;
    private final PseudoRandomGenerator random;
    private final List<DepotDto> depots;

    public IntraDepotMutation(double probability, List<DepotDto> depots, PseudoRandomGenerator random) {
        this.probability = probability;
        this.random = random;
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

        Map<Integer, DepotDto> depotMap = PermutationCodec.depotMap(solution);
        if (depotMap == null) {
            return solution;
        }

        Map<DepotDto, List<Integer>> depotOrder = PermutationCodec.depotOrder(solution, depots, depotMap);

        for (List<Integer> order : depotOrder.values()) {
            if (order.size() < 2) {
                continue;
            }
            switch (random.nextInt(0, 2)) {
                case 0 -> swap(order);
                case 1 -> invert(order);
                default -> relocate(order);
            }
        }

        PermutationCodec.writeBack(solution, depotOrder, depotMap);
        return solution;
    }

    private void swap(List<Integer> order) {
        int i = random.nextInt(0, order.size() - 1);
        int j = random.nextInt(0, order.size() - 1);
        if (i == j) {
            return;
        }
        int tmp = order.get(i);
        order.set(i, order.get(j));
        order.set(j, tmp);
    }

    private void invert(List<Integer> order) {
        if (order.size() < 3) {
            return;
        }
        int from = random.nextInt(0, order.size() - 2);
        int to = random.nextInt(from + 1, order.size() - 1);
        while (from < to) {
            int tmp = order.get(from);
            order.set(from, order.get(to));
            order.set(to, tmp);
            from++;
            to--;
        }
    }

    private void relocate(List<Integer> order) {
        int from = random.nextInt(0, order.size() - 1);
        int to = random.nextInt(0, order.size() - 1);
        if (from == to) {
            return;
        }
        int customer = order.remove(from);
        order.add(to, customer);
    }
}
