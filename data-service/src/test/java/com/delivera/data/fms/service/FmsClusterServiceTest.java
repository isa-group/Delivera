package com.delivera.data.fms.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;

import com.delivera.data.fms.dto.Cluster;
import com.delivera.data.fms.dto.ClusterConfig;
import com.delivera.data.fms.dto.CustomerDto;
import com.delivera.data.fms.dto.DbscanResult;
import com.delivera.data.fms.dto.DepotDto;

@ExtendWith (MockitoExtension.class)
class FmsClusterServiceTest {

        private FmsClusterService service;

        @BeforeEach 
        void setup() {
                service = new FmsClusterService();
        }

        private CustomerDto customer(
                String id,
                int matrixIndex,
                double lat,
                double lng
        ) {
                return new CustomerDto(
                        id,
                        1,
                        lat,
                        lng,
                        matrixIndex
                );
        }

        private DepotDto depot(
                String id,
                int matrixIndex,
                double lat,
                double lng
        ) {
                return new DepotDto(
                        id,
                        lat,
                        lng,
                        matrixIndex
                );
        }

        @Test
        void onlyOneCluster_shouldCreateSingleClusterWithAllCustomers() {

                CustomerDto c1 = customer("1", 0, 40, -3);
                CustomerDto c2 = customer("2", 1, 41, -4);

                DbscanResult result = service.onlyOneCluster(
                        List.of(c1, c2),
                        List.of(),
                        new double[2][2]
                );

                assertThat(result.getClusters())
                        .hasSize(1);

                assertThat(result.getClusters().get(0).getCustomers())
                        .hasSize(2);
                }

                @Test
                void getNeighbors_shouldReturnOnlyCustomersInsideRadius() {

                CustomerDto c1 = customer("1", 0, 40, -3);
                CustomerDto c2 = customer("2", 1, 40, -3);
                CustomerDto c3 = customer("3", 2, 40, -3);

                double[][] matrix = {
                        {0, 5, 20},
                        {5, 0, 20},
                        {20, 20, 0}
                };

                List<CustomerDto> neighbors =
                        service.getNeighbors(
                                10,
                                c1,
                                List.of(c1, c2, c3),
                                matrix
                        );

                assertThat(neighbors)
                        .containsExactly(c2);
        }

        @Test
        void getEdges_shouldReturnMostDistantCustomers() {

                CustomerDto c1 = customer("1", 0, 40, -3);
                CustomerDto c2 = customer("2", 1, 40, -3);
                CustomerDto c3 = customer("3", 2, 40, -3);

                double[][] matrix = {
                        {0, 10, 50},
                        {10, 0, 5},
                        {50, 5, 0}
                };

                Cluster cluster = Cluster.of();
                cluster.add(c1);
                cluster.add(c2);
                cluster.add(c3);

                Map<String, CustomerDto> result =
                        service.getEdges(
                                cluster,
                                matrix
                        );

                assertThat(result.get("LEFT"))
                        .isNotNull();

                assertThat(result.get("RIGHT"))
                        .isNotNull();

                assertThat(result.get("LEFT"))
                        .isNotEqualTo(result.get("RIGHT"));
                }

        @Test
        void assignDepots_shouldAssignNearbyDepot_zero_depots() {

                CustomerDto customer =
                        customer("1", 1, 40, -3);

                Cluster cluster = Cluster.of();
                cluster.add(customer);
                cluster.close();

                DepotDto depot =
                        depot("D1", 0, -15, -3);

                ClusterConfig config =
                        ClusterConfig.builder()
                                .maxDepotRadiusKm(100)
                                .build();

                DbscanResult result =
                        DbscanResult.builder()
                                .clusters(List.of(cluster))
                                .build();
                
                double[][] matrix = new  double[2][2];

                DbscanResult assigned =
                        service.assignDepots(
                                config,
                                result,
                                List.of(depot),
                                matrix
                        );

                assertThat(
                        assigned.getClusters()
                                .get(0)
                                .getDepots().isEmpty()
                ).isTrue();
        }

        @Test
        void assignDepots_shouldAssignNearbyDepot_one_depot() {

                CustomerDto customer =
                        customer("1", 1, 40, -3);

                Cluster cluster = Cluster.of();
                cluster.add(customer);
                cluster.close();

                DepotDto depot =
                        depot("D1", 0, 40, -3);

                ClusterConfig config =
                        ClusterConfig.builder()
                                .maxDepotRadiusKm(101)
                                .build();

                DbscanResult result =
                        DbscanResult.builder()
                                .clusters(List.of(cluster))
                                .build();
                
                double[][] matrix = new  double[2][2];

                DbscanResult assigned =
                        service.assignDepots(
                                config,
                                result,
                                List.of(depot),
                                matrix
                        );

                assertThat(
                        assigned.getClusters()
                                .get(0)
                                .getDepots()
                ).contains(0);
        }

