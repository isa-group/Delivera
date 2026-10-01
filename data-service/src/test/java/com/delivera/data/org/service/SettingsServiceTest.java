package com.delivera.data.org.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.delivera.data.depot.repository.OperationalUnitRepository;
import com.delivera.data.depot.repository.WorkerRepository;
import com.delivera.data.exception.CompanyHasActiveOrdersException;
import com.delivera.data.exception.SettingsAlreadyExistsException;
import com.delivera.data.exception.SettingsNotFoundException;
import com.delivera.data.order.repository.OrderEventRepository;
import com.delivera.data.order.repository.OrderMessageRepository;
import com.delivera.data.order.repository.OrderRepository;
import com.delivera.data.org.dto.CompanySettingsDTO;
import com.delivera.data.org.model.CompanySettings;
import com.delivera.data.org.repository.SettingsRepository;
import com.delivera.data.space.service.SpaceUnits;
import com.delivera.data.space.service.SpaceVehicles;
import com.delivera.data.vehicle.repository.VehicleRepository;

@ExtendWith (MockitoExtension.class)
class SettingsServiceTest {

    @Mock 
    private SettingsRepository repository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OperationalUnitRepository unitRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private OrderEventRepository orderEventRepository;

    @Mock
    private OrderMessageRepository orderMessageRepository;

    @Mock
    private SpaceUnits spaceUnits;

    @Mock
    private SpaceVehicles spaceVehicles;

    @InjectMocks 
    private SettingsService service;

    private UUID companyId;
    private UUID orgId;

    @BeforeEach 
    void setUp() {

        companyId = UUID.randomUUID();
        orgId = UUID.randomUUID();
    }

    private CompanySettings buildSettings() {

        CompanySettings settings = new CompanySettings();

        settings.setId(companyId);
        settings.setOrgId(orgId);

        return settings;
    }

