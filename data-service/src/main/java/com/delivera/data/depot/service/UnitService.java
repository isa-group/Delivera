package com.delivera.data.depot.service;

import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.data.depot.dto.B2BUnitResponse;
import com.delivera.data.depot.dto.UnitDetailResponse;
import com.delivera.data.depot.dto.UnitRequest;
import com.delivera.data.depot.dto.UnitResponse;
import com.delivera.data.depot.model.OperationalUnit;
import com.delivera.data.depot.model.UnitWorker;
import com.delivera.data.depot.model.WorkerRole;
import com.delivera.data.depot.repository.OperationalUnitRepository;
import com.delivera.data.depot.repository.WorkerRepository;
import com.delivera.data.exception.CompanyContextException;
import com.delivera.data.exception.UnitNameConflictException;
import com.delivera.data.exception.UnitNotFoundException;
import com.delivera.data.exception.WorkerNotFoundException;
import com.delivera.data.org.dto.OrgCheckRequest;
import com.delivera.data.org.service.OrgClient;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UnitService {

    private final OperationalUnitRepository unitRepository;
    //private final CompanyRepository companyRepository;
    private final OrgClient orgClient;
    private final WorkerRepository workerRepository;
    private final SecurityUtils securityUtils;
    //private final SubscriptionService subscriptionService;

    public UnitService(OperationalUnitRepository unitRepository,
                       WorkerRepository workerRepository,
                       OrgClient orgClient,
                       SecurityUtils securityUtils
                       //SubscriptionService subscriptionService
                       ) {
        this.unitRepository = unitRepository;
        this.orgClient = orgClient;
       // this.companyRepository = companyRepository;
        this.workerRepository = workerRepository;
        this.securityUtils = securityUtils;
       // this.subscriptionService = subscriptionService;
    }

    @Transactional
    public UnitResponse create(UnitRequest request) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        UUID orgId = securityUtils.getCurrentOrgId();
       //TODO: subscriptionService.checkUnitLimit(companyId);
        if (unitRepository.existsByCompanyIdAndName(companyId, request.name())) {
            throw new UnitNameConflictException();
        }

        Boolean orgCheck = orgClient.checkOrgData(new OrgCheckRequest(companyId, orgId)).block();
        if (!orgCheck) {
                throw new CompanyContextException();
        }
        OperationalUnit unit = new OperationalUnit();
        unit.setCompanyId(companyId);
        applyRequest(unit, request);
        try {
            return UnitResponse.from(unitRepository.save(unit));
        } catch (DataIntegrityViolationException e) {
            throw new UnitNameConflictException();
        }
    }

    @Transactional
    public UnitResponse update(UUID unitId, UnitRequest request) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        OperationalUnit unit = unitRepository.findByIdAndCompanyId(unitId, companyId)
                .orElseThrow(() -> new UnitNotFoundException(unitId));
        if (unitRepository.existsByCompanyIdAndNameAndIdNot(companyId, request.name(), unitId)) {
            throw new UnitNameConflictException();
        }
        applyRequest(unit, request);
        try {
            return UnitResponse.from(unitRepository.save(unit));
        } catch (DataIntegrityViolationException e) {
            throw new UnitNameConflictException();
        }
    }

   /*
    @Transactional(readOnly = true)
    public List<UnitResponse> getByCompany() {
        UUID companyId = securityUtils.getCurrentCompanyId();
        String role = securityUtils.getCurrentRole();
        if (WorkerRole.OPERATOR.name().equals(role)) {
            String email = securityUtils.getCurrentEmail();
            Worker worker = workerRepository.findByUserEmailAndCompanyId(email, companyId).orElse(null);
            if (worker == null) return List.of();
            return unitRepository.findAllByCompanyIdAndWorkersContaining(companyId, worker).stream()
                    .map(UnitResponse::from).toList();
        }
        return unitRepository.findAllByCompanyId(companyId).stream()
                .map(UnitResponse::from).toList();
    } */


    @Transactional(readOnly = true)
    public List<UnitResponse> getByCompany() {
        UUID companyId = securityUtils.getCurrentCompanyId();
        String role = securityUtils.getCurrentRole();
        if (WorkerRole.OPERATOR.name().equals(role)) {
            UUID userId = securityUtils.getCurrentUserId();
            return unitRepository.findAllByCompanyIdAndUserId(companyId, userId).stream()
                    .map(UnitResponse::from).toList();
        }
        return unitRepository.findAllByCompanyId(companyId).stream()
                .map(UnitResponse::from).toList();
    } 
            

    /* TODO: MOVER AL ORG-SERVICE??
    @Transactional(readOnly = true)
    public List<B2BUnitResponse> getExternalUnits() {
        return unitRepository.findAllExternalUnits(securityUtils.getCurrentCompanyId()).stream()
                .map(B2BUnitResponse::from)
                .toList();
    }
    */
   
    @Transactional(readOnly = true)
    public List<B2BUnitResponse> getExternalUnits(UUID externalCompanyId) {
        return unitRepository.findAllExternalUnits(
            securityUtils.getCurrentCompanyId(),
            externalCompanyId
        );
    }
    /* TODO: Esto lo debería hacer el org-service
    @Transactional(readOnly = true)
    public List<CompanySummary> getExternalCompanies() {
        UUID companyId = securityUtils.getCurrentCompanyId();
        Company company = companyRepository.findById(companyId).orElseThrow(CompanyContextException::new);
        return companyRepository.findByOrganizationId(company.getOrganization().getId())
                .stream()
                .filter(c -> !c.getId().equals(companyId))
                .map(c -> new CompanySummary(c.getId(), c.getName(), c.getActivityType().getCode(), c.getLogoData(), c.getDefaultPriority(), c.isDefaultPriorityLocked()))
                .toList();
    }*/

    /* TODO: 
    @Transactional(readOnly = true)
    public UnitDetailResponse getDetail(UUID id) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        return UnitDetailResponse.from(unitRepository.findByIdAndCompanyIdWithWorkers(id, companyId)
                .orElseThrow(() -> new UnitNotFoundException(id)));
    }
    */

    @Transactional(readOnly = true)
    public UnitDetailResponse getDetail(UUID id) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        return UnitDetailResponse.from(unitRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new UnitNotFoundException(id)));
    }

    /* TODO: DELETE
    @Transactional
    public UnitDetailResponse assignWorker(UUID unitId, UUID workerId) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        OperationalUnit unit = unitRepository.findByIdAndCompanyIdWithWorkers(unitId, companyId)
                .orElseThrow(() -> new UnitNotFoundException(unitId));
        UnitWorker worker = workerRepository.findByIdAndCompanyId(workerId, companyId)
                .orElseThrow(WorkerNotFoundException::new);
        unit.getWorkers().add(worker);
        return UnitDetailResponse.from(unitRepository.save(unit));
    } 
    
    TODO: DELETE
    @Transactional
    public UnitDetailResponse unassignWorker(UUID unitId, UUID workerId) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        OperationalUnit unit = unitRepository.findByIdAndCompanyIdWithWorkers(unitId, companyId)
                .orElseThrow(() -> new UnitNotFoundException(unitId));
        unit.getWorkers().removeIf(w -> w.getId().equals(workerId));
        return UnitDetailResponse.from(unitRepository.save(unit));
    } */

    @Transactional
    public UnitWorker assignWorker(UUID unitId, UUID workerId, UUID userId) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        OperationalUnit unit = unitRepository.findByIdAndCompanyId(unitId, companyId)
                .orElseThrow(() -> new UnitNotFoundException(unitId));
        return workerRepository.save(buildUnitWorker(unit, workerId, userId));
    } 
        

    @Transactional
    public void unassignWorker(UUID unitId, UUID workerId) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        workerRepository.unassignWorker(workerId,unitId,companyId);
    }


    @Transactional(readOnly = true)
    public List<UUID> getWorkersIdByUnit(UUID unitId) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        return workerRepository.findWorkersIdByUnitIdAndCompanyId(unitId, companyId);
    }



    // TODO: MIRAR AUNQUE CREO QUE NO HACE FALTA TOCAR NADA.
    @Transactional
    public void delete(UUID id) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        OperationalUnit unit = unitRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new UnitNotFoundException(id));
        unitRepository.delete(unit);
    }

    private void applyRequest(OperationalUnit unit, UnitRequest request) {
        unit.setName(request.name());
        unit.setType(request.type());
        unit.setAddress(request.address());
        unit.setLatitude(request.latitude());
        unit.setLongitude(request.longitude());
        unit.setDefaultPriority(request.defaultPriority());
    }

    private UnitWorker buildUnitWorker(OperationalUnit unit, UUID workerId, UUID userId) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        UnitWorker unitWorker = new UnitWorker();
        unitWorker.setCompanyId(companyId);
        unitWorker.setUnit(unit);
        unitWorker.setWorkerId(workerId);
        unitWorker.setUserId(userId);
        return unitWorker;
    }
}
