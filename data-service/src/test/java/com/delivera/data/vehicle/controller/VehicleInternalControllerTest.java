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
class VehicleInternalControllerTest {

    @Mock
    private VehicleService vehicleService;

    @InjectMocks
    private VehicleInternalController controller;

    @Test
    void createSeed_shouldReturnCreated() {

        UUID companyId = UUID.randomUUID();

        VehicleRequest request = new VehicleRequest(
                "1234ABC",
                100,
                UUID.randomUUID()
        );

        var result = controller.createSeed(request, companyId);

        assertThat(result.getStatusCode().value()).isEqualTo(201);

        verify(vehicleService).createSeed(companyId, request);
    }

    @Test
    void getByCompany_shouldReturnVehicles() {

        UUID companyId = UUID.randomUUID();

        VehicleResponse response = new VehicleResponse(
                UUID.randomUUID(),
                "1234ABC",
                100,
                UUID.randomUUID(),
                "Depot",
                Instant.now()
        );

        when(vehicleService.getByCompany(companyId))
                .thenReturn(List.of(response));

        var result = controller.getByCompany(companyId);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).hasSize(1);
    }

    @Test
    void getByCompany_shouldReturnEmptyList() {

        UUID companyId = UUID.randomUUID();

        when(vehicleService.getByCompany(companyId))
                .thenReturn(List.of());

        var result = controller.getByCompany(companyId);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isEmpty();
    }
}