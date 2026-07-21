package com.delivera.fms.routing.instance.calculator;

import com.delivera.fms.routing.dto.CustomerDto;
import com.delivera.fms.routing.dto.DepotDto;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DistanceMatrixCalculator {

    public double[][] calculate(List<DepotDto> depots, List<CustomerDto> customers) {
        int totalNodes = depots.size() + customers.size();
        double[][] matrix = new double[totalNodes][totalNodes];

        double[] xCoords = new double[totalNodes];
        double[] yCoords = new double[totalNodes];

        for (DepotDto depot : depots) {
            xCoords[depot.matrixIndex()] = depot.lng();
            yCoords[depot.matrixIndex()] = depot.lat();
        }

        for (CustomerDto customer : customers) {
            xCoords[customer.matrixIndex()] = customer.lng();
            yCoords[customer.matrixIndex()] = customer.lat();
        }

        for (int i = 0; i < totalNodes; i++) {
            for (int j = i + 1; j < totalNodes; j++) {
                double distance = euclideanDistance(xCoords[i], yCoords[i], xCoords[j], yCoords[j]);
                matrix[i][j] = distance;
                matrix[j][i] = distance;
            }
        }

        return matrix;
    }

    private double euclideanDistance(double x1, double y1, double x2, double y2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
