package com.delivera.data.vehicle.controller;

import com.delivera.data.vehicle.dto.VehicleRequest;
import com.delivera.data.vehicle.dto.VehicleResponse;
import com.delivera.data.vehicle.service.VehicleService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleControllerTest {
    @Mock
    private VehicleService vehicleService;

    @InjectMocks
    private VehicleController controller;

    @Test
    void list_shouldReturnVehicleList() {

       VehicleResponse response = new VehicleResponse(
                UUID.randomUUID(),
                "1234*BC",
                100,
               UUID.randomUUID(),
               "Depot",
                Instant.now()
        );

        when(vehicleService.getByCompany())
               .thenReturn(List.of(response));

        var result = controller.list();

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).hasSize(1);
    }
    @Test
    void create_shouldReturnCreatedVehicle() {

        VehicleRequest request = new VehicleRequest(
                "1234ABC",
               100,
               UUID.randomUUID()
        );

       VehicleResponse response = new VehicleResponse(
                UUID.randomUUID(),
                "1234ABC",
                100,
                UUID.randomUUID(),
            "Depot",
               Instant.now()
        );

        when(vehicleService.create(request))
                .thenReturn(response);

        var result = controller.create(request);

        assertThat(result.getStatusCode().value()).isEqualTo(201);
        assertThat(result.getBody()).isSameAs(response);
    }

    @Test
    void detail_shouldReturnVehicle() {

        UUID vehicleId = UUID.randomUUID();

        VehicleResponse response = new VehicleResponse(
                vehicleId,
                "1234ABC",
                100,
               UUID.randomUUID(),
"Depot",
               Instant.now()
        );

        when(vehicleService.getDetail(vehicleId))
                .thenReturn(response);

        var result = controller.detail(vehicleId);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isSameAs(response);
    }

    @Test
    void update_shouldReturnUpdatedVehicle() {

        UUID vehicleId = UUID.randomUUID();

        VehicleRequest request = new VehicleRequest(
                "9999ZZZ",
                200,
                UUID.randomUUID()
        );

        VehicleResponse response = new VehicleResponse(
                vehicleId,
                "9999ZZZ",
                200,
                UUID.randomUUID(),
                "Depot",
                Instant.now()
        );

        when(vehicleService.update(vehicleId, request))
                .thenReturn(response);

        var result = controller.update(vehicleId, request);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isSameAs(response);
    }

    @Test
    void delete_shouldReturnNoContent() {

        UUID vehicleId = UUID.randomUUID();

        var result = controller.delete(vehicleId);

        assertThat(result.getStatusCode().value()).isEqualTo(204);

        verify(vehicleService).delete(vehicleId);
    }
}