    private CompanySettingsDTO buildRequest() {

        CompanySettingsDTO dto = new CompanySettingsDTO();

        dto.setCompanyId(companyId);
        dto.setOrgId(orgId);

        return dto;
    }
    @Test
    void get_shouldReturnSettings() {

        CompanySettings settings = buildSettings();

        when(repository.findById(companyId))
                .thenReturn(Optional.of(settings));

        CompanySettings result =
                service.get(companyId);

        assertThat(result)
                .isSameAs(settings);
    }
    @Test
    void get_shouldThrowSettingsNotFound() {

        when(repository.findById(companyId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> service.get(companyId)
        ).isInstanceOf(
                SettingsNotFoundException.class
        );
    }
    @Test
    void create_shouldCreateSettings() {

        CompanySettingsDTO request =
                buildRequest();

        when(repository.existsById(companyId))
                .thenReturn(false);

        when(repository.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        CompanySettings result =
                service.create(request);

        assertThat(result.getId())
                .isEqualTo(companyId);

        assertThat(result.getOrgId())
                .isEqualTo(orgId);
    }
    @Test
    void create_shouldThrowAlreadyExists() {

        CompanySettingsDTO request =
                buildRequest();

        when(repository.existsById(companyId))
                .thenReturn(true);

        assertThatThrownBy(
                () -> service.create(request)
        ).isInstanceOf(
                SettingsAlreadyExistsException.class
        );
    }
    @Test
    void update_shouldModifySettings() {
    
        CompanySettings settings =
                buildSettings();
    
        CompanySettingsDTO request =
                buildRequest();
    
        when(repository.findById(companyId))
                .thenReturn(Optional.of(settings));
    
        when(repository.save(any()))
                .thenAnswer(i -> i.getArgument(0));
    
        CompanySettings result =
                service.update(request);
    
        assertThat(result)
                .isSameAs(settings);
    }
    @Test
    void update_shouldThrowSettingsNotFound() {
    
        CompanySettingsDTO request =
                buildRequest();
    
        when(repository.findById(companyId))
                .thenReturn(Optional.empty());
    
        assertThatThrownBy(
                () -> service.update(request)
        ).isInstanceOf(
                SettingsNotFoundException.class
        );
    }
    @Test
    void deleteCompany_shouldThrowWhenPendingOrdersExist() {

        CompanySettings settings =
                buildSettings();

        when(repository.findById(companyId))
                .thenReturn(Optional.of(settings));

        when(
                orderRepository.existsByCompanyIdAndStatusIn(
                        eq(companyId),
                        anyList()
                )
        ).thenReturn(true);

        assertThatThrownBy(
                () -> service.deleteCompany(
                        companyId,
                        false
                )
        ).isInstanceOf(
                CompanyHasActiveOrdersException.class
        );
    }
    @Test
    void deleteCompany_shouldDeleteWhenConfirmed() {

        CompanySettings settings =
                buildSettings();

        when(repository.findById(companyId))
                .thenReturn(Optional.of(settings));

        when(
                orderRepository.existsByCompanyIdAndStatusIn(
                        eq(companyId),
                        anyList()
                )
        ).thenReturn(true);

        when(vehicleRepository.deleteByCompanyId(companyId))
                .thenReturn(1);

        when(unitRepository.deleteByCompanyId(companyId))
                .thenReturn(1);

        service.deleteCompany(
                companyId,
                true
        );

        verify(repository)
                .deleteById(companyId);
    }
    @Test
    void deleteCompany_shouldDeleteEverything() {
    
        CompanySettings settings =
                buildSettings();
    
        when(repository.findById(companyId))
                .thenReturn(Optional.of(settings));
    
        when(
                orderRepository.existsByCompanyIdAndStatusIn(
                        eq(companyId),
                        anyList()
                )
        ).thenReturn(false);
    
        when(vehicleRepository.deleteByCompanyId(companyId))
                .thenReturn(2);
    
        when(unitRepository.deleteByCompanyId(companyId))
                .thenReturn(3);
    
        service.deleteCompany(
                companyId,
                true
        );
    
        verify(spaceVehicles)
                .deleteAllWithRollBack(
                        orgId.toString(),
                        2
                );
    
        verify(spaceUnits)
                .deleteAllWithRollBack(
                        orgId.toString(),
                        3
                );
    
        verify(repository)
                .deleteById(companyId);
    }
    @Test
    void deleteOrganization_shouldDeleteEverything() {
    
        Set<UUID> companyIds =
                Set.of(
                        UUID.randomUUID(),
                        UUID.randomUUID()
                );
    
        when(vehicleRepository.deleteByCompanyIds(companyIds))
                .thenReturn(2);
    
        when(unitRepository.deleteByCompanyIds(companyIds))
                .thenReturn(4);
    
        service.deleteOrganization(
                orgId.toString(),
                companyIds
        );
    
        verify(vehicleRepository)
                .deleteByCompanyIds(companyIds);
    
        verify(unitRepository)
                .deleteByCompanyIds(companyIds);
    
        verify(repository)
                .deleteByCompanyIds(companyIds);
    
        verify(spaceVehicles)
                .deleteAllWithRollBack(
                        orgId.toString(),
                        2
                );
    
        verify(spaceUnits)
                .deleteAllWithRollBack(
                        orgId.toString(),
                        4
                );
    }
    @Test
    void deleteOrganization_shouldNotCallSpaceDeleteWhenZero() {

        Set<UUID> companyIds =
                Set.of(
                        UUID.randomUUID()
                );

        when(vehicleRepository.deleteByCompanyIds(companyIds))
                .thenReturn(0);

        when(unitRepository.deleteByCompanyIds(companyIds))
                .thenReturn(0);

        service.deleteOrganization(
                orgId.toString(),
                companyIds
        );

        verify(spaceVehicles, never())
                .deleteAllWithRollBack(
                        anyString(),
                        anyInt()
                );

        verify(spaceUnits, never())
                .deleteAllWithRollBack(
                        anyString(),
                        anyInt()
                );
    }
}