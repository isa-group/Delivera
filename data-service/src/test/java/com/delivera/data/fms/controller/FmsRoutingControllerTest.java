package com.delivera.data.fms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.data.fms.dto.ClusterConfig;
import com.delivera.data.fms.dto.DbscanResult;
import com.delivera.data.fms.dto.DeliveryWindow;
import com.delivera.data.fms.dto.InstanceCatalog;
import com.delivera.data.fms.dto.RoutingRequest;
import com.delivera.data.fms.dto.RoutingResponse;
import com.delivera.data.fms.dto.SolveClusterRequest;
import com.delivera.data.fms.dto.TypeSolver;
import com.delivera.data.fms.service.FmsRoutingService;

@ExtendWith (MockitoExtension.class)
class FmsRoutingControllerTest {

        @Mock 
        private FmsRoutingService fmsRoutingService;

        @Mock
        private SecurityUtils securityUtils;

        @InjectMocks 
        private FmsRoutingController controller;

        private UUID companyId;

        @BeforeEach 
        void setup() {
        companyId = UUID.randomUUID();
        }
    
        @Test
        void dataGet_shouldReturnRoutingRequest() {

                RoutingRequest request =
                        new RoutingRequest(
                                "test",
                                List.of(),
                                List.of(),
                                List.of(),
                                new double[0][0],
                                null
                        );

                when(securityUtils.getCurrentCompanyId())
                        .thenReturn(companyId);

                when(fmsRoutingService.getRoutingRequestForCompany(
                        companyId,
                        null,
                        true,
                        false
                )).thenReturn(request);

                ResponseEntity<RoutingRequest> response =
                        controller.data();

                assertThat(response.getStatusCode().is2xxSuccessful())
                        .isTrue();

                assertThat(response.getBody())
                        .isSameAs(request);
        }
        @Test
        void dataPost_shouldReturnRoutingRequest() {

                DeliveryWindow window = new DeliveryWindow();

                RoutingRequest request =
                        new RoutingRequest(
                                "problem",
                                List.of(),
                                List.of(),
                                List.of(),
                                new double[0][0],
                                null
                        );

                when(securityUtils.getCurrentCompanyId())
                        .thenReturn(companyId);

                when(fmsRoutingService.coreCreateRequest(
                        companyId,
                       null,
                        window,
                        true,
                        false
                )).thenReturn(request);

                ResponseEntity<RoutingRequest> response =
                        controller.data(
                                window
                        );

                assertThat(response.getBody())
                        .isSameAs(request);
        }
        @Test
        void benchmarks_shouldReturnCatalog() {

                InstanceCatalog catalog =
                        mock(InstanceCatalog.class);

                when(fmsRoutingService.getInstanceSummaries())
                        .thenReturn(catalog);

                ResponseEntity<InstanceCatalog> response =
                        controller.benchmarks();

                assertThat(response.getBody())
                        .isSameAs(catalog);
        }
        @Test
        void benchmarkInstance_shouldReturnRequest() {
        
                RoutingRequest request =
                        new RoutingRequest(
                                "A",
                                List.of(),
                                List.of(),
                                List.of(),
                                new double[0][0],
                                null
                        );

                when(fmsRoutingService.getInstance(any(), any(), anyBoolean()))
                        .thenReturn(request);

                ResponseEntity<RoutingRequest> response =
                        controller.benchmarkInstace("A");

                assertThat(response.getBody())
                        .isSameAs(request);
        }
        @Test
        void cluster_shouldUseExistingWindow() {

                DeliveryWindow window = new DeliveryWindow();

                ClusterConfig config =
                        ClusterConfig.builder()
                                .window(window)
                                .build();

                DbscanResult result =
                        DbscanResult.builder()
                                .clusters(List.of())
                                .build();

                when(securityUtils.getCurrentCompanyId())
                        .thenReturn(companyId);

                when(fmsRoutingService.clustersForCompany(
                        companyId,
                        config,
                        TypeSolver.GREEDY,
                        false
                )).thenReturn(result);

                ResponseEntity<DbscanResult> response =
                        controller.cluster(
                                TypeSolver.GREEDY,
                                config
                        );

                assertThat(response.getBody())
                        .isSameAs(result);

                assertThat(config.getWindow())
                        .isSameAs(window);
        }
        @Test
        void cluster_shouldCreateDefaultWindowWhenNull() {

                ClusterConfig config =
                        ClusterConfig.builder()
                                .window(null)
                                .build();

                DbscanResult result =
                        DbscanResult.builder()
                                .clusters(List.of())
                                .build();

                when(securityUtils.getCurrentCompanyId())
                        .thenReturn(companyId);

                when(fmsRoutingService.clustersForCompany(
                        eq(companyId),
                        eq(config),
                        eq(TypeSolver.GREEDY),
                        eq(false)
                )).thenReturn(result);

                controller.cluster(
                        TypeSolver.GREEDY,
                        config
                );

                assertThat(config.getWindow())
                        .isNotNull();
        }

        @Test
        void solveCluster_shouldCallService() {

                UUID customer = UUID.randomUUID();
                UUID depot = UUID.randomUUID();

                SolveClusterRequest request =
                        new SolveClusterRequest();

                request.setCustomers(Set.of(customer));
                request.setDepots(Set.of(depot));

                RoutingResponse responseExpected =
                        mock(RoutingResponse.class);

                when(securityUtils.getCurrentCompanyId())
                        .thenReturn(companyId);

                when(fmsRoutingService.solverForCompany(
                        companyId,
                        request.getCustomers(),
                        request.getDepots(),
                        TypeSolver.GREEDY
                )).thenReturn(responseExpected);

                ResponseEntity<RoutingResponse> response =
                        controller.solveCluster(
                                TypeSolver.GREEDY,
                                request
                        );

                assertThat(response.getBody())
                        .isSameAs(responseExpected);
        }
        @Test
        void clusterBenchmarkInstance_shouldReturnDbscanResult() {

                ClusterConfig config =
                        ClusterConfig.builder().build();

                DbscanResult result =
                        DbscanResult.builder()
                                .clusters(List.of())
                                .build();

                when(
                        fmsRoutingService.clustersForInstance(
                                any(),
                                any(),
                                any()
                        )
                ).thenReturn(result);

                ResponseEntity<DbscanResult> response =
                        controller.clsuterbenchmarkInstace(
                                "A",
                                config
                        );

                assertThat(response.getBody())
                        .isSameAs(result);

                verify(fmsRoutingService)
                        .clustersForInstance(
                                "A",
                                config,
                               null
                        );
        }
}