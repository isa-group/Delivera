package com.delivera.data.depot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.data.depot.dto.AssignRequest;
import com.delivera.data.depot.dto.UnitRequest;
import com.delivera.data.depot.dto.UnitResponse;
import com.delivera.data.depot.model.OperationalUnit;
import com.delivera.data.depot.model.UnitType;
import com.delivera.data.depot.model.UnitWorker;
import com.delivera.data.depot.repository.OperationalUnitRepository;
import com.delivera.data.depot.repository.WorkerRepository;
import com.delivera.data.exception.CompanyContextException;
import com.delivera.data.exception.UnitNameConflictException;
import com.delivera.data.exception.UnitNotFoundException;
import com.delivera.data.order.model.OrderPriority;
import com.delivera.data.org.service.OrgClient;
import com.delivera.data.space.service.SpaceUnits;

import reactor.core.publisher.Mono;

@ExtendWith (MockitoExtension.class)
class UnitServiceTest {

    @Mock 
    private OperationalUnitRepository unitRepository;

    @Mock
    private OrgClient orgClient;

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private SecurityUtils securityUtils;

    @Mock
    private SpaceUnits spaceUnits;

    @InjectMocks 
    private UnitService service;

    private UUID companyId;
    private UUID orgId;
    private UUID unitId;

    private UnitRequest request;
    private OperationalUnit unit;

    @BeforeEach 
    void setUp() {

        companyId = UUID.randomUUID();
        orgId = UUID.randomUUID();
        unitId = UUID.randomUUID();

        request = new UnitRequest(
                "Main Warehouse",
                UnitType.WAREHOUSE,
                "Address",
                BigDecimal.valueOf(40),
                BigDecimal.valueOf(-3),
                OrderPriority.NORMAL
        );

        unit = new OperationalUnit();

        unit.setId(unitId);
        unit.setCompanyId(companyId);
        unit.setOrgId(orgId);
        unit.setName("Main Warehouse");
        unit.setType(UnitType.WAREHOUSE);
    }
    @Test
    void create_shouldCreateUnit() {

        when(securityUtils.getCurrentCompanyId())
                .thenReturn(companyId);

        when(securityUtils.getCurrentOrgId())
                .thenReturn(orgId);

        when(unitRepository.existsByCompanyIdAndName(
                companyId,
                request.getName()
        )).thenReturn(false);

        when(
                orgClient.checkOrgData(any())
        ).thenReturn(Mono.just(true));

        when(
                unitRepository.save(any())
        ).thenReturn(unit);

        UnitResponse response =
                service.create(request);

        assertThat(response).isNotNull();

        verify(spaceUnits)
                .addWithRollBack(orgId.toString());

        verify(unitRepository)
                .save(any());
    }
    @Test
    void create_shouldThrowConflictWhenNameExists() {

        when(securityUtils.getCurrentCompanyId())
                .thenReturn(companyId);

        when(
                unitRepository.existsByCompanyIdAndName(
                        companyId,
                        request.getName()
                )
        ).thenReturn(true);

        assertThatThrownBy(
                () -> service.create(request)
        ).isInstanceOf(
                UnitNameConflictException.class
        );
    }
    @Test
    void create_shouldThrowCompanyContextException() {
    
        when(securityUtils.getCurrentCompanyId())
                .thenReturn(companyId);
    
        when(securityUtils.getCurrentOrgId())
                .thenReturn(orgId);
    
        when(
                unitRepository.existsByCompanyIdAndName(
                        companyId,
                        request.getName()
                )
        ).thenReturn(false);
    
        when(
                orgClient.checkOrgData(any())
        ).thenReturn(Mono.just(false));
    
        assertThatThrownBy(
                () -> service.create(request)
        ).isInstanceOf(
                CompanyContextException.class
        );
    }
    @Test
    void update_shouldModifyUnit() {
    
        when(securityUtils.getCurrentCompanyId())
                .thenReturn(companyId);
    
        when(
                unitRepository.findByIdAndCompanyId(
                        unitId,
                        companyId
                )
        ).thenReturn(Optional.of(unit));
    
        when(
                unitRepository.existsByCompanyIdAndNameAndIdNot(
                        companyId,
                        request.getName(),
                        unitId
                )
        ).thenReturn(false);
    
        when(
                unitRepository.save(any())
        ).thenReturn(unit);
    
        UnitResponse response =
                service.update(
                        unitId,
                        request
                );
    
        assertThat(response).isNotNull();
    }

