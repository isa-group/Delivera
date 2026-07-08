package com.delivera.data.worker.service;



import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.client.exception.CompanyContextException;
import com.delivera.data.dto.common.User;
import com.delivera.data.exception.ForbiddenException;
import com.delivera.data.exception.LastAdminException;
import com.delivera.data.exception.LoyalUserCannotBeWorkerException;
import com.delivera.data.exception.WorkerAlreadyExistsException;
import com.delivera.data.exception.WorkerNotFoundException;
import com.delivera.data.worker.dto.ChangeRoleRequest;
import com.delivera.data.worker.dto.WorkerInviteRequest;
import com.delivera.data.worker.dto.WorkerResponse;
import com.delivera.data.worker.model.Worker;
import com.delivera.data.worker.model.WorkerRole;
import com.delivera.data.worker.repository.WorkerRepository;

import java.util.List;
import java.util.UUID;

@Service
public class WorkerService {

    private final WorkerRepository workerRepository;
    //private final UserRepository userRepository;
    //private final CompanyRepository companyRepository;
    //private final LoyalUserRepository loyalUserRepository;
    private final SecurityUtils securityUtils;
    //private final SubscriptionService subscriptionService;
    //private final AuthClient client;

    public WorkerService(WorkerRepository workerRepository,
                         //UserRepository userRepository,
                         //CompanyRepository companyRepository,
                         //LoyalUserRepository loyalUserRepository,
                         SecurityUtils securityUtils
                         //AuthClient client,
                         //SubscriptionService subscriptionService
        ) {
        this.workerRepository = workerRepository;
        //this.userRepository = userRepository;
        //this.companyRepository = companyRepository;
        //this.loyalUserRepository = loyalUserRepository;
        this.securityUtils = securityUtils;
        //this.subscriptionService = subscriptionService;
        //this.client = client;
    }

    @Transactional(readOnly = true)
    public List<WorkerResponse> getByCompany() {
        UUID companyId = securityUtils.getCurrentCompanyId();
        return workerRepository.findByCompanyIdAndRoleNotOrderByCreatedAtAsc(companyId, WorkerRole.GLOBAL_ADMIN).stream()
                .map(WorkerResponse::from)
                .toList();
    }

    @Transactional
    public WorkerResponse invite(WorkerInviteRequest req) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        //subscriptionService.checkWorkerLimit(companyId);

        String email = req.email().toLowerCase().trim();
        WorkerRole role = req.role();

        if (workerRepository.findByUserEmailAndCompanyId(email, companyId).isPresent()) {
            throw new WorkerAlreadyExistsException();
        }
        /* TODO:
        if (!loyalUserRepository.findByEmail(email).isEmpty()) {
            throw new LoyalUserCannotBeWorkerException();
        }*/

        // TODO: Company company = companyRepository.findById(companyId).orElseThrow(CompanyContextException::new);

        String tempPassword = null;
        User savedUser = null;
        // TODO: ESTO LE CORRESPONDE AL ORG-SERVICE ??
        //TODO: User user = userRepository.findByEmail(email).orElse(null);
        /* 
        User user = new User();
        if (user == null) {
            tempPassword = UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "A1";
            user = new User();
            user.setEmail(email);
            user.setFirstName(email.split("@")[0]); <---- 
            user.setLastName(""); <---- POR ESTAS TRES COSAS NECESITAMOS LLAMAR AL ORG-SERVICE
            user.setInvited(true); <--- 
            // TODO: Request--> savedUser = userRepository.save(user);
         
        }
        */

        Worker worker = new Worker();
        worker.setUserId(UUID.randomUUID());
        // TODO: worker.setCompany(company);
        worker.setRole(role);
        worker = workerRepository.save(worker);
        if (savedUser != null) {
            // TODO: client.register(savedUser.getId(), email, null, tempPassword).block();
        }
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

        // TODO: CHECKS : if (worker.getUser().getEmail().equalsIgnoreCase(securityUtils.getCurrentEmail())) {
        if (worker.getUserId().equals(securityUtils.getCurrentUserId())) {
            throw new ForbiddenException("CANNOT_REMOVE_SELF");
        }

        if (worker.getRole() == WorkerRole.COMPANY_ADMIN
                && workerRepository.countByCompanyIdAndRole(companyId, WorkerRole.COMPANY_ADMIN) <= 1) {
            throw new LastAdminException();
        }

        UUID userId = worker.getUserId();
        workerRepository.delete(worker);
       //TODO:
        if (
            //user.isInvited() && 
            workerRepository.countByUser_Id(userId) == 0) {
            //userRepository.delete(user);
            //client.deleteUser(user.getId()).block();
            
        }
    }
}
