package com.delivera.fms.routing.instance.parser;

import com.delivera.fms.routing.instance.model.DepotConfig;
import com.delivera.fms.routing.instance.model.NodeEntry;
import com.delivera.fms.routing.instance.model.StandardInstance;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

@Component
public class StandardInstanceParser {

    public StandardInstance parse(Path file) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            StringTokenizer header = new StringTokenizer(reader.readLine());
            int type = Integer.parseInt(header.nextToken());
            int vehiclesPerDepot = Integer.parseInt(header.nextToken());
            int customerCount = Integer.parseInt(header.nextToken());
            int depotCount = Integer.parseInt(header.nextToken());

            List<DepotConfig> depotConfigs = new ArrayList<>();
            for (int i = 0; i < depotCount; i++) {
                StringTokenizer configLine = new StringTokenizer(reader.readLine());
                double maxDuration = Double.parseDouble(configLine.nextToken());
                int vehicleCapacity = Integer.parseInt(configLine.nextToken());
                depotConfigs.add(new DepotConfig(maxDuration, vehicleCapacity));
            }

            List<NodeEntry> customers = new ArrayList<>();
            for (int i = 0; i < customerCount; i++) {
                customers.add(parseNodeEntry(reader.readLine()));
            }

            List<NodeEntry> depots = new ArrayList<>();
            for (int i = 0; i < depotCount; i++) {
                depots.add(parseNodeEntry(reader.readLine()));
            }

            return new StandardInstance(type, vehiclesPerDepot, customerCount, depotCount,
                    depotConfigs, customers, depots);
        }
    }

    private NodeEntry parseNodeEntry(String line) {
        StringTokenizer st = new StringTokenizer(line);
        int id = Integer.parseInt(st.nextToken());
        double x = Double.parseDouble(st.nextToken());
        double y = Double.parseDouble(st.nextToken());
        double serviceDuration = Double.parseDouble(st.nextToken());
        int demand = Integer.parseInt(st.nextToken());

        int frequency = 0;
        int visitCombinationsCount = 0;
        int[] visitCombinations = new int[0];
        double timeWindowStart = 0;
        double timeWindowEnd = 0;

        if (st.hasMoreTokens()) {
            frequency = Integer.parseInt(st.nextToken());
        }
        if (st.hasMoreTokens()) {
            visitCombinationsCount = Integer.parseInt(st.nextToken());
        }
        if (visitCombinationsCount > 0 && st.hasMoreTokens()) {
            visitCombinations = new int[visitCombinationsCount];
            for (int i = 0; i < visitCombinationsCount && st.hasMoreTokens(); i++) {
                visitCombinations[i] = Integer.parseInt(st.nextToken());
            }
        }
        if (st.hasMoreTokens()) {
            timeWindowStart = Double.parseDouble(st.nextToken());
        }
        if (st.hasMoreTokens()) {
            timeWindowEnd = Double.parseDouble(st.nextToken());
        }

        return new NodeEntry(id, x, y, serviceDuration, demand, frequency,
                visitCombinationsCount, visitCombinations, timeWindowStart, timeWindowEnd);
    }
}
