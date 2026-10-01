package com.delivera.data.depot.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import com.delivera.data.depot.dto.B2BUnitResponse;
import com.delivera.data.depot.dto.UnitDetailResponse;
import com.delivera.data.depot.dto.UnitRequest;
import com.delivera.data.depot.dto.UnitResponse;
import com.delivera.data.depot.model.UnitType;
import com.delivera.data.depot.service.UnitService;

@ExtendWith (MockitoExtension.class)
class UnitControllerTest {

    @Mock 
    private UnitService unitService;

    @InjectMocks 
    private UnitController controller;

    private UUID unitId;
    private UUID companyId;

    @BeforeEach 
    void setUp() {
        unitId = UUID.randomUUID();
        companyId = UUID.randomUUID();
    }

    @Test
    void list_shouldReturnUnits() {

        List<UnitResponse> units = List.of(
                mock(UnitResponse.class)
        );

        when(unitService.getByCompany())
                .thenReturn(units);

        ResponseEntity<List<UnitResponse>> response =
                controller.list();

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody())
                .isSameAs(units);
    }
    @Test
    void listExternal_shouldReturnExternalUnits() {

        List<B2BUnitResponse> units = List.of(
                mock(B2BUnitResponse.class)
        );

        when(unitService.getExternalUnits(companyId))
                .thenReturn(units);

        ResponseEntity<List<B2BUnitResponse>> response =
                controller.listExternal(companyId);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody())
                .isSameAs(units);
    }
    @Test
    void getByOrganization_shouldReturnNamesMap() {
    
        Map<UUID, String> map =
                Map.of(companyId, "Warehouse");
    
        when(unitService.getByCompanyId(companyId))
                .thenReturn(map);
    
        ResponseEntity<Map<UUID, String>> response =
                controller.getByOrganization(companyId);
    
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);
    
        assertThat(response.getBody())
                .isEqualTo(map);
    }
    @Test
    void create_shouldReturnCreatedStatus() {

        UnitRequest request =
                new UnitRequest(
                        "Main",
                        UnitType.WAREHOUSE,
                        "Address",
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        null
                );

        UnitResponse unit =
                mock(UnitResponse.class);

        when(unitService.create(request))
                .thenReturn(unit);

        ResponseEntity<UnitResponse> response =
                controller.create(request);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(response.getBody())
                .isSameAs(unit);
    }
    @Test
    void detail_shouldReturnUnitDetail() {

        UnitDetailResponse detail =
                mock(UnitDetailResponse.class);

        when(unitService.getDetail(unitId))
                .thenReturn(detail);

        ResponseEntity<UnitDetailResponse> response =
                controller.detail(unitId);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody())
                .isSameAs(detail);
    }
    @Test
    void assignWorker_shouldReturnAssignedWorkers() {

        AssignRequest request =
                new AssignRequest();

        UUID workerId = UUID.randomUUID();

        Set<UUID> workers =
                Set.of(workerId);

        when(unitService.assignWorker(
                unitId,
                request
        )).thenReturn(workers);

        ResponseEntity<Set<UUID>> response =
                controller.assignWorker(
                        unitId,
                        request
                );

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody())
                .contains(workerId);
    }
    @Test
    void unassignWorker_shouldReturnRemainingWorkers() {
    
        UUID workerId = UUID.randomUUID();
    
        Set<UUID> workers =
                Set.of();
    
        when(unitService.unassignWorker(
                unitId,
                workerId
        )).thenReturn(workers);
    
        ResponseEntity<Set<UUID>> response =
                controller.unassignWorker(
                        unitId,
                        workerId
                );
    
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);
    
        assertThat(response.getBody())
                .isEmpty();
    }
    @Test
    void getWorkers_shouldReturnWorkers() {
    
        UUID workerId = UUID.randomUUID();
    
        Set<UUID> workers =
                Set.of(workerId);
    
        when(unitService.getWorkersIdByUnit(unitId))
                .thenReturn(workers);
    
        ResponseEntity<Set<UUID>> response =
                controller.getWorkers(unitId);
    
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);
    
        assertThat(response.getBody())
                .contains(workerId);
    }
    @Test
    void update_shouldReturnUpdatedUnit() {
    
        UnitRequest request =
                new UnitRequest(
                        "Updated",
                        UnitType.WAREHOUSE,
                        "Address",
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        null
                );
    
        UnitResponse unit =
                mock(UnitResponse.class);
    
        when(unitService.update(
                unitId,
                request
        )).thenReturn(unit);
    
        ResponseEntity<UnitResponse> response =
                controller.update(
                        unitId,
                        request
                );
    
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);
    
        assertThat(response.getBody())
                .isSameAs(unit);
    }
    @Test
    void delete_shouldReturnNoContent() {

        ResponseEntity<Void> response =
                controller.delete(unitId);

        verify(unitService)
                .delete(unitId);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }
}
