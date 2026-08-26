package com.delivera.fms.routing.instance.model;

import java.util.List;

public record StandardInstance(
        int type,
        String problemType,
        int vehiclesPerDepot,
        int customerCount,
        int depotCount,
        List<DepotConfig> depotConfigs,
        List<NodeEntry> customers,
        List<NodeEntry> depots
) {
}
