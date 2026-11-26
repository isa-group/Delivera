package com.delivera.fms.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.delivera.fms.routing.dto.TypeSolver;
import com.delivera.fms.routing.service.HealthStatusCache;
import com.delivera.fms.routing.service.SolverRegistry;

/**
 * Arranca la aplicacion entera. Los tests unitarios crean las clases a mano y no
 * detectan un bean que Spring no sabe construir; este si.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "fms.instances.dir=../instances-MD-CVRP-JSON")
class GatewayContextTest {

    @Autowired
    private SolverRegistry registry;

    @Autowired
    private HealthStatusCache healthCache;

    @Test
    void theApplicationStartsWithEveryConfiguredSolverExceptRandom() {
        // RANDOM esta apagado por defecto (fms.engines.random.enabled=false).
        TypeSolver[] enabledByDefault = Arrays.stream(TypeSolver.values())
                .filter(type -> type != TypeSolver.RANDOM)
                .toArray(TypeSolver[]::new);

        assertThat(healthCache).isNotNull();
        assertThat(registry.findAll(false))
                .extracting(info -> info.type())
                .contains(enabledByDefault)
                .doesNotContain(TypeSolver.RANDOM);
    }
}
