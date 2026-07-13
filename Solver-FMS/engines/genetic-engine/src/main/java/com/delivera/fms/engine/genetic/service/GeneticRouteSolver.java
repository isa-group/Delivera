package com.delivera.fms.engine.genetic.service;

import com.delivera.fms.engine.genetic.dto.CustomerDto;
import com.delivera.fms.engine.genetic.dto.DepotDto;
import com.delivera.fms.engine.genetic.dto.RouteDto;
import com.delivera.fms.engine.genetic.dto.RoutingRequest;
import com.delivera.fms.engine.genetic.dto.RoutingResponse;
import com.delivera.fms.engine.genetic.dto.VehicleDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.algorithm.singleobjective.geneticalgorithm.GeneticAlgorithmBuilder;
import org.uma.jmetal.operator.crossover.CrossoverOperator;
import org.uma.jmetal.operator.crossover.impl.PMXCrossover;
import org.uma.jmetal.operator.mutation.MutationOperator;
import org.uma.jmetal.operator.mutation.impl.PermutationSwapMutation;
import org.uma.jmetal.operator.selection.SelectionOperator;
import org.uma.jmetal.operator.selection.impl.BinaryTournamentSelection;
import org.uma.jmetal.problem.permutationproblem.impl.AbstractIntegerPermutationProblem;
import org.uma.jmetal.solution.permutationsolution.PermutationSolution;
import org.uma.jmetal.util.comparator.ObjectiveComparator;

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
    private static final int POPULATION_SIZE = 100;
    private static final int MAX_EVALUATIONS = 25000;

    public RoutingResponse solve(RoutingRequest request) {
        log.info("Solving MD-CVRP problem '{}' with solver: {}", request.problemId(), SOLVER_TYPE);
        long startTime = System.currentTimeMillis();

        MDCVRPProblem problem = new MDCVRPProblem(request);
        Algorithm<PermutationSolution<Integer>> ga = buildGeneticAlgorithm(problem);

        ga.run();
        PermutationSolution<Integer> bestSolution = ga.result();

        List<RouteDto> routes = decodeSolution(bestSolution, request, problem);
        double totalCost = routes.stream().mapToDouble(RouteDto::totalDistance).sum();
        long computationTime = System.currentTimeMillis() - startTime;

        log.info("Problem '{}' solved. Routes: {}, Total cost: {}, Time: {}ms",
                request.problemId(), routes.size(), totalCost, computationTime);

        return new RoutingResponse(
                request.problemId(), "COMPLETED", SOLVER_TYPE,
                totalCost, computationTime, routes
        );
    }

    private Algorithm<PermutationSolution<Integer>> buildGeneticAlgorithm(MDCVRPProblem problem) {
        CrossoverOperator<PermutationSolution<Integer>> crossover = new PMXCrossover(0.9);
        MutationOperator<PermutationSolution<Integer>> mutation = new PermutationSwapMutation(0.2);
        SelectionOperator<List<PermutationSolution<Integer>>, PermutationSolution<Integer>> selection =
                new BinaryTournamentSelection<>(new ObjectiveComparator<>(0));

        return new GeneticAlgorithmBuilder<>(problem, crossover, mutation)
                .setPopulationSize(POPULATION_SIZE)
                .setMaxEvaluations(MAX_EVALUATIONS)
                .setSelectionOperator(selection)
                .build();
    }

    private List<RouteDto> decodeSolution(PermutationSolution<Integer> solution,
                                           RoutingRequest request,
                                           MDCVRPProblem problem) {
        List<CustomerDto> customers = request.customers();
        List<DepotDto> depots = request.depots();
        double[][] dist = request.distanceMatrix();
        List<VehicleDto> vehicles = request.vehicles();
        boolean hasVehicles = vehicles != null && !vehicles.isEmpty();

        Map<DepotDto, List<Integer>> depotOrder = problem.buildDepotOrder(solution);

        List<RouteDto> routes = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        Map<String, List<VehicleDto>> vehiclesByDepot = new HashMap<>();
        if (hasVehicles) {
            for (VehicleDto v : vehicles) {
                vehiclesByDepot.computeIfAbsent(v.startDepotId(), k -> new ArrayList<>()).add(v);
            }
        }

        for (var entry : depotOrder.entrySet()) {
            DepotDto depot = entry.getKey();
            List<Integer> order = entry.getValue();
            List<VehicleDto> depotVehicles = vehiclesByDepot.getOrDefault(depot.id(), List.of());

            int vehicleIdx = 0;
            int pos = 0;

            while (pos < order.size()) {
                int capacity;
                String vehicleId;
                if (hasVehicles && vehicleIdx < depotVehicles.size()) {
                    capacity = depotVehicles.get(vehicleIdx).capacity();
                    vehicleId = depotVehicles.get(vehicleIdx).id();
                } else if (hasVehicles) {
                    capacity = depotVehicles.isEmpty() ? Integer.MAX_VALUE : depotVehicles.get(0).capacity();
                    vehicleId = "V-GA-" + depot.id() + "-" + vehicleIdx;
                } else {
                    capacity = Integer.MAX_VALUE;
                    vehicleId = "V-GA-" + depot.id() + "-" + vehicleIdx;
                }

                List<String> stops = new ArrayList<>();
                double totalDistance = 0.0;
                int totalLoad = 0;
                int currentIndex = depot.matrixIndex();

                while (pos < order.size()) {
                    int cIdx = order.get(pos);
                    CustomerDto customer = customers.get(cIdx);
                    if (visited.contains(customer.id())) {
                        pos++;
                        continue;
                    }
                    if (totalLoad + customer.demand() > capacity && !stops.isEmpty()) break;

                    totalDistance += dist[currentIndex][customer.matrixIndex()];
                    stops.add(customer.id());
                    totalLoad += customer.demand();
                    currentIndex = customer.matrixIndex();
                    visited.add(customer.id());
                    pos++;
                }

                if (!stops.isEmpty()) {
                    totalDistance += dist[currentIndex][depot.matrixIndex()];
                    routes.add(new RouteDto(vehicleId, depot.id(), stops, totalDistance, totalLoad));
                }

                vehicleIdx++;
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

    static class MDCVRPProblem extends AbstractIntegerPermutationProblem {

        private final List<CustomerDto> customers;
        private final List<DepotDto> depots;
        private final double[][] distanceMatrix;
        private final Map<Integer, DepotDto> customerDepotMap;
        private final Map<DepotDto, Integer> depotCapacity;

        MDCVRPProblem(RoutingRequest request) {
            this.customers = request.customers();
            this.depots = request.depots();
            this.distanceMatrix = request.distanceMatrix();

            this.customerDepotMap = new HashMap<>();
            for (int i = 0; i < customers.size(); i++) {
                CustomerDto c = customers.get(i);
                DepotDto nearest = depots.stream()
                        .min(Comparator.comparingDouble(d -> distanceMatrix[d.matrixIndex()][c.matrixIndex()]))
                        .orElse(depots.get(0));
                customerDepotMap.put(i, nearest);
            }

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
            for (int i = 0; i < solution.getLength(); i++) {
                int cIdx = solution.variables().get(i);
                DepotDto depot = customerDepotMap.get(cIdx);
                if (depot != null) {
                    depotOrder.get(depot).add(cIdx);
                }
            }
            return depotOrder;
        }

        @Override
        public int length() {
            return customers.size();
        }

        @Override
        public int numberOfVariables() {
            return customers.size();
        }

        @Override
        public int numberOfObjectives() {
            return 1;
        }

        @Override
        public int numberOfConstraints() {
            return 0;
        }

        @Override
        public PermutationSolution<Integer> evaluate(PermutationSolution<Integer> solution) {
            Map<DepotDto, List<Integer>> depotOrder = buildDepotOrder(solution);

            double totalDistance = 0.0;
            for (var entry : depotOrder.entrySet()) {
                DepotDto depot = entry.getKey();
                List<Integer> order = entry.getValue();
                int capacity = depotCapacity.getOrDefault(depot, Integer.MAX_VALUE);

                int currentLoad = 0;
                int currentIndex = depot.matrixIndex();

                for (int cIdx : order) {
                    CustomerDto customer = customers.get(cIdx);

                    if (currentLoad + customer.demand() > capacity && currentLoad > 0) {
                        totalDistance += distanceMatrix[currentIndex][depot.matrixIndex()];
                        currentIndex = depot.matrixIndex();
                        currentLoad = 0;
                    }

                    totalDistance += distanceMatrix[currentIndex][customer.matrixIndex()];
                    currentLoad += customer.demand();
                    currentIndex = customer.matrixIndex();
                }

                totalDistance += distanceMatrix[currentIndex][depot.matrixIndex()];
            }

            solution.objectives()[0] = totalDistance;
            return solution;
        }

        @Override
        public String name() {
            return "MD-CVRP";
        }
    }
}
