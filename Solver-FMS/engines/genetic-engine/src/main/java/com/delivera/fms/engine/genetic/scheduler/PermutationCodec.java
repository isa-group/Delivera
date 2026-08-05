package com.delivera.fms.engine.genetic.scheduler;

import com.delivera.fms.engine.genetic.dto.DepotDto;
import org.uma.jmetal.solution.permutationsolution.PermutationSolution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Traduccion entre la permutacion (gira gigante) y el orden de clientes por deposito.
 * Centraliza la lectura del atributo de asignacion cliente-deposito para que todos los
 * operadores interpreten el cromosoma igual.
 */
public final class PermutationCodec {

    public static final String DEPOT_MAP = "depotMap";

    private PermutationCodec() {
    }

    @SuppressWarnings("unchecked")
    public static Map<Integer, DepotDto> depotMap(PermutationSolution<Integer> solution) {
        return (Map<Integer, DepotDto>) solution.attributes().get(DEPOT_MAP);
    }

    // Clientes de cada deposito, en el orden en que aparecen en la permutacion.
    public static Map<DepotDto, List<Integer>> depotOrder(PermutationSolution<Integer> solution,
                                                          List<DepotDto> depots,
                                                          Map<Integer, DepotDto> depotMap) {
        Map<DepotDto, List<Integer>> depotOrder = new LinkedHashMap<>();
        for (DepotDto depot : depots) {
            depotOrder.put(depot, new ArrayList<>());
        }
        for (int i = 0; i < solution.variables().size(); i++) {
            int customer = solution.variables().get(i);
            DepotDto depot = depotMap.get(customer);
            if (depot != null) {
                depotOrder.get(depot).add(customer);
            }
        }
        return depotOrder;
    }

    /**
     * Reescribe la permutacion con el nuevo orden por deposito conservando las posiciones que
     * ocupa cada deposito. Requiere que el conjunto de clientes de cada deposito no haya cambiado.
     */
    public static void writeBack(PermutationSolution<Integer> solution,
                                 Map<DepotDto, List<Integer>> depotOrder,
                                 Map<Integer, DepotDto> depotMap) {
        Map<DepotDto, Iterator<Integer>> cursors = new HashMap<>();
        for (var entry : depotOrder.entrySet()) {
            cursors.put(entry.getKey(), entry.getValue().iterator());
        }

        for (int i = 0; i < solution.variables().size(); i++) {
            DepotDto depot = depotMap.get(solution.variables().get(i));
            Iterator<Integer> cursor = (depot == null) ? null : cursors.get(depot);
            if (cursor != null && cursor.hasNext()) {
                solution.variables().set(i, cursor.next());
            }
        }
    }

    /**
     * Reescribe la permutacion concatenando los depositos. A diferencia de
     * {@link #writeBack}, admite que algun cliente haya cambiado de deposito, por lo que es la
     * variante que deben usar los operadores inter-deposito.
     */
    public static void writeBackContiguous(PermutationSolution<Integer> solution,
                                           Map<DepotDto, List<Integer>> depotOrder) {
        int position = 0;
        for (List<Integer> order : depotOrder.values()) {
            for (int customer : order) {
                solution.variables().set(position++, customer);
            }
        }
    }
}
