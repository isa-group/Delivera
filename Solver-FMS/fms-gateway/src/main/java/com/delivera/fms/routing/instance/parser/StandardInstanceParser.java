package com.delivera.fms.routing.instance.parser;

import com.delivera.fms.routing.instance.model.DepotConfig;
import com.delivera.fms.routing.instance.model.NodeEntry;
import com.delivera.fms.routing.instance.model.StandardInstance;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class StandardInstanceParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public StandardInstance parse(Path file) throws IOException {
        String content = Files.readString(file);
        JsonNode root = objectMapper.readTree(content);

        int type = root.get("problem_type_code").asInt();
        int vehiclesPerDepot = root.get("vehicles_per_depot").asInt();
        int customerCount = root.get("num_customers").asInt();
        int depotCount = root.get("num_depots").asInt();

        List<DepotConfig> depotConfigs = new ArrayList<>();
        JsonNode depotsNode = root.get("depots");
        for (int i = 0; i < depotCount; i++) {
            JsonNode depotNode = depotsNode.get(i);
            double maxDuration = depotNode.get("max_duration").asDouble();
            int vehicleCapacity = depotNode.get("vehicle_capacity").asInt();
            depotConfigs.add(new DepotConfig(maxDuration, vehicleCapacity));
        }

        List<NodeEntry> customers = new ArrayList<>();
        JsonNode customersNode = root.get("customers");
        for (int i = 0; i < customerCount; i++) {
            JsonNode customerNode = customersNode.get(i);
            customers.add(parseNodeEntry(customerNode));
        }

        List<NodeEntry> depots = new ArrayList<>();
        for (int i = 0; i < depotCount; i++) {
            JsonNode depotNode = depotsNode.get(i);
            depots.add(parseNodeEntry(depotNode));
        }

        return new StandardInstance(type, vehiclesPerDepot, customerCount, depotCount,
                depotConfigs, customers, depots);
    }

    private NodeEntry parseNodeEntry(JsonNode node) {
        int id = node.get("id").asInt();
        double x = node.get("x").asDouble();
        double y = node.get("y").asDouble();
        double serviceDuration = node.get("service_duration").asDouble();
        int demand = node.get("demand").asInt();

        int frequency = node.has("visit_frequency") ? node.get("visit_frequency").asInt() : 0;
        int visitCombinationsCount = node.has("num_combinations") ? node.get("num_combinations").asInt() : 0;
        
        int[] visitCombinations = new int[0];
        if (visitCombinationsCount > 0 && node.has("visit_combinations")) {
            JsonNode combinationsNode = node.get("visit_combinations");
            visitCombinations = new int[visitCombinationsCount];
            for (int i = 0; i < visitCombinationsCount; i++) {
                visitCombinations[i] = combinationsNode.get(i).asInt();
            }
        }

        double timeWindowStart = node.has("time_window_earliest") ? node.get("time_window_earliest").asDouble() : 0;
        double timeWindowEnd = node.has("time_window_latest") ? node.get("time_window_latest").asDouble() : 0;

        return new NodeEntry(id, x, y, serviceDuration, demand, frequency,
                visitCombinationsCount, visitCombinations, timeWindowStart, timeWindowEnd);
    }
}
