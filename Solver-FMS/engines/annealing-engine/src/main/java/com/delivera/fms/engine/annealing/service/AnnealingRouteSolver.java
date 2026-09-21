package com.delivera.fms.engine.annealing.service;

import com.delivera.fms.engine.annealing.algorithm.SimulatedAnnealing;
import com.delivera.fms.engine.annealing.dto.RouteDto;
import com.delivera.fms.engine.annealing.dto.RoutingRequest;
import com.delivera.fms.engine.annealing.dto.RoutingResponse;
import com.delivera.fms.engine.annealing.dto.TracePointDto;
import com.delivera.fms.engine.annealing.dto.VehicleDto;
import com.delivera.fms.engine.annealing.solution.AnnealingSolution;
import com.delivera.fms.engine.annealing.solution.Neighborhood;
import com.delivera.fms.engine.core.model.Customer;
import com.delivera.fms.engine.core.model.Depot;
import com.delivera.fms.engine.core.model.Route;
import com.delivera.fms.engine.core.model.RoutingProblem;
import com.delivera.fms.engine.core.search.DepotRebalancer;
import com.delivera.fms.engine.core.search.RouteOptimizer;
import com.delivera.fms.engine.core.split.RouteSplitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.uma.jmetal.util.pseudorandom.PseudoRandomGenerator;
import org.uma.jmetal.util.pseudorandom.impl.JavaRandomGenerator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Entrada del motor: traduce la peticion, construye la solucion de partida, lanza el recocido y
 * convierte el resultado en rutas.
 *
 * El recocido en si esta en {@link SimulatedAnnealing}; la funcion objetivo, la busqueda local y
 * el reequilibrado entre depositos vienen de {@code routing-core}, compartido con el motor
 * genetico. Que ambos midan el coste con el mismo codigo es lo que hace comparables sus
 * resultados: una diferencia entre ellos es atribuible a la estrategia de busqueda.
 */
@Service
public class AnnealingRouteSolver {

    private static final Logger log = LoggerFactory.getLogger(AnnealingRouteSolver.class);
    private static final String SOLVER_TYPE = "ANNEALING";

    /** Candidatos del vecino mas cercano aleatorizado con que se construye la solucion inicial. */
    private static final int SEED_CANDIDATE_LIST = 3;
    /** Pasadas de reequilibrado del ultimo recurso, si en toda la ejecucion no hubo una factible. */
    private static final int FINAL_REPAIR_PASSES = 5;
    /**
     * Posiciones de insercion que confirma el reequilibrador al reparar la flota. El barrido
     * exacto del motor genetico cuesta segundos por pasada en las instancias grandes; con
     * presupuesto de tiempo eso es inasumible, y el orden fino ya lo afina el propio recocido.
     */
    private static final int REPAIR_INSERTION_CANDIDATES = 3;

    // Rango de [0, 2^48) dado a que a partir de 2^48 las semillas dejan de ser distintas
    private static final long MAX_SEED = (1L << 48) - 1;

    public RoutingResponse solve(RoutingRequest request) {
        AnnealingParameters params = AnnealingParameters.from(request.parameters());
        long seed = resolveSeed(request.parameters());
        PseudoRandomGenerator random = new JavaRandomGenerator(seed);

        log.info("Solving MD-CVRP problem '{}' with solver: {}, seed: {} and parameters: {}",
                request.problemId(), SOLVER_TYPE, seed, params);
        long startTime = System.currentTimeMillis();

        RoutingProblem problem = ProblemMapper.toProblem(request);
        RouteSplitter splitter = new RouteSplitter(problem);
        RouteOptimizer optimizer = new RouteOptimizer(problem, splitter);
        DepotRebalancer rebalancer = new DepotRebalancer(problem, splitter, REPAIR_INSERTION_CANDIDATES);
        Neighborhood neighborhood = new Neighborhood(problem, splitter, random,
                params.interDepotMoveProbability(), params.depotCandidateRatio());

        AnnealingSolution initial = initialSolution(problem, splitter, random);
        SimulatedAnnealing annealing = new SimulatedAnnealing(
                initial, neighborhood, optimizer, rebalancer, params, random, startTime);
        annealing.run();

        AnnealingSolution solution = polish(annealing, optimizer, rebalancer);

        List<RouteDto> routes = decode(solution, splitter, request.vehicles());
        double totalCost = routes.stream().mapToDouble(RouteDto::totalDistance).sum();
        long computationTime = System.currentTimeMillis() - startTime;

        log.info("Problem '{}' solved. Routes: {}, Total cost: {}, Time: {}ms, Levels: {}, Moves: {}, "
                        + "Accepted: {}, Feasible: {}, Seed: {}",
                request.problemId(), routes.size(), totalCost, computationTime, annealing.levels(),
                annealing.moves(), annealing.accepted(), solution.isFeasible(), seed);

        List<TracePointDto> trace = annealing.trace().stream()
                .map(point -> new TracePointDto(point.elapsedMs(), point.cost()))
                .toList();

        return new RoutingResponse(
                request.problemId(), "COMPLETED", SOLVER_TYPE,
                totalCost, computationTime, seed, routes, trace);
    }

