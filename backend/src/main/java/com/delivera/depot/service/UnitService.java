package com.delivera.depot.service;

import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.depot.dto.B2BUnitResponse;
import com.delivera.depot.dto.UnitDetailResponse;
import com.delivera.depot.dto.UnitRequest;
import com.delivera.depot.dto.UnitResponse;
import com.delivera.depot.model.OperationalUnit;
import com.delivera.depot.repository.OperationalUnitRepository;
import com.delivera.dto.settings.CompanySummary;
import com.delivera.exception.CompanyContextException;
import com.delivera.exception.UnitNameConflictException;
import com.delivera.exception.UnitNotFoundException;
import com.delivera.exception.WorkerNotFoundException;
import com.delivera.model.Company;
import com.delivera.model.Worker;
import com.delivera.model.WorkerRole;
import com.delivera.repository.CompanyRepository;
import com.delivera.repository.WorkerRepository;
import com.delivera.service.SubscriptionService;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UnitService {

    private final OperationalUnitRepository unitRepository;
    private final CompanyRepository companyRepository;
    private final WorkerRepository workerRepository;
    private final SecurityUtils securityUtils;
    private final SubscriptionService subscriptionService;

    public UnitService(OperationalUnitRepository unitRepository,
                       CompanyRepository companyRepository,
                       WorkerRepository workerRepository,
                       SecurityUtils securityUtils,
                       SubscriptionService subscriptionService) {
        this.unitRepository = unitRepository;
        this.companyRepository = companyRepository;
        this.workerRepository = workerRepository;
        this.securityUtils = securityUtils;
        this.subscriptionService = subscriptionService;
    }

    @Transactional
    public UnitResponse create(UnitRequest request) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        subscriptionService.checkUnitLimit(companyId);
        if (unitRepository.existsByCompanyIdAndName(companyId, request.name())) {
            throw new UnitNameConflictException();
        }
        Company company = companyRepository.findById(companyId)
                .orElseThrow(CompanyContextException::new);
        OperationalUnit unit = new OperationalUnit();
        unit.setCompany(company);
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
    }

    @Transactional(readOnly = true)
    public List<B2BUnitResponse> getExternalUnits() {
        return unitRepository.findAllExternalUnits(securityUtils.getCurrentCompanyId()).stream()
                .map(B2BUnitResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CompanySummary> getExternalCompanies() {
        UUID companyId = securityUtils.getCurrentCompanyId();
        Company company = companyRepository.findById(companyId).orElseThrow(CompanyContextException::new);
        return companyRepository.findByOrganizationId(company.getOrganization().getId())
                .stream()
                .filter(c -> !c.getId().equals(companyId))
                .map(c -> new CompanySummary(c.getId(), c.getName(), c.getActivityType().getCode(), c.getLogoData(), c.getDefaultPriority(), c.isDefaultPriorityLocked()))
                .toList();
    }

    @Transactional(readOnly = true)
    public UnitDetailResponse getDetail(UUID id) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        return UnitDetailResponse.from(unitRepository.findByIdAndCompanyIdWithWorkers(id, companyId)
                .orElseThrow(() -> new UnitNotFoundException(id)));
    }

    @Transactional
    public UnitDetailResponse assignWorker(UUID unitId, UUID workerId) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        OperationalUnit unit = unitRepository.findByIdAndCompanyIdWithWorkers(unitId, companyId)
                .orElseThrow(() -> new UnitNotFoundException(unitId));
        Worker worker = workerRepository.findByIdAndCompanyId(workerId, companyId)
                .orElseThrow(WorkerNotFoundException::new);
        unit.getWorkers().add(worker);
        return UnitDetailResponse.from(unitRepository.save(unit));
    }

    @Transactional
    public UnitDetailResponse unassignWorker(UUID unitId, UUID workerId) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        OperationalUnit unit = unitRepository.findByIdAndCompanyIdWithWorkers(unitId, companyId)
                .orElseThrow(() -> new UnitNotFoundException(unitId));
        unit.getWorkers().removeIf(w -> w.getId().equals(workerId));
        return UnitDetailResponse.from(unitRepository.save(unit));
    }

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
}
