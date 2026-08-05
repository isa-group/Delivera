package com.delivera.fms.engine.genetic.service;

import com.delivera.fms.engine.genetic.dto.CustomerDto;
import com.delivera.fms.engine.genetic.dto.DepotDto;
import com.delivera.fms.engine.genetic.dto.RouteDto;
import com.delivera.fms.engine.genetic.dto.RoutingRequest;
import com.delivera.fms.engine.genetic.dto.RoutingResponse;
import com.delivera.fms.engine.genetic.dto.VehicleDto;
import com.delivera.fms.engine.genetic.operator.crossover.BestCostRouteCrossover;
import com.delivera.fms.engine.genetic.operator.mutation.InterDepotMutation;
import com.delivera.fms.engine.genetic.operator.mutation.IntraDepotMutation;
import com.delivera.fms.engine.genetic.operator.search.InterDepotLocalSearch;
import com.delivera.fms.engine.genetic.operator.search.LocalSearch;
import com.delivera.fms.engine.genetic.scheduler.PermutationCodec;
import com.delivera.fms.engine.genetic.scheduler.RouteScheduler;
import com.delivera.fms.engine.genetic.scheduler.RouteSplitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.uma.jmetal.solution.permutationsolution.PermutationSolution;
import org.uma.jmetal.solution.permutationsolution.impl.IntegerPermutationSolution;
import org.uma.jmetal.util.pseudorandom.JMetalRandom;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GeneticRouteSolver {

    private static final Logger log = LoggerFactory.getLogger(GeneticRouteSolver.class);
    private static final String SOLVER_TYPE = "GENETIC";
    private static final int POPULATION_SIZE = 150;
    private static final int MAX_EVALUATIONS = 75000;
    private static final int MIN_GENERATIONS = 100;
    private static final int INTER_DEPOT_FREQUENCY = 5;
    private static final int INTER_DEPOT_INDIVIDUALS = 10;
    private static final int RESTART_STAGNANT = 20;
    private static final int MAX_RESTARTS = 3; // Maximo numero de reinicios de poblacion antes de parar la ejecucion.
    private static final int ELITISM_COUNT = 5;
    private static final int LOCAL_SEARCH_FREQUENCY = 10;
    private static final int INTER_DEPOT_OPT_FREQUENCY = 30;
    private static final int TOP_K_LOCAL_SEARCH = 3;
    private static final double HEURISTIC_SEED_RATIO = 0.2;
    private static final int SEED_CANDIDATE_LIST = 3;

    public RoutingResponse solve(RoutingRequest request) {
        log.info("Solving MD-CVRP problem '{}' with solver: {}", request.problemId(), SOLVER_TYPE);
        long startTime = System.currentTimeMillis();

        RouteSplitter splitter = new RouteSplitter(
                request.customers(), request.distanceMatrix(),
                capacityByDepot(request), fleetByDepot(request));
        MDCVRPProblem problem = new MDCVRPProblem(request, splitter);
        RouteScheduler scheduler = new RouteScheduler(
                request.customers(), request.distanceMatrix(), splitter, request.vehicles());

        BestCostRouteCrossover crossover = new BestCostRouteCrossover(0.9, request.depots(), splitter);
        IntraDepotMutation intraMutation = new IntraDepotMutation(0.2, request.depots());
        InterDepotMutation interMutation = new InterDepotMutation(
                0.3, request.customers(), request.depots(), request.distanceMatrix(), splitter);
        LocalSearch localSearch = new LocalSearch(
                request.customers(), request.depots(), request.distanceMatrix(), splitter);
        InterDepotLocalSearch interDepotSearch = new InterDepotLocalSearch(
                request.customers(), request.depots(), request.distanceMatrix(), splitter);

        List<PermutationSolution<Integer>> population = initializePopulation(problem);
        evaluatePopulation(population, problem);

        int evaluations = population.size();
        int generation = 0;
        int stagnantGenerations = 0;
        int restarts = 0;
        PermutationSolution<Integer> bestSolution = copySolution(findBest(population));
        double bestFitness = bestSolution.objectives()[0];
        int bestGeneration = 0;

        while (evaluations < MAX_EVALUATIONS || generation < MIN_GENERATIONS) {
            List<PermutationSolution<Integer>> elites = selectElites(population, ELITISM_COUNT);
            List<PermutationSolution<Integer>> offspring = new ArrayList<>();

            while (offspring.size() < POPULATION_SIZE) {
                PermutationSolution<Integer> parent1 = tournamentSelect(population, null);
                PermutationSolution<Integer> parent2 = tournamentSelect(population, parent1);

                for (PermutationSolution<Integer> child : crossover.execute(List.of(parent1, parent2))) {
                    if (offspring.size() >= POPULATION_SIZE) {
                        break;
                    }
                    intraMutation.execute(child);
                    offspring.add(child);
                }
            }

            evaluatePopulation(offspring, problem);
            evaluations += offspring.size();

            if (generation % INTER_DEPOT_FREQUENCY == 0 && generation > 0) {
                for (int i = 0; i < Math.min(INTER_DEPOT_INDIVIDUALS, offspring.size()); i++) {
                    int index = JMetalRandom.getInstance().nextInt(0, offspring.size() - 1);
                    interMutation.execute(offspring.get(index));
                    problem.evaluate(offspring.get(index));
                    evaluations++;
                }
            }

            replaceWorstWithElites(offspring, elites);
            population = offspring;

            if (generation > 0 && generation % LOCAL_SEARCH_FREQUENCY == 0) {
                applyLocalSearchToTopK(population, problem, localSearch, TOP_K_LOCAL_SEARCH);
                evaluations += TOP_K_LOCAL_SEARCH;
            }

            if (generation > 0 && generation % INTER_DEPOT_OPT_FREQUENCY == 0) {
                PermutationSolution<Integer> candidate = copySolution(bestSolution);
                if (interDepotSearch.optimize(candidate)) {
                    localSearch.improveSolution(candidate);
                    problem.evaluate(candidate);
                    evaluations++;
                    if (candidate.objectives()[0] < bestFitness) {
                        population.set(worstIndex(population), candidate);
                    }
                }
            }

            PermutationSolution<Integer> generationBest = findBest(population);
            if (generationBest.objectives()[0] < bestFitness) {
                bestSolution = copySolution(generationBest);
                bestFitness = bestSolution.objectives()[0];
                bestGeneration = generation;
                stagnantGenerations = 0;
            } else {
                stagnantGenerations++;
            }

            generation++;

            if (generation >= MIN_GENERATIONS && stagnantGenerations >= RESTART_STAGNANT) {
                if (restarts >= MAX_RESTARTS) {
                    log.debug("Early stopping at gen {} after {} restarts. Best at gen {}.",
                            generation, restarts, bestGeneration);
                    break;
                }
                log.debug("Restarting population at gen {} after {} stagnant gens.", generation, stagnantGenerations);
                population = restartPopulation(problem, bestSolution, ELITISM_COUNT);
                evaluatePopulation(population, problem);
                evaluations += population.size();
                stagnantGenerations = 0;
                restarts++;
            }
        }

        // Pulido final: deja el mejor cromosoma en un optimo local antes de decodificarlo.
        interDepotSearch.optimize(bestSolution);
        localSearch.improveSolution(bestSolution);
        problem.evaluate(bestSolution);

        List<RouteDto> routes = decodeSolution(bestSolution, request, scheduler);
        double totalCost = routes.stream().mapToDouble(RouteDto::totalDistance).sum();
        long computationTime = System.currentTimeMillis() - startTime;

        log.info("Problem '{}' solved. Routes: {}, Total cost: {}, Time: {}ms, Generations: {}, Evaluations: {}",
                request.problemId(), routes.size(), totalCost, computationTime, generation, evaluations);

        return new RoutingResponse(
                request.problemId(), "COMPLETED", SOLVER_TYPE,
                totalCost, computationTime, routes
        );
    }

    /** Capacidad del mayor vehiculo de cada deposito; sin vehiculos declarados, sin limite. */
    private static Map<DepotDto, Integer> capacityByDepot(RoutingRequest request) {
        Map<String, List<VehicleDto>> byDepot = vehiclesByDepot(request);
        Map<DepotDto, Integer> capacities = new HashMap<>();
        for (DepotDto depot : request.depots()) {
            capacities.put(depot, byDepot.getOrDefault(depot.id(), List.of()).stream()
                    .mapToInt(VehicleDto::capacity)
                    .max()
                    .orElse(Integer.MAX_VALUE));
        }
        return capacities;
    }

    /** Numero de vehiculos de cada deposito; sin vehiculos declarados, sin limite. */
    private static Map<DepotDto, Integer> fleetByDepot(RoutingRequest request) {
        Map<String, List<VehicleDto>> byDepot = vehiclesByDepot(request);
        Map<DepotDto, Integer> fleets = new HashMap<>();
        for (DepotDto depot : request.depots()) {
            List<VehicleDto> vehicles = byDepot.getOrDefault(depot.id(), List.of());
            fleets.put(depot, vehicles.isEmpty() ? Integer.MAX_VALUE : vehicles.size());
        }
        return fleets;
    }

    private static Map<String, List<VehicleDto>> vehiclesByDepot(RoutingRequest request) {
        Map<String, List<VehicleDto>> byDepot = new HashMap<>();
        if (request.vehicles() != null) {
            for (VehicleDto vehicle : request.vehicles()) {
                byDepot.computeIfAbsent(vehicle.startDepotId(), key -> new ArrayList<>()).add(vehicle);
            }
        }
        return byDepot;
    }

    private List<PermutationSolution<Integer>> initializePopulation(MDCVRPProblem problem) {
        List<PermutationSolution<Integer>> population = new ArrayList<>();
        JMetalRandom random = JMetalRandom.getInstance();

        int heuristicCount = (int) (POPULATION_SIZE * HEURISTIC_SEED_RATIO);
        for (int i = 0; i < heuristicCount; i++) {
            population.add(createHeuristicSolution(problem, random));
        }
        while (population.size() < POPULATION_SIZE) {
            population.add(createRandomSolution(problem, random));
        }

        return population;
    }

    private PermutationSolution<Integer> createRandomSolution(MDCVRPProblem problem, JMetalRandom random) {
        PermutationSolution<Integer> solution = problem.createSolution();
        List<Integer> permutation = new ArrayList<>();
        for (int i = 0; i < problem.length(); i++) {
            permutation.add(i);
        }
        for (int i = permutation.size() - 1; i > 0; i--) {
            int j = random.nextInt(0, i);
            int tmp = permutation.get(i);
            permutation.set(i, permutation.get(j));
            permutation.set(j, tmp);
        }
        for (int i = 0; i < permutation.size(); i++) {
            solution.variables().set(i, permutation.get(i));
        }
        return solution;
    }

    /**
     * Vecino mas cercano aleatorizado: en cada paso elige al azar entre los
     * {@value #SEED_CANDIDATE_LIST} clientes mas proximos. Un vecino mas cercano puro seria
     * deterministico y sembraria la poblacion con individuos identicos.
     */
    private PermutationSolution<Integer> createHeuristicSolution(MDCVRPProblem problem, JMetalRandom random) {
        PermutationSolution<Integer> solution = problem.createSolution();
        List<CustomerDto> customers = problem.customers();
        double[][] distanceMatrix = problem.distanceMatrix();
        Map<Integer, DepotDto> depotMap = PermutationCodec.depotMap(solution);

        Map<DepotDto, List<Integer>> depotOrder = new LinkedHashMap<>();
        for (DepotDto depot : problem.depots()) {
            depotOrder.put(depot, new ArrayList<>());
        }
        for (int i = 0; i < customers.size(); i++) {
            depotOrder.get(depotMap.get(i)).add(i);
        }

        List<Integer> permutation = new ArrayList<>(customers.size());
        for (var entry : depotOrder.entrySet()) {
            List<Integer> remaining = new ArrayList<>(entry.getValue());
            int currentIndex = entry.getKey().matrixIndex();

            while (!remaining.isEmpty()) {
                int reference = currentIndex;
                remaining.sort(Comparator.comparingDouble(
                        candidate -> distanceMatrix[reference][customers.get(candidate).matrixIndex()]));

                int limit = Math.min(SEED_CANDIDATE_LIST, remaining.size());
                int chosen = remaining.remove(random.nextInt(0, limit - 1));
                permutation.add(chosen);
                currentIndex = customers.get(chosen).matrixIndex();
            }
        }

        for (int i = 0; i < permutation.size(); i++) {
            solution.variables().set(i, permutation.get(i));
        }
        return solution;
    }

    private void evaluatePopulation(List<PermutationSolution<Integer>> population, MDCVRPProblem problem) {
        for (PermutationSolution<Integer> solution : population) {
            problem.evaluate(solution);
        }
    }

    private PermutationSolution<Integer> tournamentSelect(List<PermutationSolution<Integer>> population,
                                                          PermutationSolution<Integer> exclude) {
        JMetalRandom random = JMetalRandom.getInstance();
        PermutationSolution<Integer> best = null;

        for (int i = 0; i < 3; i++) {
            PermutationSolution<Integer> candidate = population.get(random.nextInt(0, population.size() - 1));
            if (candidate == exclude) {
                continue;
            }
            if (best == null || candidate.objectives()[0] < best.objectives()[0]) {
                best = candidate;
            }
        }

        return (best != null) ? best : population.get(random.nextInt(0, population.size() - 1));
    }

    private PermutationSolution<Integer> findBest(List<PermutationSolution<Integer>> population) {
        return population.stream()
                .min(Comparator.comparingDouble(solution -> solution.objectives()[0]))
                .orElse(population.get(0));
    }

    private int worstIndex(List<PermutationSolution<Integer>> population) {
        int worst = 0;
        for (int i = 1; i < population.size(); i++) {
            if (population.get(i).objectives()[0] > population.get(worst).objectives()[0]) {
                worst = i;
            }
        }
        return worst;
    }

    /** Copias de los {@code count} mejores individuos distintos, no {@code count} clones del mejor. */
    private List<PermutationSolution<Integer>> selectElites(List<PermutationSolution<Integer>> population, int count) {
        return population.stream()
                .sorted(Comparator.comparingDouble(solution -> solution.objectives()[0]))
                .limit(count)
                .map(this::copySolution)
                .toList();
    }

    private void replaceWorstWithElites(List<PermutationSolution<Integer>> offspring,
                                         List<PermutationSolution<Integer>> elites) {
        List<Integer> worstIndices = findWorstIndices(offspring, elites.size());
        for (int i = 0; i < worstIndices.size(); i++) {
            offspring.set(worstIndices.get(i), elites.get(i));
        }
    }

    private List<Integer> findWorstIndices(List<PermutationSolution<Integer>> population, int count) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < population.size(); i++) {
            indices.add(i);
        }
        indices.sort((a, b) -> Double.compare(
                population.get(b).objectives()[0], population.get(a).objectives()[0]));
        return indices.subList(0, Math.min(count, indices.size()));
    }

    private void applyLocalSearchToTopK(List<PermutationSolution<Integer>> population,
                                         MDCVRPProblem problem,
                                         LocalSearch localSearch,
                                         int k) {
        List<PermutationSolution<Integer>> sorted = new ArrayList<>(population);
        sorted.sort(Comparator.comparingDouble(solution -> solution.objectives()[0]));

        for (int i = 0; i < Math.min(k, sorted.size()); i++) {
            PermutationSolution<Integer> solution = sorted.get(i);
            localSearch.improveSolution(solution);
            problem.evaluate(solution);
        }
    }

    private List<PermutationSolution<Integer>> restartPopulation(MDCVRPProblem problem,
                                                                  PermutationSolution<Integer> bestSolution,
                                                                  int keepCount) {
        JMetalRandom random = JMetalRandom.getInstance();
        List<PermutationSolution<Integer>> newPopulation = new ArrayList<>();
        for (int i = 0; i < Math.max(1, keepCount); i++) {
            newPopulation.add(copySolution(bestSolution));
        }

        int heuristicCount = (int) ((POPULATION_SIZE - newPopulation.size()) * HEURISTIC_SEED_RATIO);
        for (int i = 0; i < heuristicCount; i++) {
            newPopulation.add(createHeuristicSolution(problem, random));
        }
        while (newPopulation.size() < POPULATION_SIZE) {
            newPopulation.add(createRandomSolution(problem, random));
        }

        return newPopulation;
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

        Map<Integer, DepotDto> sourceMap = PermutationCodec.depotMap(source);
        if (sourceMap != null) {
            copy.attributes().put(PermutationCodec.DEPOT_MAP, new HashMap<>(sourceMap));
        }

        return copy;
    }

    private List<RouteDto> decodeSolution(PermutationSolution<Integer> solution,
                                           RoutingRequest request,
                                           RouteScheduler scheduler) {
        Map<Integer, DepotDto> depotMap = PermutationCodec.depotMap(solution);
        Map<DepotDto, List<Integer>> depotOrder =
                PermutationCodec.depotOrder(solution, request.depots(), depotMap);

        List<RouteDto> routes = new ArrayList<>();
        for (var entry : depotOrder.entrySet()) {
            routes.addAll(scheduler.buildRoutes(entry.getKey(), entry.getValue()));
        }
        return routes;
    }

    static class MDCVRPProblem {

        private final List<CustomerDto> customers;
        private final List<DepotDto> depots;
        private final double[][] distanceMatrix;
        private final RouteSplitter splitter;

        MDCVRPProblem(RoutingRequest request, RouteSplitter splitter) {
            this.customers = request.customers();
            this.depots = request.depots();
            this.distanceMatrix = request.distanceMatrix();
            this.splitter = splitter;
        }

        PermutationSolution<Integer> createSolution() {
            PermutationSolution<Integer> solution = new IntegerPermutationSolution(customers.size(), 1, 0);

            Map<Integer, DepotDto> depotMap = new HashMap<>();
            for (int i = 0; i < customers.size(); i++) {
                CustomerDto customer = customers.get(i);
                DepotDto nearest = depots.stream()
                        .min(Comparator.comparingDouble(
                                depot -> distanceMatrix[depot.matrixIndex()][customer.matrixIndex()]))
                        .orElse(depots.get(0));
                depotMap.put(i, nearest);
            }

            solution.attributes().put(PermutationCodec.DEPOT_MAP, depotMap);
            return solution;
        }

        PermutationSolution<Integer> evaluate(PermutationSolution<Integer> solution) {
            Map<Integer, DepotDto> depotMap = PermutationCodec.depotMap(solution);
            Map<DepotDto, List<Integer>> depotOrder =
                    PermutationCodec.depotOrder(solution, depots, depotMap);

            double totalDistance = 0.0;
            for (var entry : depotOrder.entrySet()) {
                DepotDto depot = entry.getKey();
                RouteSplitter.Split split = splitter.evaluate(depot, entry.getValue());
                totalDistance += split.cost()
                        + RouteSplitter.fleetPenalty(split.routeCount(), splitter.fleet(depot));
            }

            solution.objectives()[0] = totalDistance;
            return solution;
        }

        int length() {
            return customers.size();
        }

        List<CustomerDto> customers() {
            return customers;
        }

        List<DepotDto> depots() {
            return depots;
        }

        double[][] distanceMatrix() {
            return distanceMatrix;
        }
    }
}
