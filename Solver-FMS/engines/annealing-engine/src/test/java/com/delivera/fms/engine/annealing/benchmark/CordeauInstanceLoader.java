package com.delivera.fms.engine.annealing.benchmark;

import com.delivera.fms.engine.annealing.dto.CustomerDto;
import com.delivera.fms.engine.annealing.dto.DepotDto;
import com.delivera.fms.engine.annealing.dto.RoutingRequest;
import com.delivera.fms.engine.annealing.dto.VehicleDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Carga una instancia Cordeau y la convierte en la peticion que recibiria el motor.
 *
 * Replica el mapeo de la pasarela (lat = y, lng = x, matriz euclidea, {@code vehicles_per_depot}
 * vehiculos por deposito) para que los resultados obtenidos aqui sean los mismos que a traves de
 * ella. Si ese mapeo cambia en la pasarela, hay que cambiarlo tambien aqui.
 */
public final class CordeauInstanceLoader {

    static final Path INSTANCES_DIR = Path.of("..", "..", "instances-MD-CVRP-JSON");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CordeauInstanceLoader() {
    }

    public static RoutingRequest load(String name, Map<String, Object> parameters) throws IOException {
        JsonNode root = MAPPER.readTree(Files.readString(INSTANCES_DIR.resolve(name + ".json")));
        int fleet = root.get("vehicles_per_depot").asInt();

        List<DepotDto> depots = new ArrayList<>();
        List<VehicleDto> vehicles = new ArrayList<>();
        JsonNode depotNodes = root.get("depots");
        for (int i = 0; i < depotNodes.size(); i++) {
            JsonNode node = depotNodes.get(i);
            String depotId = String.valueOf(i + 1);
            depots.add(new DepotDto(depotId, node.get("y").asDouble(), node.get("x").asDouble(), i,
                    node.get("max_duration").asDouble()));
            for (int v = 0; v < fleet; v++) {
                vehicles.add(new VehicleDto("V" + depotId + "-" + (v + 1),
                        node.get("vehicle_capacity").asInt(), depotId));
            }
        }

        List<CustomerDto> customers = new ArrayList<>();
        JsonNode customerNodes = root.get("customers");
        for (int i = 0; i < customerNodes.size(); i++) {
            JsonNode node = customerNodes.get(i);
            customers.add(new CustomerDto(node.get("id").asText(), node.get("demand").asInt(),
                    node.get("y").asDouble(), node.get("x").asDouble(), depots.size() + i,
                    node.get("service_duration").asDouble()));
        }

        return new RoutingRequest(name, depots, customers, vehicles,
                distanceMatrix(depots, customers), parameters);
    }

    private static double[][] distanceMatrix(List<DepotDto> depots, List<CustomerDto> customers) {
        int size = depots.size() + customers.size();
        double[] x = new double[size];
        double[] y = new double[size];
        for (DepotDto depot : depots) {
            x[depot.matrixIndex()] = depot.lng();
            y[depot.matrixIndex()] = depot.lat();
        }
        for (CustomerDto customer : customers) {
            x[customer.matrixIndex()] = customer.lng();
            y[customer.matrixIndex()] = customer.lat();
        }

        double[][] matrix = new double[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = i + 1; j < size; j++) {
                double distance = Math.hypot(x[i] - x[j], y[i] - y[j]);
                matrix[i][j] = distance;
                matrix[j][i] = distance;
            }
        }
        return matrix;
    }
}
