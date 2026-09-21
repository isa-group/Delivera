package com.delivera.fms.engine.annealing.solution;

import com.delivera.fms.engine.core.model.Depot;
import com.delivera.fms.engine.core.model.RoutingProblem;
import com.delivera.fms.engine.core.split.RouteSplitter;
import org.uma.jmetal.util.pseudorandom.PseudoRandomGenerator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Genera el vecino de una solucion y sabe deshacerlo.
 *
 * Un movimiento elige un cliente al azar -lo que pondera los depositos por su tamano- y aplica uno
 * de estos cambios:
 *
 * <ul>
 *   <li><b>Intra-deposito</b>, reordenan una secuencia y el troceado decide donde caen los cortes:
 *       reubicar un cliente, intercambiar dos o invertir un tramo.</li>
 *   <li><b>Inter-deposito</b>, los unicos que cambian el reparto: llevar un cliente a otro
 *       deposito en su posicion mas barata, o intercambiar el deposito de dos clientes. El
 *       intercambio no altera cuantos clientes tiene cada deposito, asi que sigue sirviendo cuando
 *       el reparto ya esta ajustado a la flota.</li>
 * </ul>
 *
 * El deposito destino se elige entre los cercanos al cliente. Mover un cliente al otro extremo del
 * mapa es un movimiento que ningun criterio de aceptacion va a admitir, y proponerlo solo gasta
 * iteraciones. Esa lista se precalcula porque depende de la geometria, no de la asignacion.
 *
 * Cada movimiento guarda una copia de los depositos que toca justo antes de tocarlos. Ese aviso
 * hace dos cosas: dice que hay que recalcular, y permite deshacer si el movimiento se rechaza.
 */
public final class Neighborhood {

    private final RoutingProblem problem;
    private final RouteSplitter splitter;
    private final PseudoRandomGenerator random;
    private final double interDepotProbability;
    private final List<List<Integer>> nearbyDepots;

    public Neighborhood(RoutingProblem problem,
                        RouteSplitter splitter,
                        PseudoRandomGenerator random,
                        double interDepotProbability,
                        double depotCandidateRatio) {
        this.problem = problem;
        this.splitter = splitter;
        this.random = random;
        this.interDepotProbability = interDepotProbability;
        this.nearbyDepots = nearbyDepots(problem, depotCandidateRatio);
    }

    /**
     * Aplica un movimiento aleatorio sobre la solucion y la deja evaluada.
     *
     * @return el movimiento aplicado, con lo necesario para deshacerlo; {@code null} si no habia
     *         ningun movimiento posible para el cliente elegido
     */
    public Move propose(AnnealingSolution solution) {
        int customer = random.nextInt(0, problem.customerCount() - 1);
        int depot = solution.depotOf(customer);
        double before = solution.cost();

        List<AnnealingSolution.DepotBackup> backups;
        if (problem.depotCount() > 1 && random.nextDouble() < interDepotProbability) {
            backups = interDepotMove(solution, customer, depot);
        } else {
            backups = intraDepotMove(solution, customer, depot);
        }
        if (backups == null) {
            return null;
        }

        for (AnnealingSolution.DepotBackup backup : backups) {
            solution.evaluateDepot(backup.depot());
        }
        return new Move(backups, solution.cost() - before);
    }

    public void undo(AnnealingSolution solution, Move move) {
        for (AnnealingSolution.DepotBackup backup : move.backups()) {
            solution.restore(backup);
        }
    }

    private List<AnnealingSolution.DepotBackup> intraDepotMove(AnnealingSolution solution, int customer, int depot) {
        List<Integer> order = solution.order(depot);
        int size = order.size();
        if (size < 2) {
            return null;
        }

        int from = order.indexOf(customer);
        int to = random.nextInt(0, size - 2);
        if (to >= from) {
            to++;
        }
        AnnealingSolution.DepotBackup backup = solution.backup(depot);

        switch (random.nextInt(0, 2)) {
            case 0 -> {
                order.remove(from);
                order.add(to, customer);
            }
            case 1 -> Collections.swap(order, from, to);
            default -> Collections.reverse(order.subList(Math.min(from, to), Math.max(from, to) + 1));
        }
        return List.of(backup);
    }

    private List<AnnealingSolution.DepotBackup> interDepotMove(AnnealingSolution solution, int customer, int source) {
        int target = pickTarget(nearbyDepots.get(customer), source);
        if (target < 0) {
            return intraDepotMove(solution, customer, source);
        }

        List<AnnealingSolution.DepotBackup> backups = List.of(solution.backup(source), solution.backup(target));
        List<Integer> from = solution.order(source);
        List<Integer> to = solution.order(target);

        if (random.nextInt(0, 1) == 0 || to.isEmpty()) {
            from.remove(Integer.valueOf(customer));
            insertCheapest(target, to, customer);
        } else {
            int other = to.get(random.nextInt(0, to.size() - 1));
            from.remove(Integer.valueOf(customer));
            to.remove(Integer.valueOf(other));
            insertCheapest(target, to, customer);
            insertCheapest(source, from, other);
        }
        return backups;
    }

    // Deposito destino uniforme entre los candidatos distintos del actual, o -1 si no hay ninguno.
    private int pickTarget(List<Integer> candidates, int source) {
        int others = candidates.contains(source) ? candidates.size() - 1 : candidates.size();
        if (others == 0) {
            return -1;
        }
        int pick = random.nextInt(0, others - 1);
        for (int candidate : candidates) {
            if (candidate == source) {
                continue;
            }
            if (pick-- == 0) {
                return candidate;
            }
        }
        return -1;
    }

    private void insertCheapest(int depot, List<Integer> order, int customer) {
        Depot target = problem.depot(depot);
        order.add(splitter.bestPosition(target, order, customer), customer);
    }

    /**
     * Para cada cliente, los depositos que no estan a mas de {@code ratio} veces la distancia del
     * mas cercano. Incluye al mas cercano, asi que un cliente asignado a otro deposito siempre
     * tiene al menos un destino que proponer.
     */
    private static List<List<Integer>> nearbyDepots(RoutingProblem problem, double ratio) {
        List<List<Integer>> nearby = new ArrayList<>(problem.customerCount());
        for (int customer = 0; customer < problem.customerCount(); customer++) {
            double nearest = Double.MAX_VALUE;
            for (Depot depot : problem.depots()) {
                nearest = Math.min(nearest, problem.distance(depot, customer));
            }
            List<Integer> candidates = new ArrayList<>();
            for (Depot depot : problem.depots()) {
                if (problem.distance(depot, customer) <= nearest * ratio) {
                    candidates.add(depot.index());
                }
            }
            nearby.add(List.copyOf(candidates));
        }
        return nearby;
    }

    /**
     * Un movimiento ya aplicado.
     *
     * @param backups estado previo de los depositos tocados, en el orden en que hay que restaurarlos
     * @param delta   variacion del coste penalizado; negativo si mejora
     */
    public record Move(List<AnnealingSolution.DepotBackup> backups, double delta) {
    }
}
