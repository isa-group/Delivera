package com.delivera.data.fms.service;

import com.delivera.data.depot.dto.RoutableUnit;
import com.delivera.data.depot.repository.OperationalUnitRepository;
import com.delivera.data.fms.dto.*;
import com.delivera.data.order.dto.RoutableOrder;
import com.delivera.data.order.repository.OrderRepository;
import com.delivera.data.vehicle.repository.VehicleRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FmsRoutingServiceImplTest {

    @Mock
    private RestClient fmsRoutingClient;

    @Mock
    private OperationalUnitRepository unitRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private FmsClusterService clusterService;

    @InjectMocks
    private FmsRoutingServiceImpl service;

    private UUID companyId;
    private UUID depot1Id;
    private UUID depot2Id;
    private UUID customer1Id;
    private UUID customer2Id;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        depot1Id = UUID.randomUUID();
        depot2Id = UUID.randomUUID();
        customer1Id = UUID.randomUUID();
        customer2Id = UUID.randomUUID();
    }

    private void prepareScenario() {

        when(vehicleRepository.findDTOsByCompanyId(companyId))
                .thenReturn(List.of(
                        new VehicleDto(
                                UUID.randomUUID(),
                                100,
                                depot1Id
                        )
                ));

        when(unitRepository.findRotubleUnitsByCompanyId(companyId))
                .thenReturn(List.of(
                        new RoutableUnit(
                                depot1Id,
                                BigDecimal.valueOf(40.0),
                                BigDecimal.valueOf(-3.0)
                        ),
                        new RoutableUnit(
                                depot2Id,
                                BigDecimal.valueOf(41.0),
                                BigDecimal.valueOf(-4.0)
                        )
                ));

        when(orderRepository.searchRoutableOrders(
                eq(companyId),
                anyBoolean(),
                anyBoolean(),
                any(),
                any(),
                anySet()))
                .thenReturn(List.of(
                        new RoutableOrder(
                                customer1Id,
                                BigDecimal.valueOf(39.0),
                                BigDecimal.valueOf(-2.0)
                        ),
                        new RoutableOrder(
                                customer2Id,
                                BigDecimal.valueOf(38.0),
                                BigDecimal.valueOf(-1.0)
                        )
                ));
    }

    @Test
    void coreCreateRequest_shouldBuildRoutingRequest() {

        prepareScenario();

        RoutingRequest request =
                service.coreCreateRequest(
                        companyId,
                        TypeSolver.GREEDY,
                        new DeliveryWindow(),
                        false,
                        false
                );

        assertThat(request).isNotNull();

        assertThat(request.solverType())
                .isEqualTo(TypeSolver.GREEDY);

        assertThat(request.problemId())
                .isNotBlank();

        assertThat(request.depots())
                .hasSize(1);

        assertThat(request.customers())
                .hasSize(2);

        assertThat(request.vehicles())
                .hasSize(1);
    }

    @Test
    void coreCreateRequest_shouldOnlyIncludeDepotsWithVehicles() {

        prepareScenario();

        RoutingRequest request =
                service.coreCreateRequest(
                        companyId,
                        TypeSolver.GREEDY,
                        new DeliveryWindow(),
                        false,
                        false
                );

        assertThat(request.depots()).hasSize(1);

        assertThat(request.depots().get(0).id())
                .isEqualTo(depot1Id.toString());
    }

    @Test
    void coreCreateRequest_shouldIncludeAllDepots() {

        prepareScenario();

        RoutingRequest request =
                service.coreCreateRequest(
                        companyId,
                        TypeSolver.GREEDY,
                        new DeliveryWindow(),
                        true,
                        false
                );

        assertThat(request.depots()).hasSize(2);
    }

    @Test
    void coreCreateRequest_shouldGenerateSequentialIndexes() {

        prepareScenario();

        RoutingRequest request =
                service.coreCreateRequest(
                        companyId,
                        TypeSolver.GREEDY,
                        new DeliveryWindow(),
                        true,
                        false
                );

        assertThat(request.depots().get(0).matrixIndex())
                .isEqualTo(0);

        assertThat(request.depots().get(1).matrixIndex())
                .isEqualTo(1);

        assertThat(request.customers().get(0).matrixIndex())
                .isEqualTo(2);

        assertThat(request.customers().get(1).matrixIndex())
                .isEqualTo(3);
    }

    @Test
    void coreCreateRequest_shouldGenerateCorrectMatrixSize() {

        prepareScenario();

        RoutingRequest request =
                service.coreCreateRequest(
                        companyId,
                        TypeSolver.GREEDY,
                        new DeliveryWindow(),
                        true,
                        true
                );

        int totalNodes =
                request.depots().size()
                + request.customers().size();

        assertThat(request.distanceMatrix().length).isEqualTo(totalNodes);

        for (double[] row : request.distanceMatrix()) {
            assertThat(row).hasSize(totalNodes);
        }
    }

    @Test
    void coreCreateRequest_shouldGenerateZeroDistanceOnDiagonal() {

        prepareScenario();

        RoutingRequest request =
                service.coreCreateRequest(
                        companyId,
                        TypeSolver.GREEDY,
                        new DeliveryWindow(),
                        true,
                        true
                );

        double[][] matrix = request.distanceMatrix();

        for (int i = 0; i < matrix.length; i++) {
            assertThat(matrix[i][i]).isZero();
        }
    }

    @Test
    void coreCreateRequest_shouldGenerateSymmetricDistanceMatrix() {

        prepareScenario();

        RoutingRequest request =
                service.coreCreateRequest(
                        companyId,
                        TypeSolver.GREEDY,
                        new DeliveryWindow(),
                        true,
                        true
                );

        double[][] matrix = request.distanceMatrix();

        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix.length; j++) {

                assertThat(matrix[i][j])
                        .isEqualTo(matrix[j][i]);
            }
        }
    }

    @Test
    void coreCreateRequest_shouldMapVehicleCorrectly() {

        prepareScenario();

        RoutingRequest request =
                service.coreCreateRequest(
                        companyId,
                        TypeSolver.GREEDY,
                        new DeliveryWindow(),
                        false,
                        true
                );

        VehicleProjection vehicle =
                request.vehicles().get(0);

        assertThat(vehicle.getCapacity())
                .isEqualTo(100);

        assertThat(vehicle.getStartDepotId())
                .isEqualTo(depot1Id.toString());
    }

    @Test
    void coreCreateRequest_shouldMapCustomerDemandToOne() {

        prepareScenario();

        RoutingRequest request =
                service.coreCreateRequest(
                        companyId,
                        TypeSolver.GREEDY,
                        new DeliveryWindow(),
                        false,
                        true
                );

        assertThat(request.customers())
                .allMatch(customer -> customer.demand().equals(1));
    }

    @Test
    void getRoutingRequestForCompany_shouldBuildRequest() {

        when(vehicleRepository.findDTOsByCompanyId(companyId))
                .thenReturn(List.of(
                        new VehicleDto(
                                UUID.randomUUID(),
                                100,
                                depot1Id
                        )
                ));

        when(unitRepository.findRotubleUnitsByCompanyId(companyId))
                .thenReturn(List.of(
                        new RoutableUnit(
                                depot1Id,
                                BigDecimal.valueOf(40.0),
                                BigDecimal.valueOf(-3.0)
                        )
                ));

        when(orderRepository.searchRoutableOrders(
                eq(companyId),
                anyBoolean(),
                anyBoolean(),
                any(),
                any(),
                anySet()
        )).thenReturn(List.of(
                new RoutableOrder(
                        customer1Id,
                        BigDecimal.valueOf(39.0),
                        BigDecimal.valueOf(-2.0)
                )
        ));

        RoutingRequest request =
                service.getRoutingRequestForCompany(
                        companyId,
                        TypeSolver.GREEDY,
                        false,
                        false
                );

        assertThat(request.depots()).hasSize(1);
        assertThat(request.customers()).hasSize(1);
        assertThat(request.vehicles()).hasSize(1);
    }


        @Test
        void coreCreateRequest_shouldUseDefaultDateRangeWhenDatesAreNull() {

                DeliveryWindow window = new DeliveryWindow();
                window.setIncludeNullsFromDate(true);
                window.setIncludeNullsToDate(true);

                when(vehicleRepository.findDTOsByCompanyId(companyId))
                        .thenReturn(List.of());

                when(unitRepository.findRotubleUnitsByCompanyId(companyId))
                        .thenReturn(List.of());

                when(orderRepository.searchRoutableOrders(
                        any(),
                        anyBoolean(),
                        anyBoolean(),
                        any(),
                        any(),
                        anySet()
                )).thenReturn(List.of());

                service.coreCreateRequest(
                        companyId,
                        TypeSolver.GREEDY,
                        window,
                        false,
                        false
                );

                verify(orderRepository)
                .searchRoutableOrders(
                        eq(companyId),
                        eq(true),
                        eq(true),
                        eq(Instant.parse("1900-01-01T00:00:00Z")),
                        eq(Instant.parse("9999-12-31T23:59:59Z")),
                        anySet()
                );
        }

        @Test
        void clusters_shouldDelegateToClusterService() {
        
            ClusterConfig config = mock(ClusterConfig.class);
        
            RoutingRequest request =
                    new RoutingRequest(
                            "test",
                            List.of(),
                            List.of(),
                            List.of(),
                            new double[0][0],
                            TypeSolver.GREEDY
                    );
        
            DbscanResult expected =
                    DbscanResult.builder()
                            .clusters(List.of())
                            .build();
        
            when(clusterService.clusterClients(
                    config,
                    request.customers(),
                    request.depots(),
                    request.distanceMatrix()
            )).thenReturn(expected);
        
            DbscanResult result =
                    service.clusters(
                            config,
                            request
                    );
        
            assertThat(result)
                    .isSameAs(expected);
        
            verify(clusterService)
                    .clusterClients(
                            config,
                            request.customers(),
                            request.depots(),
                            request.distanceMatrix()
                    );
        }
        @Test
        void clustersForCompany_shouldBuildRequestAndCluster() {

                UUID companyId = UUID.randomUUID();

                ClusterConfig config =
                        ClusterConfig.builder()
                                .build();

                RoutingRequest request =
                        new RoutingRequest(
                                "test",
                                List.of(),
                                List.of(),
                                List.of(),
                                new double[0][0],
                                TypeSolver.GREEDY
                        );

                DbscanResult expected =
                        DbscanResult.builder()
                                .clusters(List.of())
                                .build();

                FmsRoutingServiceImpl spy =
                        Mockito.spy(service);

                doReturn(request)
                        .when(spy)
                        .coreCreateRequest(
                                eq(companyId),
                                eq(TypeSolver.GREEDY),
                                eq(config.getWindow()),
                                eq(false),
                                eq(true)
                        );

                doReturn(expected)
                        .when(spy)
                        .clusters(
                                config,
                                request
                        );

                DbscanResult result =
                        spy.clustersForCompany(
                                companyId,
                                config,
                                TypeSolver.GREEDY,
                                true
                        );

                assertThat(result)
                        .isSameAs(expected);
        }
        @Test
        void getRoutingRequestForCompany_custom_shouldBuildRequest() {
        
            UUID depotId = UUID.randomUUID();
            UUID customerId = UUID.randomUUID();
        
            Set<UUID> depots = Set.of(depotId);
            Set<UUID> customers = Set.of(customerId);
        
            when(vehicleRepository.retrieveDTOsByCompanyIdInSelectedDepots(
                    companyId,
                    depots
            )).thenReturn(List.of(
                    new VehicleDto(
                            UUID.randomUUID(),
                            100,
                            depotId
                    )
            ));
        
            when(unitRepository.retrieveDepotsByCompanyId(
                    companyId,
                    depots
            )).thenReturn(List.of(
                    new RoutableUnit(
                            depotId,
                            BigDecimal.valueOf(40),
                            BigDecimal.valueOf(-3)
                    )
            ));
        
            when(orderRepository.retrieveSelectedClientsByCompanyIdAndStatus(
                    eq(companyId),
                    anySet(),
                    eq(customers)
            )).thenReturn(List.of(
                    new RoutableOrder(
                            customerId,
                            BigDecimal.valueOf(41),
                            BigDecimal.valueOf(-4)
                    )
            ));
        
            RoutingRequest result =
                    service.getRoutingRequestForCompany(
                            companyId,
                            customers,
                            depots,
                            TypeSolver.GREEDY
                    );
        
            assertThat(result.depots())
                    .hasSize(1);
        
            assertThat(result.customers())
                    .hasSize(1);
        
            assertThat(result.vehicles())
                    .hasSize(1);
        
            assertThat(result.distanceMatrix().length)
                    .isEqualTo(2);
        }
}