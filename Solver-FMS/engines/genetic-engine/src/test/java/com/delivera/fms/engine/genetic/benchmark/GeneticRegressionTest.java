package com.delivera.fms.engine.genetic.benchmark;

import com.delivera.fms.engine.genetic.dto.RoutingRequest;
import com.delivera.fms.engine.genetic.dto.RoutingResponse;
import com.delivera.fms.engine.genetic.service.GeneticRouteSolver;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fija el resultado del motor con semilla sobre tres instancias Cordeau.
 *
 * No juzga la calidad: juzga que la busqueda no ha cambiado. Es lo que permite refactorizar el
 * nucleo compartido sabiendo si se ha alterado el algoritmo. Si un cambio es deliberado, se
 * actualizan los costes esperados en el mismo commit que lo introduce.
 *
 * Las tres instancias cubren los casos que han dado problemas: p01 es la pequena de referencia,
 * p07 es infactible con la asignacion al deposito mas cercano (obliga a reparar la flota) y p22
 * es grande y con limite de duracion.
 */
class GeneticRegressionTest {

    private static final long SEED = 20260914L;
    private static final double COST_TOLERANCE = 1e-6;

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "p01, 609.87298911005",
            "p07, 941.5649179862726",
            "p22, 6030.194630902542",
    })
    void solverResultIsPinned(String instance, double expectedCost) throws IOException {
        RoutingRequest request = CordeauInstanceLoader.load(instance, Map.of("seed", SEED));

        RoutingResponse response = new GeneticRouteSolver().solve(request);

        List<String> violations = FeasibilityChecker.violations(request, response);
        assertTrue(violations.isEmpty(), () -> instance + " infactible: " + violations);

        double cost = FeasibilityChecker.cost(request, response);
        assertEquals(cost, response.totalCost(), COST_TOLERANCE,
                "el coste informado no coincide con el recalculado desde las paradas");
        assertEquals(expectedCost, cost, COST_TOLERANCE,
                () -> instance + " ha cambiado de coste con la semilla " + SEED);
    }
}
