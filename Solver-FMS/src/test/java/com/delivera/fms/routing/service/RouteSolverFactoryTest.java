package com.delivera.fms.routing.service;

import com.delivera.fms.routing.dto.TypeSolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RouteSolverFactoryTest {

    private RouteSolverFactory factory;

    @BeforeEach
    void setUp() {
        factory = new RouteSolverFactory(List.of(
                new GreedyRouteSolverImpl(),
                new RandomRouteSolverImpl()
        ));
    }

    @Test
    void shouldReturnGreedySolver() {
        RouteSolver solver = factory.getSolver(TypeSolver.GREEDY);
        assertNotNull(solver);
        assertEquals("greedy", solver.getSolverId());
    }

    @Test
    void shouldReturnRandomSolver() {
        RouteSolver solver = factory.getSolver(TypeSolver.RANDOM);
        assertNotNull(solver);
        assertEquals("random", solver.getSolverId());
    }

    @Test
    void shouldThrowForUnknownSolver() {
        RouteSolverFactory emptyFactory = new RouteSolverFactory(List.of());
        assertThrows(IllegalArgumentException.class, () -> emptyFactory.getSolver(TypeSolver.GREEDY));
    }
}
