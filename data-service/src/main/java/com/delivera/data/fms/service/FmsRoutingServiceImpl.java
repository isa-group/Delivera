package com.delivera.data.fms.service;

import com.delivera.data.depot.dto.RoutableUnit;
import com.delivera.data.depot.repository.OperationalUnitRepository;
import com.delivera.data.fms.dto.ClusterConfig;
import com.delivera.data.fms.dto.Coordinates;
import com.delivera.data.fms.dto.CustomerDto;
import com.delivera.data.fms.dto.DbscanResult;
import com.delivera.data.fms.dto.DeliveryWindow;
import com.delivera.data.fms.dto.DepotDto;
import com.delivera.data.fms.dto.InstanceCatalog;
import com.delivera.data.fms.dto.InstanceDetail;
import com.delivera.data.fms.dto.RoutingRequest;
import com.delivera.data.fms.dto.RoutingResponse;
import com.delivera.data.fms.dto.TypeSolver;
import com.delivera.data.fms.dto.VehicleDto;
import com.delivera.data.fms.dto.VehicleProjection;
import com.delivera.data.order.dto.RoutableOrder;
import com.delivera.data.order.model.OrderStatus;
import com.delivera.data.order.repository.OrderRepository;
import com.delivera.data.vehicle.repository.VehicleRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j 
@Service
@RequiredArgsConstructor
public class FmsRoutingServiceImpl implements FmsRoutingService {

    private final RestClient fmsRoutingClient;
    private final OperationalUnitRepository unitRepository;
    private final OrderRepository orderRepository;
    private final VehicleRepository vehicleRepository;
    private final FmsClusterService clusterService;



   

    public InstanceCatalog getInstanceSummaries() {
        return fmsRoutingClient.get()
                .uri("/api/v1/fms/instances")
                .retrieve()
                .body(InstanceCatalog.class);
    }

    @Override
    public RoutingRequest getInstance(
        String name, 
        TypeSolver type,
        boolean buildMatrix
    ) {
        InstanceDetail detail = fmsRoutingClient.get()
                .uri("/api/v1/fms/instances/"+name)
                .retrieve()
                .body(InstanceDetail.class);

        List<DepotDto> depots = detail.depots();
        List<CustomerDto> customers = detail.customers();
        List<VehicleProjection> vehicles = detail.vehicles();
        
        
        return buildRequest(
            customers, 
            depots, 
            vehicles, 
            type, 
            buildMatrix
        );
    }

    @Override 
    @Transactional(readOnly = true)
    public RoutingRequest getRoutingRequestForCompany(
        UUID companyId, 
        Set<UUID> clients,
        Set<UUID> depots,
        TypeSolver solverType
    ) {
        AtomicInteger index = new AtomicInteger(0); // Es literalmente un contador en este caso (sino habría que usar 2 bucles for para asignar el index a cada depot y customer)
        List<VehicleProjection> vehicleDtos = transformInDTO(
            vehicleRepository.retrieveDTOsByCompanyIdInSelectedDepots(
                companyId, depots
            )
        );
       
      
        Set<String> depotsWithVehicle = vehicleDtos
        .stream().map(vehicle -> vehicle.getStartDepotId()).collect(Collectors.toSet());
        
        
        List<DepotDto> depotDtos =  transformInDTO(
            unitRepository.retrieveDepotsByCompanyId(companyId, depots),
            depotsWithVehicle,
            false,
            index
        );
        

        List<CustomerDto> customerDtos = transformInDTO(
            orderRepository.retrieveSelectedClientsByCompanyIdAndStatus(
                companyId, 
                Set.of(OrderStatus.PENDING, OrderStatus.IN_TRANSIT),
                clients
            ),
            index
        );

        boolean buildMatrix = true;
       
        return  buildRequest(
            customerDtos, 
            depotDtos, 
            vehicleDtos, 
            solverType,
            buildMatrix 
        );
    }

   

    
    @Override
    @Transactional(readOnly = true)
    public RoutingRequest getRoutingRequestForCompany(
        UUID companyId, 
        TypeSolver solverType, 
        Boolean showAllDepots,
        Boolean buildMatrix
    ) {
        return  coreCreateRequest(
            companyId,
            solverType,
            DeliveryWindow.allOrders(),
            showAllDepots,
            buildMatrix
        );
    }

    
    @Transactional(readOnly = true)
    public RoutingRequest coreCreateRequest(
        UUID companyId, 
        TypeSolver solverType,
        DeliveryWindow window, 
        Boolean showAllDepots,
        Boolean buildMatrix
    ) {
        AtomicInteger index = new AtomicInteger(0); // Es literalmente un contador en este caso (sino habría que usar 2 bucles for para asignar el index a cada depot y customer)
        List<VehicleProjection> vehicleDtos = transformInDTO(
            vehicleRepository.findDTOsByCompanyId(companyId)
        );
       
      
        Set<String> depotsWithVehicle = vehicleDtos
        .stream().map(vehicle -> vehicle.getStartDepotId()).collect(Collectors.toSet());
    
        List<DepotDto> depotDtos = transformInDTO( 
            unitRepository.findRotubleUnitsByCompanyId(companyId),
            depotsWithVehicle,
            showAllDepots,
            index
        );

        Instant minInstant = Instant.parse("1900-01-01T00:00:00Z");
        Instant maxInstant = Instant.parse("9999-12-31T23:59:59Z");

        Instant fromDate = window.getFromDate() == null ? minInstant : window.getFromDate();
        Instant toDate = window.getToDate() == null ? maxInstant : window.getToDate();

        List<CustomerDto> customerDtos = transformInDTO(
            orderRepository.searchRoutableOrders(
                companyId,
                window.isIncludeNullsFromDate(),
                window.isIncludeNullsToDate(),
                fromDate,
                toDate,
                Set.of(OrderStatus.PENDING, OrderStatus.IN_TRANSIT)
            ), 
            index
        );
        return  buildRequest(
            customerDtos, 
            depotDtos, 
            vehicleDtos, 
            solverType,
            buildMatrix 
        );
        
    }


  

