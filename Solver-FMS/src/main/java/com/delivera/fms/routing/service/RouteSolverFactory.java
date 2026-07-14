package com.delivera.fms.routing.service;

import com.delivera.fms.routing.dto.TypeSolver;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RouteSolverFactory {

    private final Map<String, RouteSolver> solvers;

    public RouteSolverFactory(List<RouteSolver> solvers) {
        this.solvers = solvers.stream()
                .collect(Collectors.toMap(RouteSolver::getSolverId, Function.identity()));
    }

    public RouteSolver getSolver(TypeSolver type) {
        RouteSolver solver = solvers.get(type.name().toLowerCase());
        if (solver == null) {
            throw new IllegalArgumentException("No solver found for type: " + type);
        }
        return solver;
    }
}