    /**
     * Semilla recibida o, en su defecto, una sorteada dentro del rango que la pasarela admite: una
     * semilla que se devuelve al cliente pero que este no puede reenviar no serviria para
     * reproducir nada.
     */
    private static long resolveSeed(Map<String, Object> parameters) {
        Object raw = (parameters != null) ? parameters.get("seed") : null;
        if (raw instanceof Number number) {
            return number.longValue();
        }
        return ThreadLocalRandom.current().nextLong(MAX_SEED + 1);
    }

    /**
     * Cada cliente al deposito mas cercano y, dentro de cada deposito, vecino mas cercano
     * aleatorizado: en cada paso se elige al azar entre los {@value #SEED_CANDIDATE_LIST} clientes
     * mas proximos, para que semillas distintas partan de soluciones distintas.
     */
    public static AnnealingSolution initialSolution(RoutingProblem problem,
                                                     RouteSplitter splitter,
                                                     PseudoRandomGenerator random) {
        List<List<Integer>> assigned = new ArrayList<>();
        for (int depot = 0; depot < problem.depotCount(); depot++) {
            assigned.add(new ArrayList<>());
        }
        for (Customer customer : problem.customers()) {
            Depot nearest = problem.depots().stream()
                    .min(Comparator.comparingDouble(depot -> problem.distance(depot, customer.index())))
                    .orElseThrow();
            assigned.get(nearest.index()).add(customer.index());
        }

        List<List<Integer>> orders = new ArrayList<>();
        for (Depot depot : problem.depots()) {
            List<Integer> remaining = new ArrayList<>(assigned.get(depot.index()));
            List<Integer> order = new ArrayList<>(remaining.size());
            int previous = -1;

            while (!remaining.isEmpty()) {
                int reference = previous;
                remaining.sort(Comparator.comparingDouble(candidate -> reference < 0
                        ? problem.distance(depot, candidate)
                        : problem.distance(reference, candidate)));
                int limit = Math.min(SEED_CANDIDATE_LIST, remaining.size());
                int chosen = remaining.remove(random.nextInt(0, limit - 1));
                order.add(chosen);
                previous = chosen;
            }
            orders.add(order);
        }

        return AnnealingSolution.of(problem, splitter, orders);
    }

    /**
     * Pulido final del resultado y, si en toda la ejecucion no aparecio ninguna solucion factible,
     * un ultimo intento de reparacion antes de rendirse.
     */
    private static AnnealingSolution polish(SimulatedAnnealing annealing,
                                            RouteOptimizer optimizer,
                                            DepotRebalancer rebalancer) {
        AnnealingSolution result = annealing.result();
        AnnealingSolution candidate = result.copy();

        int passes = annealing.foundFeasible() ? 1 : FINAL_REPAIR_PASSES;
        for (int pass = 0; pass < passes; pass++) {
            boolean changed = rebalancer.rebalance(candidate.orders());
            for (Depot depot : candidate.problem().depots()) {
                changed |= optimizer.improve(depot, candidate.order(depot.index()));
            }
            if (!changed) {
                break;
            }
            candidate.evaluateAll();
        }

        // El pulido no puede devolver algo peor que lo que ya se tenia, ni perder la factibilidad.
        boolean candidateFeasible = candidate.isFeasible();
        if (annealing.foundFeasible()) {
            return (candidateFeasible && candidate.cost() <= result.cost()) ? candidate : result;
        }
        if (candidateFeasible || candidate.cost() <= result.cost()) {
            return candidate;
        }
        return result;
    }

    private static List<RouteDto> decode(AnnealingSolution solution, RouteSplitter splitter, List<VehicleDto> vehicles) {
        Map<String, List<VehicleDto>> vehiclesByDepot = new HashMap<>();
        if (vehicles != null) {
            for (VehicleDto vehicle : vehicles) {
                vehiclesByDepot.computeIfAbsent(vehicle.startDepotId(), key -> new ArrayList<>()).add(vehicle);
            }
        }

        RoutingProblem problem = solution.problem();
        List<RouteDto> routes = new ArrayList<>();
        for (Depot depot : problem.depots()) {
            List<VehicleDto> depotVehicles = vehiclesByDepot.getOrDefault(depot.id(), List.of());
            int index = 0;
            for (Route route : splitter.routes(depot, solution.order(depot.index()))) {
                if (route.customers().isEmpty()) {
                    continue;
                }
                String vehicleId = depotVehicles.isEmpty()
                        ? "V-SA-" + depot.id() + "-" + (index + 1)
                        : depotVehicles.get(index % depotVehicles.size()).id();
                index++;

                List<String> stops = new ArrayList<>(route.customers().size());
                for (int customer : route.customers()) {
                    stops.add(problem.customer(customer).id());
                }
                routes.add(new RouteDto(vehicleId, depot.id(), stops, route.distance(), route.load()));
            }
        }
        return routes;
    }
}
