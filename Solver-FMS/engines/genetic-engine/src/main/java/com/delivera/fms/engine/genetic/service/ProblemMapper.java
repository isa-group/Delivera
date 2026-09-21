package com.delivera.fms.engine.genetic.service;

import com.delivera.fms.engine.core.model.Customer;
import com.delivera.fms.engine.core.model.Depot;
import com.delivera.fms.engine.core.model.RoutingProblem;
import com.delivera.fms.engine.genetic.dto.CustomerDto;
import com.delivera.fms.engine.genetic.dto.DepotDto;
import com.delivera.fms.engine.genetic.dto.RoutingRequest;
import com.delivera.fms.engine.genetic.dto.VehicleDto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Traduce la peticion del contrato HTTP al modelo del nucleo compartido.
 *
 * Es el unico sitio donde se decide como se leen las restricciones de la flota: la capacidad de
 * un deposito es la del mayor de sus vehiculos y su flota, cuantos vehiculos tiene. Sin vehiculos
 * declarados, ninguna de las dos limita.
 */
final class ProblemMapper {

    private ProblemMapper() {
    }

    static RoutingProblem toProblem(RoutingRequest request) {
        Map<String, List<VehicleDto>> vehiclesByDepot = new HashMap<>();
        if (request.vehicles() != null) {
            for (VehicleDto vehicle : request.vehicles()) {
                vehiclesByDepot.computeIfAbsent(vehicle.startDepotId(), key -> new ArrayList<>()).add(vehicle);
            }
        }

        List<Depot> depots = new ArrayList<>();
        for (int i = 0; i < request.depots().size(); i++) {
            DepotDto dto = request.depots().get(i);
            List<VehicleDto> vehicles = vehiclesByDepot.getOrDefault(dto.id(), List.of());
            int capacity = vehicles.stream().mapToInt(VehicleDto::capacity).max().orElse(Depot.UNLIMITED);
            int fleet = vehicles.isEmpty() ? Depot.UNLIMITED : vehicles.size();
            depots.add(new Depot(i, dto.id(), dto.matrixIndex(), capacity, fleet, dto.durationLimit()));
        }

        List<Customer> customers = new ArrayList<>();
        for (int i = 0; i < request.customers().size(); i++) {
            CustomerDto dto = request.customers().get(i);
            customers.add(new Customer(i, dto.id(), dto.matrixIndex(), dto.demand(), dto.service()));
        }

        return new RoutingProblem(depots, customers, request.distanceMatrix());
    }
}
