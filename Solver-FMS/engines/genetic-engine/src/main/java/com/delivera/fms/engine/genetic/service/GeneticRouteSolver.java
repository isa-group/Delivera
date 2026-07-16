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
import com.delivera.fms.engine.genetic.scheduler.RouteScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.uma.jmetal.solution.permutationsolution.PermutationSolution;
import org.uma.jmetal.solution.permutationsolution.impl.IntegerPermutationSolution;
import org.uma.jmetal.util.pseudorandom.JMetalRandom;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private static final int ELITISM_COUNT = 5;
    private static final int LOCAL_SEARCH_FREQUENCY = 25;
    private static final int INTER_DEPOT_OPT_FREQUENCY = 30;
    private static final int TOP_K_LOCAL_SEARCH = 3;
    private static final double HEURISTIC_SEED_RATIO = 0.2;

    public RoutingResponse solve(RoutingRequest request) {
        log.info("Solving MD-CVRP problem '{}' with solver: {}", request.problemId(), SOLVER_TYPE);
        long startTime = System.currentTimeMillis();

        MDCVRPProblem problem = new MDCVRPProblem(request);
        RouteScheduler scheduler = new RouteScheduler(
                request.customers(), request.depots(), request.distanceMatrix(), request.vehicles());

        BestCostRouteCrossover crossover = new BestCostRouteCrossover(
                0.9, request.customers(), request.depots(), request.distanceMatrix());
        IntraDepotMutation intraMutation = new IntraDepotMutation(
                0.2, request.customers(), request.depots());
        InterDepotMutation interMutation = new InterDepotMutation(
                0.3, request.customers(), request.depots(), request.distanceMatrix());
        LocalSearch localSearch = new LocalSearch(
                request.customers(), request.depots(), request.distanceMatrix());
        InterDepotLocalSearch interDepotSearch = new InterDepotLocalSearch(
                request.customers(), request.depots(), request.distanceMatrix());

        List<PermutationSolution<Integer>> population = initializePopulation(problem);
        evaluatePopulation(population, problem);

        int evaluations = population.size();
        int generation = 0;
        int stagnantGenerations = 0;
        boolean hasRestarted = false;
        PermutationSolution<Integer> bestSolution = findBest(population);
        double bestFitness = bestSolution.objectives()[0];
        int bestGeneration = 0;

        while (evaluations < MAX_EVALUATIONS || generation < MIN_GENERATIONS) {
            List<PermutationSolution<Integer>> offspring = new ArrayList<>();

            while (offspring.size() < POPULATION_SIZE) {
                PermutationSolution<Integer> parent1 = tournamentSelect(population);
                PermutationSolution<Integer> parent2 = tournamentSelect(population);

                List<PermutationSolution<Integer>> children = crossover.execute(List.of(parent1, parent2));

                for (int i = 0; i < children.size(); i++) {
                    PermutationSolution<Integer> child = children.get(i);

                    if (i == 0) {
                        @SuppressWarnings("unchecked")
                        Map<Integer, DepotDto> parent1Map = (Map<Integer, DepotDto>) parent1.attributes().get("depotMap");
                        child.attributes().put("depotMap", new HashMap<>(parent1Map));
                    }

                    if (offspring.size() >= POPULATION_SIZE) break;

                    intraMutation.execute(child);
                    offspring.add(child);
                }
            }

            if (offspring.size() > POPULATION_SIZE) {
                offspring = new ArrayList<>(offspring.subList(0, POPULATION_SIZE));
            }

            evaluatePopulation(offspring, problem);
            evaluations += offspring.size();

            if (generation % INTER_DEPOT_FREQUENCY == 0 && generation > 0) {
                for (int i = 0; i < Math.min(INTER_DEPOT_INDIVIDUALS, offspring.size()); i++) {
                    int idx = JMetalRandom.getInstance().nextInt(0, offspring.size() - 1);
                    interMutation.execute(offspring.get(idx));
                    problem.evaluate(offspring.get(idx));
                    evaluations++;
                }
            }

            List<Integer> worstIndices = findWorstIndices(offspring, ELITISM_COUNT);
            replaceWorstWithElites(offspring, worstIndices, bestSolution);

            population = offspring;
            PermutationSolution<Integer> genBest = findBest(population);
            double genBestFitness = genBest.objectives()[0];
            if (genBestFitness < bestFitness) {
                bestSolution = genBest;
                bestFitness = genBestFitness;
                bestGeneration = generation;
                stagnantGenerations = 0;
            } else {
                stagnantGenerations++;
            }

            if (generation > 0 && generation % LOCAL_SEARCH_FREQUENCY == 0) {
                applyLocalSearchToTopK(population, problem, localSearch, TOP_K_LOCAL_SEARCH);
                evaluations += TOP_K_LOCAL_SEARCH;
                PermutationSolution<Integer> lsBest = findBest(population);
                double lsFitness = lsBest.objectives()[0];
                if (lsFitness < bestFitness) {
                    bestSolution = lsBest;
                    bestFitness = lsFitness;
                    bestGeneration = generation;
                    stagnantGenerations = 0;
                }
            }

            if (generation > 0 && generation % INTER_DEPOT_OPT_FREQUENCY == 0) {
                PermutationSolution<Integer> candidate = copySolution(bestSolution);
                boolean moved = interDepotSearch.optimize(candidate);
                if (moved) {
                    problem.evaluate(candidate);
                    evaluations++;
                    localSearch.improveSolution(candidate);
                    problem.evaluate(candidate);
                    evaluations++;
                    if (candidate.objectives()[0] < bestFitness) {
                        bestSolution = candidate;
                        bestFitness = candidate.objectives()[0];
                        bestGeneration = generation;
                        stagnantGenerations = 0;
                    }
                }
            }

            generation++;

            if (generation >= MIN_GENERATIONS && stagnantGenerations >= RESTART_STAGNANT) {
                if (hasRestarted) {
                    log.debug("Early stopping at gen {} after second stagnation. Best at gen {}.", generation, bestGeneration);
                    break;
                }
                log.debug("Restarting population at gen {} after {} stagnant gens.", generation, stagnantGenerations);
                population = restartPopulation(problem, bestSolution, ELITISM_COUNT);
                evaluatePopulation(population, problem);
                evaluations += POPULATION_SIZE;
                stagnantGenerations = 0;
                hasRestarted = true;
            }
        }

        List<RouteDto> routes = decodeSolution(bestSolution, request, problem, scheduler);
        double totalCost = routes.stream().mapToDouble(RouteDto::totalDistance).sum();
        long computationTime = System.currentTimeMillis() - startTime;

        log.info("Problem '{}' solved. Routes: {}, Total cost: {}, Time: {}ms, Generations: {}, Evaluations: {}",
                request.problemId(), routes.size(), totalCost, computationTime, generation, evaluations);

        return new RoutingResponse(
                request.problemId(), "COMPLETED", SOLVER_TYPE,
                totalCost, computationTime, routes
        );
    }

    private List<PermutationSolution<Integer>> initializePopulation(MDCVRPProblem problem) {
        List<PermutationSolution<Integer>> population = new ArrayList<>();
        JMetalRandom random = JMetalRandom.getInstance();

        int heuristicCount = (int) (POPULATION_SIZE * HEURISTIC_SEED_RATIO);
        for (int i = 0; i < heuristicCount; i++) {
            population.add(createHeuristicSolution(problem, random));
        }

        for (int i = population.size(); i < POPULATION_SIZE; i++) {
            population.add(createRandomSolution(problem, random));
        }

        return population;
    }

    private PermutationSolution<Integer> createRandomSolution(MDCVRPProblem problem, JMetalRandom random) {
        PermutationSolution<Integer> solution = problem.createSolution();
        List<Integer> perm = new ArrayList<>();
        for (int j = 0; j < problem.length(); j++) {
            perm.add(j);
        }
        for (int j = perm.size() - 1; j > 0; j--) {
            int k = random.nextInt(0, j);
            int tmp = perm.get(j);
            perm.set(j, perm.get(k));
            perm.set(k, tmp);
        }
        for (int j = 0; j < perm.size(); j++) {
            solution.variables().set(j, perm.get(j));
        }
        return solution;
    }

    private PermutationSolution<Integer> createHeuristicSolution(MDCVRPProblem problem, JMetalRandom random) {
        PermutationSolution<Integer> solution = problem.createSolution();
        List<CustomerDto> customers = problem.customers();
        List<DepotDto> depots = problem.depots();
        double[][] dist = problem.distanceMatrix();

        @SuppressWarnings("unchecked")
        Map<Integer, DepotDto> depotMap = (Map<Integer, DepotDto>) solution.attributes().get("depotMap");

        Map<DepotDto, List<Integer>> depotCustomers = new LinkedHashMap<>();
        for (DepotDto d : depots) {
            depotCustomers.put(d, new ArrayList<>());
        }
        for (int j = 0; j < customers.size(); j++) {
            DepotDto d = depotMap.get(j);
            if (d != null) {
                depotCustomers.get(d).add(j);
            }
        }

        List<Integer> permutation = new ArrayList<>();
        for (var entry : depotCustomers.entrySet()) {
            DepotDto depot = entry.getKey();
            List<Integer> customerList = new ArrayList<>(entry.getValue());

            if (customerList.size() <= 1) {
                permutation.addAll(customerList);
                continue;
            }

            int currentMatrixIdx = depot.matrixIndex();
            Set<Integer> remaining = new HashSet<>(customerList);
            List<Integer> ordered = new ArrayList<>();

            while (!remaining.isEmpty()) {
                int bestCustomer = -1;
                double bestDist = Double.MAX_VALUE;
                for (int cIdx : remaining) {
                    double d = dist[currentMatrixIdx][customers.get(cIdx).matrixIndex()];
                    if (d < bestDist) {
                        bestDist = d;
                        bestCustomer = cIdx;
                    }
                }
                ordered.add(bestCustomer);
                currentMatrixIdx = customers.get(bestCustomer).matrixIndex();
                remaining.remove(bestCustomer);
            }
            permutation.addAll(ordered);
        }

        for (int j = 0; j < permutation.size(); j++) {
            solution.variables().set(j, permutation.get(j));
        }
        return solution;
    }

    private void evaluatePopulation(List<PermutationSolution<Integer>> population, MDCVRPProblem problem) {
        for (PermutationSolution<Integer> solution : population) {
            problem.evaluate(solution);
        }
    }

    private PermutationSolution<Integer> tournamentSelect(List<PermutationSolution<Integer>> population) {
        JMetalRandom random = JMetalRandom.getInstance();
        int a = random.nextInt(0, population.size() - 1);
        int b = random.nextInt(0, population.size() - 1);
        int c = random.nextInt(0, population.size() - 1);
        PermutationSolution<Integer> best = population.get(a);
        if (population.get(b).objectives()[0] < best.objectives()[0]) best = population.get(b);
        if (population.get(c).objectives()[0] < best.objectives()[0]) best = population.get(c);
        return best;
    }

    private PermutationSolution<Integer> findBest(List<PermutationSolution<Integer>> population) {
        return population.stream()
                .min(Comparator.comparingDouble(s -> s.objectives()[0]))
                .orElse(population.get(0));
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

    private void replaceWorstWithElites(List<PermutationSolution<Integer>> offspring,
                                         List<Integer> worstIndices,
                                         PermutationSolution<Integer> bestSolution) {
        PermutationSolution<Integer> elite = copySolution(bestSolution);
        for (int idx : worstIndices) {
            offspring.set(idx, idx == worstIndices.get(0) ? elite : copySolution(bestSolution));
        }
    }

    private void applyLocalSearchToTopK(List<PermutationSolution<Integer>> population,
                                         MDCVRPProblem problem,
                                         LocalSearch localSearch,
                                         int k) {
        List<PermutationSolution<Integer>> sorted = new ArrayList<>(population);
        sorted.sort(Comparator.comparingDouble(s -> s.objectives()[0]));

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
        newPopulation.add(copySolution(bestSolution));

        int heuristicCount = (int) ((POPULATION_SIZE - keepCount) * HEURISTIC_SEED_RATIO);
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
        
        @SuppressWarnings("unchecked")
        Map<Integer, DepotDto> sourceMap = (Map<Integer, DepotDto>) source.attributes().get("depotMap");
        if (sourceMap != null) {
            copy.attributes().put("depotMap", new HashMap<>(sourceMap));
        }
        
        return copy;
    }

    private List<RouteDto> decodeSolution(PermutationSolution<Integer> solution,
                                           RoutingRequest request,
                                           MDCVRPProblem problem,
                                           RouteScheduler scheduler) {
        List<CustomerDto> customers = request.customers();
        List<DepotDto> depots = request.depots();
        List<VehicleDto> vehicles = request.vehicles();
        boolean hasVehicles = vehicles != null && !vehicles.isEmpty();

        Map<DepotDto, List<Integer>> depotOrder = problem.buildDepotOrder(solution);

        List<RouteDto> routes = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        for (var entry : depotOrder.entrySet()) {
            DepotDto depot = entry.getKey();
            List<Integer> order = entry.getValue();

            List<RouteDto> depotRoutes = scheduler.buildRoutes(depot, order);
            for (RouteDto route : depotRoutes) {
                for (String stop : route.stops()) {
                    visited.add(stop);
                }
                routes.add(route);
            }
        }

        List<CustomerDto> unvisited = customers.stream()
                .filter(c -> !visited.contains(c.id()))
                .toList();
        if (!unvisited.isEmpty()) {
            DepotDto fallbackDepot = depots.get(0);
            String fallbackVehicleId = "V-GA-FALLBACK-" + fallbackDepot.id();
            List<String> stops = new ArrayList<>();
            double totalDistance = 0.0;
            int totalLoad = 0;
            int currentIndex = fallbackDepot.matrixIndex();
            double[][] dist = request.distanceMatrix();

            for (CustomerDto c : unvisited) {
                totalDistance += dist[currentIndex][c.matrixIndex()];
                stops.add(c.id());
                totalLoad += c.demand();
                currentIndex = c.matrixIndex();
            }
            totalDistance += dist[currentIndex][fallbackDepot.matrixIndex()];
            routes.add(new RouteDto(fallbackVehicleId, fallbackDepot.id(), stops, totalDistance, totalLoad));
        }

        return routes;
    }

    static class MDCVRPProblem {

        private final List<CustomerDto> customers;
        private final List<DepotDto> depots;
        private final double[][] distanceMatrix;
        private final Map<DepotDto, Integer> depotCapacity;

        MDCVRPProblem(RoutingRequest request) {
            this.customers = request.customers();
            this.depots = request.depots();
            this.distanceMatrix = request.distanceMatrix();

            boolean hasVehicles = request.vehicles() != null && !request.vehicles().isEmpty();
            this.depotCapacity = new HashMap<>();
            if (hasVehicles) {
                Map<String, List<VehicleDto>> vehiclesByDepot = new HashMap<>();
                for (VehicleDto v : request.vehicles()) {
                    vehiclesByDepot.computeIfAbsent(v.startDepotId(), k -> new ArrayList<>()).add(v);
                }
                for (DepotDto d : depots) {
                    List<VehicleDto> dv = vehiclesByDepot.getOrDefault(d.id(), List.of());
                    int maxCap = dv.isEmpty()
                            ? Integer.MAX_VALUE
                            : dv.stream().mapToInt(VehicleDto::capacity).max().orElse(Integer.MAX_VALUE);
                    depotCapacity.put(d, maxCap);
                }
            } else {
                for (DepotDto d : depots) {
                    depotCapacity.put(d, Integer.MAX_VALUE);
                }
            }
        }

        Map<DepotDto, List<Integer>> buildDepotOrder(PermutationSolution<Integer> solution) {
            Map<DepotDto, List<Integer>> depotOrder = new LinkedHashMap<>();
            for (DepotDto d : depots) {
                depotOrder.put(d, new ArrayList<>());
            }
            
            @SuppressWarnings("unchecked")
            Map<Integer, DepotDto> localDepotMap = (Map<Integer, DepotDto>) solution.attributes().get("depotMap");

            for (int i = 0; i < solution.variables().size(); i++) {
                int cIdx = solution.variables().get(i);
                DepotDto depot = localDepotMap.get(cIdx);
                if (depot != null) {
                    depotOrder.get(depot).add(cIdx);
                }
            }
            return depotOrder;
        }

        PermutationSolution<Integer> createSolution() {
            PermutationSolution<Integer> solution = new IntegerPermutationSolution(customers.size(), 1, 0);
            
            Map<Integer, DepotDto> localDepotMap = new HashMap<>();
            for (int i = 0; i < customers.size(); i++) {
                CustomerDto c = customers.get(i);
                DepotDto nearest = depots.stream()
                        .min(Comparator.comparingDouble(d -> distanceMatrix[d.matrixIndex()][c.matrixIndex()]))
                        .orElse(depots.get(0));
                localDepotMap.put(i, nearest);
            }
            
            solution.attributes().put("depotMap", localDepotMap);
            return solution;
        }

        PermutationSolution<Integer> evaluate(PermutationSolution<Integer> solution) {
            Map<DepotDto, List<Integer>> depotOrder = buildDepotOrder(solution);

            double totalDistance = 0.0;
            int routeCount = 0;
            for (var entry : depotOrder.entrySet()) {
                DepotDto depot = entry.getKey();
                List<Integer> order = entry.getValue();
                int capacity = depotCapacity.getOrDefault(depot, Integer.MAX_VALUE);

                int currentLoad = 0;
                int currentIndex = depot.matrixIndex();
                boolean routeOpen = false;

                for (int cIdx : order) {
                    CustomerDto customer = customers.get(cIdx);

                    if (currentLoad + customer.demand() > capacity && currentLoad > 0) {
                        totalDistance += distanceMatrix[currentIndex][depot.matrixIndex()];
                        currentIndex = depot.matrixIndex();
                        currentLoad = 0;
                        routeCount++;
                        routeOpen = false;
                    }

                    totalDistance += distanceMatrix[currentIndex][customer.matrixIndex()];
                    currentLoad += customer.demand();
                    currentIndex = customer.matrixIndex();
                    routeOpen = true;
                }

                if (routeOpen) {
                    totalDistance += distanceMatrix[currentIndex][depot.matrixIndex()];
                    routeCount++;
                }
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
