package com.delivera.fms.engine.annealing.solution;

import com.delivera.fms.engine.core.model.Depot;
import com.delivera.fms.engine.core.model.RoutingProblem;
import com.delivera.fms.engine.core.split.RouteSplitter;

import java.util.ArrayList;
import java.util.List;

/**
 * Una solucion del recocido: la secuencia de clientes de cada deposito y, derivada de ella, la
 * asignacion cliente-deposito.
 *
 * Es el mismo espacio de soluciones que recorre el motor genetico, sin la permutacion global que
 * alli existe solo por la representacion de jMetal. Las rutas no se guardan: se derivan troceando
 * cada secuencia con {@link RouteSplitter}.
 *
 * El coste se cachea por deposito. Un movimiento toca uno o dos depositos, asi que solo esos se
 * vuelven a trocear y el total se mantiene por diferencias. Es lo que hace asequible evaluar cada
 * movimiento con el troceado exacto en lugar de con un delta sobre la secuencia, que no ve donde
 * va a caer el corte en rutas.
 */
public final class AnnealingSolution {

    private final RoutingProblem problem;
    private final RouteSplitter splitter;
    private final List<List<Integer>> orders;
    private final int[] depotOf;
    private final double[] depotCost;
    private double totalCost;

    private AnnealingSolution(RoutingProblem problem, RouteSplitter splitter, List<List<Integer>> orders) {
        this.problem = problem;
        this.splitter = splitter;
        this.orders = orders;
        this.depotOf = new int[problem.customerCount()];
        this.depotCost = new double[problem.depotCount()];
        evaluateAll();
    }

    /** Construye la solucion a partir de las secuencias por deposito y la evalua entera. */
    public static AnnealingSolution of(RoutingProblem problem, RouteSplitter splitter, List<List<Integer>> orders) {
        List<List<Integer>> copies = new ArrayList<>(orders.size());
        for (List<Integer> order : orders) {
            copies.add(new ArrayList<>(order));
        }
        return new AnnealingSolution(problem, splitter, copies);
    }

    public AnnealingSolution copy() {
        return new AnnealingSolution(problem, splitter, copyOrders(), depotOf.clone(), depotCost.clone(), totalCost);
    }

    private AnnealingSolution(RoutingProblem problem, RouteSplitter splitter, List<List<Integer>> orders,
                              int[] depotOf, double[] depotCost, double totalCost) {
        this.problem = problem;
        this.splitter = splitter;
        this.orders = orders;
        this.depotOf = depotOf;
        this.depotCost = depotCost;
        this.totalCost = totalCost;
    }

    public RoutingProblem problem() {
        return problem;
    }

    /** Coste penalizado total, el que guia la busqueda. */
    public double cost() {
        return totalCost;
    }

    public double depotCost(int depot) {
        return depotCost[depot];
    }

    /** Secuencia mutable del deposito. Quien la modifique debe llamar a {@link #evaluateDepot}. */
    public List<Integer> order(int depot) {
        return orders.get(depot);
    }

    public List<List<Integer>> orders() {
        return orders;
    }

    public int depotOf(int customer) {
        return depotOf[customer];
    }

    /** Vuelve a trocear un deposito y actualiza el total por diferencia. */
    public void evaluateDepot(int depot) {
        double previous = depotCost[depot];
        double current = splitter.penalizedCost(problem.depot(depot), orders.get(depot));
        depotCost[depot] = current;
        totalCost += current - previous;
        for (int customer : orders.get(depot)) {
            depotOf[customer] = depot;
        }
    }

    /**
     * Vuelve a trocear todos los depositos y recalcula el total desde cero, sin arrastre de
     * diferencias. Es lo que hay que llamar tras una fase que haya podido tocar cualquier deposito.
     */
    public void evaluateAll() {
        totalCost = 0.0;
        for (int depot = 0; depot < depotCost.length; depot++) {
            depotCost[depot] = splitter.penalizedCost(problem.depot(depot), orders.get(depot));
            totalCost += depotCost[depot];
            for (int customer : orders.get(depot)) {
                depotOf[customer] = depot;
            }
        }
    }

    /**
     * Si el troceado de todos los depositos cumple de verdad capacidad, duracion y flota.
     * Cuesta un troceado completo: no llamarlo en cada movimiento.
     */
    public boolean isFeasible() {
        for (Depot depot : problem.depots()) {
            if (!splitter.isFeasible(depot, orders.get(depot.index()))) {
                return false;
            }
        }
        return true;
    }

    /** Copia de la secuencia y el coste de un deposito, para poder deshacer un movimiento. */
    public DepotBackup backup(int depot) {
        return new DepotBackup(depot, new ArrayList<>(orders.get(depot)), depotCost[depot]);
    }

    /** Restaura la secuencia y el coste guardados y vuelve a apuntar sus clientes al deposito. */
    public void restore(DepotBackup backup) {
        List<Integer> order = orders.get(backup.depot());
        order.clear();
        order.addAll(backup.order());
        totalCost += backup.cost() - depotCost[backup.depot()];
        depotCost[backup.depot()] = backup.cost();
        for (int customer : order) {
            depotOf[customer] = backup.depot();
        }
    }

    private List<List<Integer>> copyOrders() {
        List<List<Integer>> copies = new ArrayList<>(orders.size());
        for (List<Integer> order : orders) {
            copies.add(new ArrayList<>(order));
        }
        return copies;
    }

    public record DepotBackup(int depot, List<Integer> order, double cost) {
    }
}
