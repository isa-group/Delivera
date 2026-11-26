package com.delivera.fms.routing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.delivera.fms.routing.dto.TypeSolver;
import com.delivera.fms.routing.service.SolverRegistry;

/** Con FMS_ENGINES_RANDOM_ENABLED=true el gateway vuelve a registrar RANDOM. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "fms.instances.dir=../instances-MD-CVRP-JSON",
                "FMS_ENGINES_RANDOM_ENABLED=true"
        })
class GatewayRandomEnabledContextTest {

    @Autowired
    private SolverRegistry registry;

    @Test
    void randomComesBackWhenItIsEnabled() {
        assertThat(registry.findAll(false))
                .extracting(info -> info.type())
                .contains(TypeSolver.values());
    }
}
