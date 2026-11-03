package com.delivera.fms.routing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import com.delivera.fms.routing.config.EngineProperties;
import com.delivera.fms.routing.dto.ParameterType;
import com.delivera.fms.routing.dto.SolverInfo;
import com.delivera.fms.routing.dto.SolverParameter;
import com.delivera.fms.routing.dto.TypeSolver;

/**
 * Los dos solvers cuanticos tal como los declara el application.yml real.
 *
 * Solo se levanta el enlace de propiedades, no la aplicacion: el registro no
 * habla con ningun motor hasta que se pide su estado o se resuelve.
 */
@SpringBootTest(classes = QuantumSolversCatalogTest.Config.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class QuantumSolversCatalogTest {

    @Configuration
    @EnableConfigurationProperties(EngineProperties.class)
    static class Config {
    }

    @Autowired
    private EngineProperties properties;

    private SolverRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SolverRegistry(properties, WebClient.builder());
    }

    @Test
    void bothBackendsAreRegisteredOnTheSameEngine() {
        assertThat(properties.getEngines().get(TypeSolver.QISKIT).getUrl())
                .isEqualTo(properties.getEngines().get(TypeSolver.DWAVE).getUrl())
                .isEqualTo("http://quantum-engine:8095");

        assertThat(registry.findAll(false))
                .extracting(SolverInfo::type)
                .contains(TypeSolver.QISKIT, TypeSolver.DWAVE);
    }

    @Test
    void qiskitDeclaresItsFourParametersWithCqvrpDefaults() {
        SolverInfo qiskit = registry.findByType(TypeSolver.QISKIT, false);

        assertThat(qiskit.deterministic()).isFalse();
        assertThat(qiskit.parameters())
                .extracting(SolverParameter::name, SolverParameter::type, SolverParameter::defaultValue)
                .containsExactly(
                        tuple("maxQubits", ParameterType.INTEGER, 24L),
                        tuple("quantumTimeoutSeconds", ParameterType.DECIMAL, 30.0),
                        tuple("classicalTimeLimit", ParameterType.INTEGER, 60L),
                        tuple("includeMtz", ParameterType.BOOLEAN, false));
    }

    @Test
    void dwaveOnlyDeclaresTheClassicalTimeLimit() {
        // maxQubits, quantumTimeoutSeconds e includeMtz no se usan en la via D-Wave de cqvrp.
        assertThat(registry.findByType(TypeSolver.DWAVE, false).parameters())
                .extracting(SolverParameter::name)
                .containsExactly("classicalTimeLimit");
    }

    @Test
    void missingQiskitParametersAreFilledWithTheirDefaults() {
        Map<String, Object> resolved = registry.resolveParameters(TypeSolver.QISKIT, Map.of("includeMtz", true));

        assertThat(resolved).containsExactly(
                Map.entry("maxQubits", 24L),
                Map.entry("quantumTimeoutSeconds", 30.0),
                Map.entry("classicalTimeLimit", 60L),
                Map.entry("includeMtz", true));
    }

    @Test
    void qiskitRejectsMoreQubitsThanTheSimulatorHas() {
        assertThatThrownBy(() -> registry.resolveParameters(TypeSolver.QISKIT, Map.of("maxQubits", 30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxQubits")
                .hasMessageContaining("maximum is 24");
    }

    @Test
    void qiskitRejectsIncludeMtzSentAsText() {
        assertThatThrownBy(() -> registry.resolveParameters(TypeSolver.QISKIT, Map.of("includeMtz", "true")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("includeMtz");
    }

    @Test
    void dwaveIgnoresQiskitOnlyParameters() {
        Map<String, Object> resolved = registry.resolveParameters(TypeSolver.DWAVE, Map.of("maxQubits", 30));

        assertThat(resolved).containsExactly(Map.entry("classicalTimeLimit", 60L));
    }
}
