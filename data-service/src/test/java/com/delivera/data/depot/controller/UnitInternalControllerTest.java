package com.delivera.data.depot.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.delivera.data.depot.dto.AssignRequest;
import com.delivera.data.depot.dto.UnitRequest;
import com.delivera.data.depot.dto.UnitResponse;
import com.delivera.data.depot.model.UnitType;
import com.delivera.data.depot.service.UnitService;

@ExtendWith (MockitoExtension.class)
class UnitInternalControllerTest {

    @Mock 
    private UnitService unitService;

    @InjectMocks 
    private UnitInternalController controller;

    private UUID unitId;
    private UUID companyId;
    private UUID orgId;

    @BeforeEach 
    void setUp() {
        unitId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        orgId = UUID.randomUUID();
    }
    @Test
    void createSeed_shouldReturnCreatedAndUnitId() {

        UnitResponse response =
                new UnitResponse(
                        unitId,
                        "Warehouse",
                        "WAREHOUSE",
                        "Address",
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        Instant.now(),
                        null
                );

        UnitRequest request =
                new UnitRequest(
                        "Warehouse",
                        UnitType.WAREHOUSE,
                        "Address",
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        null
                );

        when(
                unitService.createSeed(
                        request,
                        orgId,
                        companyId
                )
        ).thenReturn(response);

        ResponseEntity<UUID> result =
                controller.createSeed(
                        orgId,
                        companyId,
                        request
                );

        assertThat(result.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(result.getBody())
                .isEqualTo(unitId);
    }
    @Test
    void createAssign_shouldReturnCreated() {

        AssignRequest request =
                new AssignRequest();

        request.setWorkerId(UUID.randomUUID());
        request.setUserId(UUID.randomUUID());
        request.setCompanyId(companyId);

        ResponseEntity<Void> response =
                controller.createAssign(
                        unitId,
                        request
                );

        verify(unitService)
                .assignWorkerSeed(
                        unitId,
                        request
                );

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }
    @Test
    void unassignWorkerOfAllUnits_shouldReturnNoContent() {
    
        UUID workerId = UUID.randomUUID();
    
        ResponseEntity<Void> response =
                controller.unassignWorkerOfAllUnits(
                        workerId
                );
    
        verify(unitService)
                .unassignWorkerOfAllUnits(
                        workerId
                );
    
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }
}