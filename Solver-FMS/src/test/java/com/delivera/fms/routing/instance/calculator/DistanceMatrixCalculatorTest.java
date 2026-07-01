package com.delivera.fms.routing.instance.calculator;

import com.delivera.fms.routing.dto.CustomerDto;
import com.delivera.fms.routing.dto.DepotDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DistanceMatrixCalculatorTest {

    @Test
    void shouldCalculateEuclideanDistances() {
        DepotDto depot = new DepotDto("D1", 0.0, 0.0, 0);
        CustomerDto c1 = new CustomerDto("C1", 10, 3.0, 0.0, 1);
        CustomerDto c2 = new CustomerDto("C2", 10, 0.0, 4.0, 2);

        DistanceMatrixCalculator calculator = new DistanceMatrixCalculator();
        double[][] matrix = calculator.calculate(List.of(depot), List.of(c1, c2));

        assertEquals(3, matrix.length);
        assertEquals(3, matrix[0].length);

        assertEquals(0.0, matrix[0][0], 0.001);
        assertEquals(3.0, matrix[0][1], 0.001);
        assertEquals(4.0, matrix[0][2], 0.001);
        assertEquals(5.0, matrix[1][2], 0.001);

        assertEquals(matrix[0][1], matrix[1][0], 0.001);
        assertEquals(matrix[0][2], matrix[2][0], 0.001);
        assertEquals(matrix[1][2], matrix[2][1], 0.001);
    }

    @Test
    void shouldHandleSingleNode() {
        DepotDto depot = new DepotDto("D1", 0.0, 0.0, 0);

        DistanceMatrixCalculator calculator = new DistanceMatrixCalculator();
        double[][] matrix = calculator.calculate(List.of(depot), List.of());

        assertEquals(1, matrix.length);
        assertEquals(0.0, matrix[0][0], 0.001);
    }
}