    @Override
    public DbscanResult clusters(
        ClusterConfig config, 
        RoutingRequest request
    ) {
        DbscanResult result = clusterService.clusterClients(
            config, 
            request.customers(), 
            request.depots(), 
            request.distanceMatrix()
        );
        return result;
    }

    @Override
    public DbscanResult clustersForCompany(
        UUID companyId, 
        ClusterConfig config,  
        TypeSolver solverType, 
        Boolean showAllDepots
    ) {
        boolean notShowAllDepots = false;
        boolean buildMatrix = true;

        RoutingRequest request = coreCreateRequest(
            companyId, 
            solverType, 
            config.getWindow(), 
            notShowAllDepots, 
            buildMatrix
        );
        
        return clusters(config, request);
    }

    @Override
    public DbscanResult clustersForInstance(
        String instanceId, 
        ClusterConfig config,  
        TypeSolver solverType
    ) {
        boolean buildMatrix = true;

        RoutingRequest request = getInstance(
            instanceId,
            solverType, 
            buildMatrix
        );
        
        return clusters(config, request);
    }





    

    // coordinates = [ ...depots, ...customers]
    private double[][] buildHaversineDistanceMatrix(int size,List<Coordinates> coordinates) {
        double[][] matrix = new double[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (i == j) {
                    matrix[i][j] = 0.0;
                } else {
                    Coordinates coordinatesI = coordinates.get(i);
                    Coordinates coordinatesJ = coordinates.get(j);
                    
                    matrix[i][j] = Haversine.distanceKm(
                        coordinatesI.getLat(),coordinatesI.getLng(),
                        coordinatesJ.getLat(),coordinatesJ.getLng()
                    );
                }
            }
        }
        return matrix;
    }


    @Override
    public RoutingResponse solverForCompany(
        UUID companyId, 
        Set<UUID> customers, 
        Set<UUID> depots,
        TypeSolver solverType
    ) {

        RoutingRequest request = getRoutingRequestForCompany(
            companyId,
            customers, 
            depots, 
            solverType
        );
        return fmsRoutingClient.post()
                .uri("/api/v1/fms/routing/solve")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(RoutingResponse.class);
    }



    private  List<DepotDto> transformInDTO(
        List<RoutableUnit> routableUnits,
        Set<String> validDepots,
        Boolean showAll,
        AtomicInteger index
    ) {
        return  routableUnits.stream()
        .filter(depot -> showAll || validDepots.contains(depot.getId().toString()))
        .map(routableUnit -> new DepotDto(
                routableUnit.getId().toString(),
                routableUnit.getLat().doubleValue(),
                routableUnit.getLgn().doubleValue(),
                index.getAndIncrement()
        ))
        .toList();
    }

    private  List<VehicleProjection> transformInDTO(
        List<VehicleDto> vehilces
    ) {
        return vehilces.stream().map(vehicle -> new VehicleProjection(
            vehicle.getId().toString(), 
            vehicle.getCapacity(), 
            vehicle.getStartDepotId().toString())
        ).toList();
    }
    

    private  List<CustomerDto> transformInDTO(
        List<RoutableOrder> routableOrders,
        AtomicInteger index
    ) {
        return  routableOrders.stream().map(routableOrder -> {
            return new CustomerDto(
                routableOrder.getId().toString(),
                1,
                routableOrder.getLat().doubleValue(),
                routableOrder.getLng().doubleValue(),
                index.getAndIncrement()
            );
        }).toList();
    }


    
    private  RoutingRequest buildRequest(
        List<CustomerDto> customerDtos,
        List<DepotDto> depotDtos,
        List<VehicleProjection> vehicleDtos,
        TypeSolver solverType,
        boolean buildMatrix
    ) {
       
        List<Coordinates> coordinates = new ArrayList<>(depotDtos);
        coordinates.addAll(customerDtos);
            
        double[][] distanceMatrix = new double[0][0];

        if (buildMatrix) {
            int totalNodes = depotDtos.size() + customerDtos.size();
            distanceMatrix = buildHaversineDistanceMatrix(totalNodes,coordinates);
        }
        
        
        RoutingRequest request = new RoutingRequest(
                UUID.randomUUID().toString(),
                depotDtos,
                customerDtos,
                vehicleDtos,
                distanceMatrix,
                solverType
        );

        return request;
    }


 

   

    
}
