package com.delivera.data.vehicle.service;

import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.data.depot.model.OperationalUnit;
import com.delivera.data.depot.repository.OperationalUnitRepository;
import com.delivera.data.exception.UnitNotFoundException;
import com.delivera.data.exception.VehicleNotFoundException;
import com.delivera.data.exception.VehiclePlateConflictException;
import com.delivera.data.space.service.SpaceVehicles;
import com.delivera.data.vehicle.dto.VehicleRequest;
import com.delivera.data.vehicle.dto.VehicleResponse;
import com.delivera.data.vehicle.model.Vehicle;
import com.delivera.data.vehicle.repository.VehicleRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private OperationalUnitRepository unitRepository;

    @Mock
    private SecurityUtils securityUtils;

    @Mock
    private SpaceVehicles spaceVehicles;

    @InjectMocks
    private VehicleService vehicleService;

    private UUID companyId;
    private UUID vehicleId;
    private UUID depotId;
    private UUID orgId;

    private VehicleRequest request;
    private OperationalUnit depot;
    private Vehicle vehicle;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        vehicleId = UUID.randomUUID();
        depotId = UUID.randomUUID();
        orgId = UUID.randomUUID();

        request = new VehicleRequest(
                "1234ABC",
                100,
                depotId
        );

        depot = new OperationalUnit();
        depot.setId(depotId);
        depot.setCompanyId(companyId);
        depot.setOrgId(orgId);
        depot.setName("Depot Test");

        vehicle = new Vehicle();
        vehicle.setId(vehicleId);
        vehicle.setCompanyId(companyId);
        vehicle.setDepot(depot);
        vehicle.setPlate("1234ABC");
        vehicle.setCapacity(100);
    }

    @Test
    void create_shouldCreateVehicle() {

        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(vehicleRepository.existsByCompanyIdAndPlate(companyId, "1234ABC"))
                .thenReturn(false);
        when(unitRepository.findByIdAndCompanyId(depotId, companyId))
                .thenReturn(Optional.of(depot));
        when(vehicleRepository.save(any(Vehicle.class)))
                .thenReturn(vehicle);

        VehicleResponse response = vehicleService.create(request);

        assertThat(response).isNotNull();
        assertThat(response.plate()).isEqualTo("1234ABC");
        assertThat(response.capacity()).isEqualTo(100);

        verify(spaceVehicles).addWithRollBack(orgId.toString());
        verify(vehicleRepository).save(any(Vehicle.class));
    }

    @Test
    void create_shouldThrowConflictWhenPlateExists() {

        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(vehicleRepository.existsByCompanyIdAndPlate(companyId, "1234ABC"))
                .thenReturn(true);

        assertThatThrownBy(() -> vehicleService.create(request))
                .isInstanceOf(VehiclePlateConflictException.class);

        verify(vehicleRepository, never()).save(any());
        verify(spaceVehicles, never()).addWithRollBack(any());
    }

    @Test
    void create_shouldThrowUnitNotFound() {

        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(vehicleRepository.existsByCompanyIdAndPlate(companyId, "1234ABC"))
                .thenReturn(false);
        when(unitRepository.findByIdAndCompanyId(depotId, companyId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> vehicleService.create(request))
                .isInstanceOf(UnitNotFoundException.class);
    }

    @Test
    void create_shouldTranslateDataIntegrityViolationException() {

        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(vehicleRepository.existsByCompanyIdAndPlate(companyId, "1234ABC"))
                .thenReturn(false);
        when(unitRepository.findByIdAndCompanyId(depotId, companyId))
                .thenReturn(Optional.of(depot));

        when(vehicleRepository.save(any(Vehicle.class)))
                .thenThrow(new DataIntegrityViolationException("constraint"));

        assertThatThrownBy(() -> vehicleService.create(request))
                .isInstanceOf(VehiclePlateConflictException.class);
    }

    @Test
    void update_shouldModifyVehicle() {

        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);

        when(vehicleRepository.findByIdAndCompanyId(vehicleId, companyId))
                .thenReturn(Optional.of(vehicle));

        when(vehicleRepository.existsByCompanyIdAndPlateAndIdNot(
                companyId,
                "1234ABC",
                vehicleId))
                .thenReturn(false);

        when(unitRepository.findByIdAndCompanyId(depotId, companyId))
                .thenReturn(Optional.of(depot));

        when(vehicleRepository.save(any(Vehicle.class)))
                .thenReturn(vehicle);

        VehicleResponse response =
                vehicleService.update(vehicleId, request);

        assertThat(response).isNotNull();
        assertThat(response.plate()).isEqualTo("1234ABC");
    }

    @Test
    void update_shouldThrowVehicleNotFound() {

        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);

        when(vehicleRepository.findByIdAndCompanyId(vehicleId, companyId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> vehicleService.update(vehicleId, request))
                .isInstanceOf(VehicleNotFoundException.class);
    }

    @Test
    void update_shouldThrowConflictWhenPlateUsedByAnotherVehicle() {

        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);

        when(vehicleRepository.findByIdAndCompanyId(vehicleId, companyId))
                .thenReturn(Optional.of(vehicle));

        when(vehicleRepository.existsByCompanyIdAndPlateAndIdNot(
                companyId,
                "1234ABC",
                vehicleId))
                .thenReturn(true);

        assertThatThrownBy(() -> vehicleService.update(vehicleId, request))
                .isInstanceOf(VehiclePlateConflictException.class);
    }

    @Test
    void update_shouldAllowKeepingSamePlate() {

        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);

        when(vehicleRepository.findByIdAndCompanyId(vehicleId, companyId))
                .thenReturn(Optional.of(vehicle));

        when(vehicleRepository.existsByCompanyIdAndPlateAndIdNot(
                companyId,
                "1234ABC",
                vehicleId))
                .thenReturn(false);

        when(unitRepository.findByIdAndCompanyId(depotId, companyId))
                .thenReturn(Optional.of(depot));

        when(vehicleRepository.save(any(Vehicle.class)))
                .thenReturn(vehicle);

        VehicleResponse response =
                vehicleService.update(vehicleId, request);

        assertThat(response).isNotNull();
    }

    @Test
    void getByCompany_shouldReturnVehicles() {

        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);

        when(vehicleRepository.findAllByCompanyId(companyId))
                .thenReturn(List.of(vehicle));

        List<VehicleResponse> result =
                vehicleService.getByCompany();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).plate()).isEqualTo("1234ABC");
    }

    @Test
    void getByCompany_shouldReturnEmptyList() {

        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);

        when(vehicleRepository.findAllByCompanyId(companyId))
                .thenReturn(List.of());

        assertThat(vehicleService.getByCompany()).isEmpty();
    }

    @Test
    void getDetail_shouldReturnVehicle() {

        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);

        when(vehicleRepository.findByIdAndCompanyId(vehicleId, companyId))
                .thenReturn(Optional.of(vehicle));

        VehicleResponse response =
                vehicleService.getDetail(vehicleId);

        assertThat(response.id()).isEqualTo(vehicleId);
    }

    @Test
    void getDetail_shouldThrowVehicleNotFound() {

        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);

        when(vehicleRepository.findByIdAndCompanyId(vehicleId, companyId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> vehicleService.getDetail(vehicleId))
                .isInstanceOf(VehicleNotFoundException.class);
    }

    @Test
    void delete_shouldDeleteVehicle() {

        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(securityUtils.getCurrentOrgId()).thenReturn(orgId);

        when(vehicleRepository.findByIdAndCompanyId(vehicleId, companyId))
                .thenReturn(Optional.of(vehicle));

        vehicleService.delete(vehicleId);

        verify(spaceVehicles).deleteWithRollBack(orgId.toString());
        verify(vehicleRepository).delete(vehicle);
    }

    @Test
    void delete_shouldThrowVehicleNotFound() {

        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(securityUtils.getCurrentOrgId()).thenReturn(orgId);

        when(vehicleRepository.findByIdAndCompanyId(vehicleId, companyId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> vehicleService.delete(vehicleId))
                .isInstanceOf(VehicleNotFoundException.class);

        verify(vehicleRepository, never()).delete(any());
    }
}