    @Test
    void update_shouldThrowUnitNotFound() {

        when(securityUtils.getCurrentCompanyId())
                .thenReturn(companyId);

        when(
                unitRepository.findByIdAndCompanyId(
                        unitId,
                        companyId
                )
        ).thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> service.update(
                        unitId,
                        request
                )
        ).isInstanceOf(
                UnitNotFoundException.class
        );
    }
    @Test
    void update_shouldThrowConflict() {
    
        when(securityUtils.getCurrentCompanyId())
                .thenReturn(companyId);
    
        when(
                unitRepository.findByIdAndCompanyId(
                        unitId,
                        companyId
                )
        ).thenReturn(Optional.of(unit));
    
        when(
                unitRepository.existsByCompanyIdAndNameAndIdNot(
                        companyId,
                        request.getName(),
                        unitId
                )
        ).thenReturn(true);
    
        assertThatThrownBy(
                () -> service.update(
                        unitId,
                        request
                )
        ).isInstanceOf(
                UnitNameConflictException.class
        );
    }
    @Test
    void getByCompany_shouldUseOperatorQuery() {
    
        UUID userId = UUID.randomUUID();
    
        when(securityUtils.getCurrentCompanyId())
                .thenReturn(companyId);
    
        when(securityUtils.getCurrentRole())
                .thenReturn("OPERATOR");
    
        when(securityUtils.getCurrentUserId())
                .thenReturn(userId);
    
        when(
                unitRepository.findAllByCompanyIdAndUserId(
                        companyId,
                        userId
                )
        ).thenReturn(List.of(unit));
    
        List<UnitResponse> response =
                service.getByCompany();
    
        assertThat(response).hasSize(1);
    
        verify(unitRepository)
                .findAllByCompanyIdAndUserId(
                        companyId,
                        userId
                );
    }

    @Test
    void getByCompany_shouldUseCompanyQuery() {

        when(securityUtils.getCurrentCompanyId())
                .thenReturn(companyId);

        when(securityUtils.getCurrentRole())
                .thenReturn("ADMIN");

        when(
                unitRepository.findAllByCompanyId(companyId)
        ).thenReturn(List.of(unit));

        List<UnitResponse> response =
                service.getByCompany();

        assertThat(response).hasSize(1);

        verify(unitRepository)
                .findAllByCompanyId(companyId);
    }

    @Test
    void assignWorker_shouldAssignWorker() {

        UUID workerId = UUID.randomUUID();

        AssignRequest request =
                new AssignRequest();

        request.setWorkerId(workerId);
        request.setUserId(UUID.randomUUID());

        when(securityUtils.getCurrentCompanyId())
                .thenReturn(companyId);

        when(
                unitRepository.findByIdAndCompanyId(
                        unitId,
                        companyId
                )
        ).thenReturn(Optional.of(unit));

        when(
                workerRepository.findWorkersIdByUnitIdAndCompanyId(
                        unitId,
                        companyId
                )
        ).thenReturn(new HashSet<>());

        service.assignWorker(
                unitId,
                request
        );

        verify(workerRepository)
                .save(any(UnitWorker.class));
    }

    @Test
    void assignWorker_shouldNotDuplicateWorker() {

        UUID workerId = UUID.randomUUID();

        AssignRequest request =
                new AssignRequest();

        request.setWorkerId(workerId);
        request.setUserId(UUID.randomUUID());

        when(securityUtils.getCurrentCompanyId())
                .thenReturn(companyId);

        when(
                unitRepository.findByIdAndCompanyId(
                        unitId,
                        companyId
                )
        ).thenReturn(Optional.of(unit));

        when(
                workerRepository.findWorkersIdByUnitIdAndCompanyId(
                        unitId,
                        companyId
                )
        ).thenReturn(
                new HashSet<>(Set.of(workerId))
        );

        service.assignWorker(
                unitId,
                request
        );

        verify(workerRepository, never())
                .save(any());
    }
    @Test
    void delete_shouldDeleteUnit() {
    
        when(securityUtils.getCurrentCompanyId())
                .thenReturn(companyId);
    
        when(
                unitRepository.findByIdAndCompanyId(
                        unitId,
                        companyId
                )
        ).thenReturn(Optional.of(unit));
    
        service.delete(unitId);
    
        verify(spaceUnits)
                .deleteWithRollBack(
                        orgId.toString()
                );
    
        verify(unitRepository)
                .delete(unit);
    }
    @Test
    void delete_shouldThrowUnitNotFound() {
    
        when(securityUtils.getCurrentCompanyId())
                .thenReturn(companyId);
    
        when(
                unitRepository.findByIdAndCompanyId(
                        unitId,
                        companyId
                )
        ).thenReturn(Optional.empty());
    
        assertThatThrownBy(
                () -> service.delete(unitId)
        ).isInstanceOf(
                UnitNotFoundException.class
        );
    }
    @Test
    void createSeed_shouldThrowConflictWhenNameExists() {
    
        when(
            unitRepository.existsByCompanyIdAndName(
                companyId,
                request.getName()
            )
        ).thenReturn(true);
    
        assertThatThrownBy(
            () -> service.createSeed(
                request,
                orgId,
                companyId
            )
        ).isInstanceOf(
            UnitNameConflictException.class
        );
    
        verify(unitRepository, never())
            .save(any());
    
        verify(spaceUnits, never())
            .addWithRollBack(any());
    }
    @Test
    void createSeed_shouldCreateUnit() {
    
        when(
            unitRepository.existsByCompanyIdAndName(
                companyId,
                request.getName()
            )
        ).thenReturn(false);
    
        when(
            unitRepository.save(any(OperationalUnit.class))
        ).thenAnswer(invocation -> {
    
            OperationalUnit unit =
                invocation.getArgument(0);
    
            unit.setId(unitId);
    
            return unit;
        });
    
        UnitResponse response =
            service.createSeed(
                request,
                orgId,
                companyId
            );
    
        assertThat(response).isNotNull();
    
        verify(spaceUnits)
            .addWithRollBack(
                orgId.toString()
            );
    
        verify(unitRepository)
            .save(any(OperationalUnit.class));
    }
    @Test
    void createSeed_shouldTranslateDataIntegrityViolationException() {

        when(
            unitRepository.existsByCompanyIdAndName(
                companyId,
                request.getName()
            )
        ).thenReturn(false);

        when(
            unitRepository.save(any())
        ).thenThrow(
            new DataIntegrityViolationException("constraint")
        );

        assertThatThrownBy(
            () -> service.createSeed(
                request,
                orgId,
                companyId
            )
        ).isInstanceOf(
            UnitNameConflictException.class
        );
    }
    @Test
    void assignWorkerSeed_shouldAssignWorker() {
    
        UUID workerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
    
        AssignRequest request =
            new AssignRequest();
    
        request.setCompanyId(companyId);
        request.setWorkerId(workerId);
        request.setUserId(userId);
    
        when(
            unitRepository.findByIdAndCompanyId(
                unitId,
                companyId
            )
        ).thenReturn(
            Optional.of(unit)
        );
    
        UnitWorker savedWorker =
            new UnitWorker();
    
        when(
            workerRepository.save(any(UnitWorker.class))
        ).thenReturn(savedWorker);
    
        UnitWorker result =
            service.assignWorkerSeed(
                unitId,
                request
            );
    
        assertThat(result)
            .isSameAs(savedWorker);
    
        verify(workerRepository)
            .save(any(UnitWorker.class));
    }
    @Test
    void assignWorkerSeed_shouldThrowUnitNotFound() {
    
        AssignRequest request =
            new AssignRequest();
    
        request.setCompanyId(companyId);
    
        when(
            unitRepository.findByIdAndCompanyId(
                unitId,
                companyId
            )
        ).thenReturn(Optional.empty());
    
        assertThatThrownBy(
            () -> service.assignWorkerSeed(
                unitId,
                request
            )
        ).isInstanceOf(
            UnitNotFoundException.class
        );
    
        verify(workerRepository, never())
            .save(any());
    }
    
}
