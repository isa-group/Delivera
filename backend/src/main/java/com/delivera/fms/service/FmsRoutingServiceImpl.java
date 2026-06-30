package com.delivera.fms.service;

import com.delivera.fms.dto.CustomerDto;
import com.delivera.fms.dto.DepotDto;
import com.delivera.fms.dto.RoutingRequest;
import com.delivera.fms.dto.RoutingResponse;
import com.delivera.fms.dto.VehicleDto;
import com.delivera.model.OperationalUnit;
import com.delivera.model.Order;
import com.delivera.model.OrderStatus;
import com.delivera.model.Vehicle;
import com.delivera.repository.OperationalUnitRepository;
import com.delivera.repository.OrderRepository;
import com.delivera.repository.VehicleRepository;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class FmsRoutingServiceImpl implements FmsRoutingService {

    private final RestClient fmsRoutingClient;
    private final OperationalUnitRepository unitRepository;
    private final OrderRepository orderRepository;
    private final VehicleRepository vehicleRepository;

    public FmsRoutingServiceImpl(RestClient fmsRoutingClient,
                                  OperationalUnitRepository unitRepository,
                                  OrderRepository orderRepository,
                                  VehicleRepository vehicleRepository) {
        this.fmsRoutingClient = fmsRoutingClient;
        this.unitRepository = unitRepository;
        this.orderRepository = orderRepository;
        this.vehicleRepository = vehicleRepository;
    }

    @Override
    public RoutingResponse solveForCompany(UUID companyId) {
        List<OperationalUnit> units = unitRepository.findAllByCompanyId(companyId);
        List<OperationalUnit> depots = units.stream()
                .filter(u -> u.getLatitude() != null && u.getLongitude() != null)
                .toList();

        AtomicInteger index = new AtomicInteger(0); // Es literalmente un contador en este caso (sino habría que usar 2 bucles for para asignar el index a cada depot y customer)
        List<DepotDto> depotDtos = depots.stream()
                .map(u -> new DepotDto(
                        u.getId().toString(),
                        u.getLatitude().doubleValue(),
                        u.getLongitude().doubleValue(),
                        index.getAndIncrement()
                ))
                .toList();

        List<Order> orders = orderRepository.findByCompanyId(companyId);
        List<Order> routableOrders = orders.stream()
                .filter(o -> o.getStatus() == OrderStatus.PENDING || o.getStatus() == OrderStatus.IN_TRANSIT)
                .filter(o -> {
                    if (o.getDestination() != null
                            && o.getDestination().getLatitude() != null
                            && o.getDestination().getLongitude() != null) {
                        return true;
                    }
                    return o.getRecipientLatitude() != null && o.getRecipientLongitude() != null;
                })
                .toList();

        List<CustomerDto> customerDtos = routableOrders.stream()
                .map(o -> {
                    double lat;
                    double lng;
                    if (o.getDestination() != null
                            && o.getDestination().getLatitude() != null
                            && o.getDestination().getLongitude() != null) {
                        lat = o.getDestination().getLatitude().doubleValue();
                        lng = o.getDestination().getLongitude().doubleValue();
                    } else {
                        lat = o.getRecipientLatitude().doubleValue();
                        lng = o.getRecipientLongitude().doubleValue();
                    }
                    return new CustomerDto(
                            o.getId().toString(),
                            1,
                            lat,
                            lng,
                            index.getAndIncrement()
                    );
                })
                .toList();

        List<Vehicle> vehicles = vehicleRepository.findAllByCompanyId(companyId);
        List<VehicleDto> vehicleDtos = vehicles.stream()
                .map(v -> new VehicleDto(
                        v.getId().toString(),
                        v.getCapacity(),
                        v.getDepot().getId().toString()
                ))
                .toList();

        int totalNodes = depotDtos.size() + customerDtos.size();
        double[][] distanceMatrix = buildMockDistanceMatrix(totalNodes);

        RoutingRequest request = new RoutingRequest(
                UUID.randomUUID().toString(),
                depotDtos,
                customerDtos,
                vehicleDtos,
                distanceMatrix
        );

        return fmsRoutingClient.post()
                .uri("/api/v1/fms/routing/solve")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(RoutingResponse.class);
    }

    /**
     * Mock distance matrix for testing purposes.
     * TODO: Reemplazar con un cálculo real de distancias (fórmula de Haversine o API externa de ruteo como OSRM/Google Maps).
     * Genera una distancia random entre 1 y 50 km para cada par de nodos (depots y clientes).
     */
    private double[][] buildMockDistanceMatrix(int size) {
        double[][] matrix = new double[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (i == j) {
                    matrix[i][j] = 0.0;
                } else {
                    matrix[i][j] = Math.round((Math.random() * 49.0 + 1.0) * 100.0) / 100.0;
                }
            }
        }
        return matrix;
    }
}