        @Test
        void clusterClients_shouldExecuteDbscanFlow_splitted() {
        
                CustomerDto c1 =
                        customer("1", 0, 40, -3);

                CustomerDto c2 =
                        customer("2", 1, 40, -3);

                ClusterConfig config =
                        ClusterConfig.builder()
                                .dbscan(true)
                                .maxRadiusKm(100)
                                .minClusterSize(1)
                                .maxClusterSize(10)
                                .build();

                double[][] matrix = {
                        {0, 101},
                        {101, 0}
                };

                DbscanResult result =
                        service.clusterClients(
                                config,
                                List.of(c1, c2),
                                List.of(),
                                matrix
                        );

                assertThat(result.getClusters())
                        .hasSize(2);
        }


        @Test
        void clusterClients_shouldExecuteDbscanFlow_not_splitted() {
        
                CustomerDto c1 =
                        customer("1", 0, 40, -3);

                CustomerDto c2 =
                        customer("2", 1, 40, -3);

                ClusterConfig config =
                        ClusterConfig.builder()
                                .dbscan(true)
                                .maxRadiusKm(100)
                                .minClusterSize(1)
                                .maxClusterSize(10)
                                .build();

                double[][] matrix = {
                        {0, 100},
                        {100, 0}
                };

                DbscanResult result =
                        service.clusterClients(
                                config,
                                List.of(c1, c2),
                                List.of(),
                                matrix
                        );

                assertThat(result.getClusters())
                        .hasSize(1);
        }


        @Test
        void clusterClients_shouldUseSingleClusterMode() {
        
                ClusterConfig config =
                        ClusterConfig.builder()
                                .dbscan(false)
                                .maxClusterSize(10)
                                .build();

                CustomerDto c1 =
                        customer("1", 0, 40, -3);

                CustomerDto c2 =
                        customer("2", 1, 40, -3);

                DbscanResult result =
                        service.clusterClients(
                                config,
                                List.of(c1, c2),
                                List.of(),
                                new double[][]{
                                        {0, 1},
                                        {1, 0}
                                }
                        );

                assertThat(result.getClusters())
                        .hasSize(1);
        }    
        
        
        @Test
        void clusterClients_shouldSubdivideAndAssignDepots() {
        
                CustomerDto c1 =
                        new CustomerDto("C1", 1, 40.0000, -3.0000, 0);

                CustomerDto c2 =
                        new CustomerDto("C2", 1, 40.0005, -3.0005, 1);

                CustomerDto c3 =
                        new CustomerDto("C3", 1, 40.0010, -3.0010, 2);

                CustomerDto c4 =
                        new CustomerDto("C4", 1, 40.0015, -3.0015, 3);

                CustomerDto c5 =
                        new CustomerDto("C5", 1, 40.0020, -3.0020, 4);

                DepotDto depot1 =
                        new DepotDto(
                                "D1",
                                40.0002,
                                -3.0002,
                                5
                        );

                DepotDto depot2 =
                        new DepotDto(
                                "D2",
                                40.0018,
                                -3.0018,
                                6
                        );

                DepotDto depotFar =
                        new DepotDto(
                                "D3",
                                55.0,
                                15.0,
                                7
                        );

                double[][] matrix = {

                        {0, 1, 8, 15, 25},
                        {1, 0, 7, 14, 24},
                        {8, 7, 0, 3, 20},
                        {15,14, 3, 0, 18},
                        {25,24,20,18, 0}
                };

                ClusterConfig config =
                        ClusterConfig.builder()
                                .dbscan(false)
                                .maxClusterSize(2)
                                .maxDepotRadiusKm(50)
                                .build();

                DbscanResult result =
                        service.clusterClients(
                                config,
                                List.of(c1, c2, c3, c4, c5),
                                List.of(depot1, depot2, depotFar),
                                matrix
                        );

                assertThat(result.getClusters())
                        .hasSizeGreaterThan(1);

                assertThat(
                        result.getClusters()
                                .stream()
                                .allMatch(
                                cluster ->
                                        cluster.totalClients() <= 2
                                )
                        ).isTrue();

                boolean foundAssignedDepot =
                        result.getClusters()
                                .stream()
                                .anyMatch(
                                        cluster ->
                                                !cluster.getDepots().isEmpty()
                                );

                assertThat(foundAssignedDepot)
                        .isTrue();

                boolean depotFarAssigned =
                        result.getClusters()
                                .stream()
                                .flatMap(
                                        cluster ->
                                                cluster.getDepots().stream()
                                )
                                .anyMatch(index -> index == 7);

                assertThat(depotFarAssigned)
                        .isFalse();
        }

