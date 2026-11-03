package com.delivera.fms.routing;

import static org.assertj.core.api.Assertions.assertThat;

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
    void theApplicationStartsWithEveryConfiguredSolver() {
        assertThat(healthCache).isNotNull();
        assertThat(registry.findAll(false))
                .extracting(info -> info.type())
                .contains(TypeSolver.values());
    }
}
