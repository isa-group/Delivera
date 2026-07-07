package com.delivera.service;


import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.depot.dto.UnitRequest;
import com.delivera.depot.model.OperationalUnit;
import com.delivera.depot.model.UnitType;
import com.delivera.depot.repository.OperationalUnitRepository;
import com.delivera.depot.service.UnitService;
import com.delivera.exception.CompanyContextException;
import com.delivera.exception.UnitNameConflictException;
import com.delivera.exception.UnitNotFoundException;
import com.delivera.model.Company;
import com.delivera.model.Organization;
import com.delivera.repository.CompanyRepository;
import com.delivera.worker.model.Worker;
import com.delivera.worker.repository.WorkerRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UnitServiceTest {

    @Mock
    private OperationalUnitRepository unitRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private WorkerRepository workerRepository;
    @Mock
    private SecurityUtils securityUtils;
    @Mock
    private SubscriptionService subscriptionService;
    @InjectMocks
    private UnitService unitService;

    private UUID companyId;
    private Company company;
    private OperationalUnit unit;
    private UnitRequest request;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        company = new Company();
        company.setId(companyId);

        unit = new OperationalUnit();
        unit.setId(UUID.randomUUID());
        unit.setName("Warehouse A");
        unit.setType(UnitType.WAREHOUSE);
        unit.setCompany(company);

        request = new UnitRequest("Warehouse A", UnitType.WAREHOUSE, "1 Main St", null, null, null);
    }

    @Test
    void create_success() {
        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(unitRepository.existsByCompanyIdAndName(companyId, "Warehouse A")).thenReturn(false);
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company));
        when(unitRepository.save(any())).thenReturn(unit);

        assertThat(unitService.create(request)).isNotNull();
        verify(unitRepository).save(any());
    }

    @Test
    void create_nameDuplicate_throws() {
        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(unitRepository.existsByCompanyIdAndName(companyId, "Warehouse A")).thenReturn(true);

        assertThatThrownBy(() -> unitService.create(request))
                .isInstanceOf(UnitNameConflictException.class);
    }

    @Test
    void update_success() {
        UUID unitId = unit.getId();
        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(unitRepository.findByIdAndCompanyId(unitId, companyId)).thenReturn(Optional.of(unit));
        when(unitRepository.existsByCompanyIdAndNameAndIdNot(companyId, "Warehouse A", unitId)).thenReturn(false);
        when(unitRepository.save(unit)).thenReturn(unit);

        assertThat(unitService.update(unitId, request)).isNotNull();
    }

    @Test
    void delete_success() {
        UUID unitId = unit.getId();
        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(unitRepository.findByIdAndCompanyId(unitId, companyId)).thenReturn(Optional.of(unit));

        unitService.delete(unitId);
        verify(unitRepository).delete(unit);
    }

    @Test
    void getByCompany_operatorRole_filtersByWorker() {
        Worker worker = new Worker();
        worker.setId(UUID.randomUUID());
        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(securityUtils.getCurrentRole()).thenReturn("OPERATOR");
        when(securityUtils.getCurrentEmail()).thenReturn("op@test.com");
        when(workerRepository.findByUserEmailAndCompanyId("op@test.com", companyId)).thenReturn(Optional.of(worker));
        when(unitRepository.findAllByCompanyIdAndWorkersContaining(companyId, worker)).thenReturn(List.of(unit));

        assertThat(unitService.getByCompany()).hasSize(1);
    }

    @Test
    void getByCompany_nonOperator_returnsAll() {
        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(securityUtils.getCurrentRole()).thenReturn("COMPANY_ADMIN");
        when(unitRepository.findAllByCompanyId(companyId)).thenReturn(List.of(unit));

        assertThat(unitService.getByCompany()).hasSize(1);
    }

    @Test
    void assignAndUnassignWorker_success() {
        UUID workerId = UUID.randomUUID();
        com.delivera.model.User user = new com.delivera.model.User();
        user.setEmail("w@t.com");
        user.setFirstName("A");
        user.setLastName("B");
        Worker worker = new Worker();
        worker.setId(workerId);
        worker.setUser(user);
        worker.setRole(com.delivera.worker.model.WorkerRole.OPERATOR);
        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(unitRepository.findByIdAndCompanyIdWithWorkers(unit.getId(), companyId)).thenReturn(Optional.of(unit));
        when(workerRepository.findByIdAndCompanyId(workerId, companyId)).thenReturn(Optional.of(worker));
        when(unitRepository.save(unit)).thenReturn(unit);

        unitService.assignWorker(unit.getId(), workerId);
        unitService.unassignWorker(unit.getId(), workerId);
        verify(unitRepository, times(2)).save(unit);
    }

    @Test
    void update_nameConflict_throws() {
        UUID unitId = unit.getId();
        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(unitRepository.findByIdAndCompanyId(unitId, companyId)).thenReturn(Optional.of(unit));
        when(unitRepository.existsByCompanyIdAndNameAndIdNot(companyId, "Warehouse A", unitId)).thenReturn(true);
        assertThatThrownBy(() -> unitService.update(unitId, request))
                .isInstanceOf(UnitNameConflictException.class);
    }

    @Test
    void create_dataIntegrity_throws() {
        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(unitRepository.existsByCompanyIdAndName(companyId, "Warehouse A")).thenReturn(false);
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company));
        when(unitRepository.save(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("dup"));
        assertThatThrownBy(() -> unitService.create(request))
                .isInstanceOf(UnitNameConflictException.class);
    }

    @Test
    void getDetail_success() {
        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(unitRepository.findByIdAndCompanyIdWithWorkers(unit.getId(), companyId)).thenReturn(Optional.of(unit));
        assertThat(unitService.getDetail(unit.getId())).isNotNull();
    }

    @Test
    void getDetail_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(unitRepository.findByIdAndCompanyIdWithWorkers(id, companyId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> unitService.getDetail(id)).isInstanceOf(UnitNotFoundException.class);
    }

    @Test
    void getExternalUnits_mapsResults() {
        Organization org = new Organization();
        org.setId(UUID.randomUUID());
        org.setName("Org");
        company.setOrganization(org);
        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(unitRepository.findAllExternalUnits(companyId)).thenReturn(List.of(unit));
        assertThat(unitService.getExternalUnits()).hasSize(1);
    }

    @Test
    void getByCompany_operatorWithoutWorker_returnsEmpty() {
        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(securityUtils.getCurrentRole()).thenReturn("OPERATOR");
        when(securityUtils.getCurrentEmail()).thenReturn("op@test.com");
        when(workerRepository.findByUserEmailAndCompanyId("op@test.com", companyId)).thenReturn(Optional.empty());
        assertThat(unitService.getByCompany()).isEmpty();
    }

    @Test
    void getExternalCompanies_excludesSelf() {
        Organization org = new Organization();
        org.setId(UUID.randomUUID());
        company.setOrganization(org);
        Company other = new Company();
        other.setId(UUID.randomUUID());
        other.setName("Other");
        other.setActivityType(new com.delivera.model.ActivityType() {{ setCode("FOOD"); }});
        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company));
        when(companyRepository.findByOrganizationId(org.getId())).thenReturn(List.of(company, other));

        assertThat(unitService.getExternalCompanies()).hasSize(1);
    }

}