        @Test
        void subdivideCluster_shouldReturnSameClusterWhenOnlyOneCustomer() {
        
                CustomerDto customer =
                        new CustomerDto(
                                "C1",
                                1,
                                40.0,
                                -3.0,
                                0
                        );

                Cluster cluster = Cluster.of();
                cluster.add(customer);

                ClusterConfig config =
                        ClusterConfig.builder()
                                .maxClusterSize(1)
                                .build();

                List<Cluster> result =
                        service.subdivideCluster(
                                config,
                                cluster,
                                new double[][]{{0}}
                        );

                assertThat(result).hasSize(1);
                assertThat(result.get(0)).isSameAs(cluster);
        }
        
        @Test
        void subdivideCluster_shouldHandleEqualDistances() {
        
                CustomerDto c1 =
                        new CustomerDto("C1", 1, 40d, -3d, 0);

                CustomerDto c2 =
                        new CustomerDto("C2", 1, 41d, -3d, 1);

                CustomerDto c3 =
                        new CustomerDto("C3", 1, 42d, -3d, 2);

                Cluster cluster = Cluster.of();

                cluster.add(c1);
                cluster.add(c2);
                cluster.add(c3);

                double[][] matrix = {
                        {0,10,5},
                        {10,0,5},
                        {5,5,0}
                };

                ClusterConfig config =
                        ClusterConfig.builder()
                                .maxClusterSize(2)
                                .build();

                List<Cluster> result =
                        service.subdivideCluster(
                                config,
                                cluster,
                                matrix
                        );

                assertThat(result).hasSize(2);
                assertThat(
                        result.stream()
                                .mapToInt(Cluster::totalClients)
                                .sum()
                ).isEqualTo(3);
        }
        @Test
        void silenceNoise_shouldCreateNewClusterWhenNoiseCannotBeAssigned() {

                CustomerDto clusterCustomer =
                        new CustomerDto(
                                "A",
                                1,
                                40.0,
                                -3.0,
                                0
                        );

                CustomerDto noiseCustomer =
                        new CustomerDto(
                                "B",
                                1,
                                60.0,
                                20.0,
                                1
                        );

                Cluster cluster = Cluster.of();
                cluster.add(clusterCustomer);

                DbscanResult initial =
                        DbscanResult.builder()
                                .clusters(new ArrayList<>(List.of(cluster)))
                                .noise(List.of(noiseCustomer))
                                .build();

                ClusterConfig config =
                        ClusterConfig.builder()
                                .noiseClusterMaxDistanceKm(1d)
                                .noiseMaxDistanceKm(1d)
                                .build();

                double[][] matrix = {
                        {0,2000},
                        {2000,0}
                };

                DbscanResult result =
                        service.silenceNoise(
                                config,
                                initial,
                                matrix
                        );

                assertThat(result.getClusters())
                        .hasSize(2);

                assertThat(result.getClusters())
                        .anyMatch(
                                c ->
                                        c.totalClients() == 1
                                        && c.contains(noiseCustomer)
                        );
        }
        @Test
        void silenceNoise_shouldAttachCustomerToClosestCluster() {
        
                CustomerDto c1 =
                        new CustomerDto(
                                "A",
                                1,
                                40.0000,
                                -3.0000,
                                0
                        );

                CustomerDto noise =
                        new CustomerDto(
                                "B",
                                1,
                                40.0001,
                                -3.0001,
                                1
                        );

                Cluster cluster = Cluster.of();
                cluster.add(c1);

                DbscanResult initial =
                        DbscanResult.builder()
                                .clusters(new ArrayList<>(List.of(cluster)))
                                .noise(List.of(noise))
                                .build();

                ClusterConfig config =
                        ClusterConfig.builder()
                                .noiseClusterMaxDistanceKm(10d)
                                .noiseMaxDistanceKm(10d)
                                .build();

                double[][] matrix = {
                        {0,1},
                        {1,0}
                };

                DbscanResult result =
                        service.silenceNoise(
                                config,
                                initial,
                                matrix
                        );

                assertThat(result.getClusters())
                        .hasSize(1);

                assertThat(
                        result.getClusters()
                                .get(0)
                                .totalClients()
                ).isEqualTo(2);

                assertThat(result.getNoise())
                        .isEmpty();
        }
}