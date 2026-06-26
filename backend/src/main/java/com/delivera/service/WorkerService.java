package com.delivera.service;

import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.dto.worker.ChangeRoleRequest;
import com.delivera.dto.worker.WorkerInviteRequest;
import com.delivera.dto.worker.WorkerResponse;
import com.delivera.exception.*;
import com.delivera.model.*;
import com.delivera.repository.*;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class WorkerService {

    private final WorkerRepository workerRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final LoyalUserRepository loyalUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityUtils securityUtils;
    private final SubscriptionService subscriptionService;

    public WorkerService(WorkerRepository workerRepository,
                         UserRepository userRepository,
                         CompanyRepository companyRepository,
                         LoyalUserRepository loyalUserRepository,
                         PasswordEncoder passwordEncoder,
                         SecurityUtils securityUtils,
                         SubscriptionService subscriptionService) {
        this.workerRepository = workerRepository;
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.loyalUserRepository = loyalUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.securityUtils = securityUtils;
        this.subscriptionService = subscriptionService;
    }

    @Transactional(readOnly = true)
    public List<WorkerResponse> getByCompany() {
        UUID companyId = securityUtils.getCurrentCompanyId();
        return workerRepository.findByCompanyIdOrderByCreatedAtAsc(companyId).stream()
                .map(WorkerResponse::from)
                .toList();
    }

    @Transactional
    public WorkerResponse invite(WorkerInviteRequest req) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        subscriptionService.checkWorkerLimit(companyId);

        String email = req.email().toLowerCase().trim();
        WorkerRole role = req.role();

        if (workerRepository.findByUserEmailAndCompanyId(email, companyId).isPresent()) {
            throw new WorkerAlreadyExistsException();
        }

        if (!loyalUserRepository.findByEmail(email).isEmpty()) {
            throw new LoyalUserCannotBeWorkerException();
        }

        Company company = companyRepository.findById(companyId).orElseThrow(CompanyContextException::new);

        String tempPassword = null;
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            tempPassword = UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "A1";
            user = new User();
            user.setEmail(email);
            user.setFirstName(email.split("@")[0]);
            user.setLastName("");
            user.setPasswordHash(passwordEncoder.encode(tempPassword));
            user.setInvited(true);
            userRepository.save(user);
        }

        Worker worker = new Worker();
        worker.setUser(user);
        worker.setCompany(company);
        worker.setRole(role);
        worker = workerRepository.save(worker);

        return tempPassword != null ? WorkerResponse.withTemp(worker, tempPassword) : WorkerResponse.from(worker);
    }

    @Transactional
    public WorkerResponse changeRole(UUID workerId, ChangeRoleRequest req) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        Worker worker = workerRepository.findByIdAndCompanyId(workerId, companyId)
                .orElseThrow(WorkerNotFoundException::new);

        WorkerRole newRole = req.role();

        if (worker.getRole() == WorkerRole.COMPANY_ADMIN && newRole != WorkerRole.COMPANY_ADMIN
                && workerRepository.countByCompanyIdAndRole(companyId, WorkerRole.COMPANY_ADMIN) <= 1) {
            throw new LastAdminException();
        }

        worker.setRole(newRole);
        return WorkerResponse.from(workerRepository.save(worker));
    }

    @Transactional
    public void remove(UUID workerId) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        Worker worker = workerRepository.findByIdAndCompanyId(workerId, companyId)
                .orElseThrow(WorkerNotFoundException::new);

        if (worker.getUser().getEmail().equalsIgnoreCase(securityUtils.getCurrentEmail())) {
            throw new ForbiddenException("CANNOT_REMOVE_SELF");
        }

        if (worker.getRole() == WorkerRole.COMPANY_ADMIN
                && workerRepository.countByCompanyIdAndRole(companyId, WorkerRole.COMPANY_ADMIN) <= 1) {
            throw new LastAdminException();
        }

        User user = worker.getUser();
        workerRepository.delete(worker);
        if (user.isInvited() && workerRepository.countByUser_Id(user.getId()) == 0) {
            userRepository.delete(user);
        }
    }
}
