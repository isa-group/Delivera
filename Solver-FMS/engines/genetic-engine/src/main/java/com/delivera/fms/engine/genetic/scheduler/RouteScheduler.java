package com.delivera.fms.engine.genetic.scheduler;

import com.delivera.fms.engine.core.model.Depot;
import com.delivera.fms.engine.core.model.Route;
import com.delivera.fms.engine.core.model.RoutingProblem;
import com.delivera.fms.engine.core.split.RouteSplitter;
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

    private final RoutingProblem problem;
    private final RouteSplitter splitter;
    private final Map<String, List<VehicleDto>> vehiclesByDepot;

    public RouteScheduler(RoutingProblem problem, RouteSplitter splitter, List<VehicleDto> vehicles) {
        this.problem = problem;
        this.splitter = splitter;
        this.vehiclesByDepot = new HashMap<>();
        if (vehicles != null) {
            for (VehicleDto vehicle : vehicles) {
                vehiclesByDepot.computeIfAbsent(vehicle.startDepotId(), key -> new ArrayList<>()).add(vehicle);
            }
        }
    }

    public List<RouteDto> buildRoutes(Depot depot, List<Integer> customerOrder) {
        List<VehicleDto> depotVehicles = vehiclesByDepot.getOrDefault(depot.id(), List.of());

        List<RouteDto> routes = new ArrayList<>();
        for (Route route : splitter.routes(depot, customerOrder)) {
            if (route.customers().isEmpty()) {
                continue;
            }

            String vehicleId = vehicleFor(depotVehicles, depot, routes.size());
            List<String> stops = new ArrayList<>(route.customers().size());
            for (int customer : route.customers()) {
                stops.add(problem.customer(customer).id());
            }

            routes.add(new RouteDto(vehicleId, depot.id(), stops, route.distance(), route.load()));
        }

        return routes;
    }

    private String vehicleFor(List<VehicleDto> depotVehicles, Depot depot, int index) {
        return depotVehicles.isEmpty()
                ? "V-GA-" + depot.id() + "-" + (index + 1)
                : depotVehicles.get(index % depotVehicles.size()).id();
    }
}
