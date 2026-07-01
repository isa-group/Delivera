package com.delivera.fms.routing.instance.mapper;

import com.delivera.fms.routing.dto.CustomerDto;
import com.delivera.fms.routing.dto.DepotDto;
import com.delivera.fms.routing.dto.VehicleDto;
import com.delivera.fms.routing.instance.model.DepotConfig;
import com.delivera.fms.routing.instance.model.NodeEntry;
import com.delivera.fms.routing.instance.model.StandardInstance;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class StandardInstanceMapper {

    public MappingResult map(StandardInstance instance) {
        List<DepotDto> depots = new ArrayList<>();
        List<CustomerDto> customers = new ArrayList<>();
        List<VehicleDto> vehicles = new ArrayList<>();

        for (int i = 0; i < instance.depots().size(); i++) {
            NodeEntry depotNode = instance.depots().get(i);
            DepotConfig config = instance.depotConfigs().get(i);
            int matrixIndex = i;
            String depotId = String.valueOf(i + 1);

            DepotDto depot = new DepotDto(
                    depotId,
                    depotNode.y(),
                    depotNode.x(),
                    matrixIndex
            );
            depots.add(depot);

            for (int v = 0; v < instance.vehiclesPerDepot(); v++) {
                VehicleDto vehicle = new VehicleDto(
                        "V" + depotId + "-" + (v + 1),
                        config.vehicleCapacity(),
                        depotId
                );
                vehicles.add(vehicle);
            }
        }

        int customerMatrixIndexStart = instance.depots().size();
        for (int i = 0; i < instance.customers().size(); i++) {
            NodeEntry customerNode = instance.customers().get(i);
            int matrixIndex = customerMatrixIndexStart + i;

            CustomerDto customer = new CustomerDto(
                    String.valueOf(customerNode.id()),
                    customerNode.demand(),
                    customerNode.y(),
                    customerNode.x(),
                    matrixIndex
            );
            customers.add(customer);
        }

        return new MappingResult(depots, customers, vehicles);
    }

    public record MappingResult(
            List<DepotDto> depots,
            List<CustomerDto> customers,
            List<VehicleDto> vehicles
    ) {
    }
}
