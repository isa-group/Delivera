package com.delivera.fms.engine.annealing.benchmark;

import com.delivera.fms.engine.annealing.dto.RoutingRequest;
import com.delivera.fms.engine.annealing.dto.RoutingResponse;
import com.delivera.fms.engine.annealing.service.AnnealingRouteSolver;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fija el resultado del motor con semilla sobre tres instancias Cordeau.
 *
 * No juzga la calidad: juzga que la busqueda no ha cambiado. Con parada por tiempo dos ejecuciones
 * hacen distinto numero de movimientos segun la carga de la maquina, asi que aqui se fija
 * {@code maxLevels} y se da un presupuesto de tiempo que nunca llega a actuar. Si un cambio en el
 * algoritmo es deliberado, se actualizan los costes esperados en el mismo commit.
 *
 * Las tres instancias cubren los casos que han dado problemas: p01 es la pequena de referencia,
 * p07 es infactible con la asignacion al deposito mas cercano (obliga a reparar la flota) y p22
 * es grande, con limite de duracion, y su solucion inicial necesita menos rutas de las que tiene.
 */
class AnnealingRegressionTest {

    private static final long SEED = 20260914L;
    private static final int LEVELS = 60;
    private static final long GENEROUS_TIME_LIMIT_MS = 600_000L;
    private static final double COST_TOLERANCE = 1e-6;

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "p01, 590.4483641423434",
            "p07, 916.0560787609537",
            "p22, 5887.841928476814",
    })
    void solverResultIsPinned(String instance, double expectedCost) throws IOException {
        RoutingRequest request = CordeauInstanceLoader.load(instance, Map.of(
                "seed", SEED, "maxLevels", LEVELS, "timeLimitMs", GENEROUS_TIME_LIMIT_MS));

        RoutingResponse response = new AnnealingRouteSolver().solve(request);

        List<String> violations = FeasibilityChecker.violations(request, response);
        assertTrue(violations.isEmpty(), () -> instance + " infactible: " + violations);
        assertFalse(response.trace().isEmpty(), "la curva anytime debe tener al menos un punto");

        double cost = FeasibilityChecker.cost(request, response);
        assertEquals(cost, response.totalCost(), COST_TOLERANCE,
                "el coste informado no coincide con el recalculado desde las paradas");
        assertEquals(expectedCost, cost, COST_TOLERANCE,
                () -> instance + " ha cambiado de coste con la semilla " + SEED + " y " + LEVELS + " niveles");
    }
}
