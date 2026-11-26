package com.delivera.data.fms.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.delivera.data.fms.dto.solver.info.TypeSolver;
import com.fasterxml.jackson.databind.ObjectMapper;

class TypeSolverTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void readsTheSolverNameTheHgsEngineAnswersWith() throws Exception {
        assertThat(mapper.readValue("\"HGS-ADC\"", TypeSolver.class)).isEqualTo(TypeSolver.HGS);
    }

    @Test
    void sendsHgsWithTheNameTheGatewayExpects() throws Exception {
        assertThat(mapper.writeValueAsString(TypeSolver.HGS)).isEqualTo("\"HGS\"");
        assertThat(mapper.readValue("\"HGS\"", TypeSolver.class)).isEqualTo(TypeSolver.HGS);
    }
}
