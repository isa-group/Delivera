package com.delivera.controller;

import com.delivera.depot.controller.UnitController;
import com.delivera.depot.dto.UnitDetailResponse;
import com.delivera.depot.dto.UnitRequest;
import com.delivera.depot.dto.UnitResponse;
import com.delivera.depot.model.UnitType;
import com.delivera.service.UnitService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UnitControllerTest {

    @Mock private UnitService unitService;
    @InjectMocks private UnitController controller;

    // UnitResponse(id, name, type, address, lat, lon, createdAt, defaultPriority)
    private static UnitResponse sampleUnit(UUID id) {
        return new UnitResponse(id, "Almacén", "WAREHOUSE", null, null, null, null, null);
    }

    // UnitDetailResponse(id, name, type, address, lat, lon, createdAt, defaultPriority, workers)
    private static UnitDetailResponse sampleDetail(UUID id) {
        return new UnitDetailResponse(id, "Almacén", "WAREHOUSE", null, null, null, null, null, List.of());
    }

    @Test
    void list_returns200WithItems() {
        when(unitService.getByCompany()).thenReturn(List.of(sampleUnit(UUID.randomUUID())));
        var resp = controller.list();
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).hasSize(1);
    }

    @Test
    void listExternal_returns200() {
        when(unitService.getExternalUnits()).thenReturn(List.of());
        assertThat(controller.listExternal().getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void listExternalCompanies_returns200() {
        when(unitService.getExternalCompanies()).thenReturn(List.of());
        assertThat(controller.listExternalCompanies().getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void create_returns201() {
        // UnitRequest(name, type, address, lat, lon, defaultPriority)
        UnitRequest req = new UnitRequest("Tienda", UnitType.STORE, "Calle 1", null, null, null);
        UUID id = UUID.randomUUID();
        when(unitService.create(req)).thenReturn(sampleUnit(id));
        var resp = controller.create(req);
        assertThat(resp.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void detail_returns200() {
        UUID id = UUID.randomUUID();
        when(unitService.getDetail(id)).thenReturn(sampleDetail(id));
        var resp = controller.detail(id);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
    }

    @Test
    void assignWorker_returns200() {
        UUID id = UUID.randomUUID();
        UUID wId = UUID.randomUUID();
        when(unitService.assignWorker(id, wId)).thenReturn(sampleDetail(id));
        assertThat(controller.assignWorker(id, wId).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void unassignWorker_returns200() {
        UUID id = UUID.randomUUID();
        UUID wId = UUID.randomUUID();
        when(unitService.unassignWorker(id, wId)).thenReturn(sampleDetail(id));
        assertThat(controller.unassignWorker(id, wId).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void update_returns200() {
        UUID id = UUID.randomUUID();
        UnitRequest req = new UnitRequest("Tienda 2", UnitType.STORE, "Calle 2", null, null, null);
        when(unitService.update(id, req)).thenReturn(sampleUnit(id));
        var resp = controller.update(id, req);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void delete_returns204() {
        UUID id = UUID.randomUUID();
        doNothing().when(unitService).delete(id);
        assertThat(controller.delete(id).getStatusCode().value()).isEqualTo(204);
        verify(unitService).delete(id);
    }
}
