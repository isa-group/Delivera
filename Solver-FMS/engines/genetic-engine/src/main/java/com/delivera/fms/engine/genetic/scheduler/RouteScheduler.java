package com.delivera.fms.engine.genetic.scheduler;

import com.delivera.fms.engine.genetic.dto.CustomerDto;
import com.delivera.fms.engine.genetic.dto.DepotDto;
import com.delivera.fms.engine.genetic.dto.RouteDto;
import com.delivera.fms.engine.genetic.dto.VehicleDto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Convierte la secuencia de clientes de un deposito en las rutas que se devuelven al cliente.
 *
 * Delega el troceado en {@link RouteSplitter}, el mismo que usa la funcion objetivo, para que el
 * coste reportado coincida con el que ha guiado la busqueda.
 */
public class RouteScheduler {

    private final List<CustomerDto> customers;
    private final double[][] distanceMatrix;
    private final RouteSplitter splitter;
    private final Map<String, List<VehicleDto>> vehiclesByDepot;

    public RouteScheduler(List<CustomerDto> customers,
                          double[][] distanceMatrix,
                          RouteSplitter splitter,
                          List<VehicleDto> vehicles) {
        this.customers = customers;
        this.distanceMatrix = distanceMatrix;
        this.splitter = splitter;
        this.vehiclesByDepot = new HashMap<>();
        if (vehicles != null) {
            for (VehicleDto vehicle : vehicles) {
                vehiclesByDepot.computeIfAbsent(vehicle.startDepotId(), key -> new ArrayList<>()).add(vehicle);
            }
        }
    }

    public List<RouteDto> buildRoutes(DepotDto depot, List<Integer> customerOrder) {
        List<VehicleDto> depotVehicles = vehiclesByDepot.getOrDefault(depot.id(), List.of());

        List<RouteDto> routes = new ArrayList<>();
        List<List<Integer>> split = splitter.split(depot, customerOrder);

        for (List<Integer> route : split) {
            if (route.isEmpty()) {
                continue;
            }

            String vehicleId = vehicleFor(depotVehicles, depot, routes.size());

            List<String> stops = new ArrayList<>(route.size());
            double totalDistance = 0.0;
            int totalLoad = 0;
            int currentIndex = depot.matrixIndex();

            for (int customerIdx : route) {
                CustomerDto customer = customers.get(customerIdx);
                totalDistance += distanceMatrix[currentIndex][customer.matrixIndex()];
                stops.add(customer.id());
                totalLoad += customer.demand();
                currentIndex = customer.matrixIndex();
            }
            totalDistance += distanceMatrix[currentIndex][depot.matrixIndex()];

            routes.add(new RouteDto(vehicleId, depot.id(), stops, totalDistance, totalLoad));
        }

        return routes;
    }

    private String vehicleFor(List<VehicleDto> depotVehicles, DepotDto depot, int index) {
        return depotVehicles.isEmpty()
                ? "V-GA-" + depot.id() + "-" + (index + 1)
                : depotVehicles.get(index % depotVehicles.size()).id();
    }
}
