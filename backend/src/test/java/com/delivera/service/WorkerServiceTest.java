package com.delivera.service;

import com.delivera.auth.service.AuthClient;
import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.dto.worker.ChangeRoleRequest;
import com.delivera.dto.worker.WorkerInviteRequest;
import com.delivera.dto.worker.WorkerResponse;
import com.delivera.exception.ForbiddenException;
import com.delivera.exception.LastAdminException;
import com.delivera.exception.LoyalUserCannotBeWorkerException;
import com.delivera.exception.WorkerAlreadyExistsException;
import com.delivera.exception.WorkerNotFoundException;
import com.delivera.model.*;
import com.delivera.repository.*;

import reactor.core.publisher.Mono;

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
class WorkerServiceTest {

    @Mock private WorkerRepository workerRepository;
    @Mock private UserRepository userRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private LoyalUserRepository loyalUserRepository;
    @Mock private SecurityUtils securityUtils;
    @Mock private SubscriptionService subscriptionService;
    @Mock private AuthClient client;
    @InjectMocks private WorkerService workerService;

    private UUID companyId;
    private Company company;
    private User user;
    private Worker worker;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        company = new Company();

        user = new User();
        user.setEmail("worker@test.com");
        user.setFirstName("Ana");
        user.setLastName("García");

        worker = new Worker();
        worker.setUser(user);
        worker.setCompany(company);
        worker.setRole(WorkerRole.OPERATOR);

        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
    }

    @Test
    void invite_existingUser_createsWorkerWithoutTempPassword() {
        when(userRepository.findByEmail("worker@test.com")).thenReturn(Optional.of(user));
        when(workerRepository.findByUserEmailAndCompanyId("worker@test.com", companyId)).thenReturn(Optional.empty());
        when(loyalUserRepository.findByEmail("worker@test.com")).thenReturn(List.of());
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company));
        when(workerRepository.save(any())).thenReturn(worker);

        WorkerResponse response = workerService.invite(new WorkerInviteRequest("worker@test.com", WorkerRole.OPERATOR));

        assertThat(response.email()).isEqualTo("worker@test.com");
        assertThat(response.tempPassword()).isNull();
        verify(userRepository, never()).save(any());
    }

    @Test
    void invite_newUser_createsUserAndReturnsTempPassword() {
        when(client.register(any(), any(), any(), any()))
        .thenReturn(Mono.empty());
        when(userRepository.findByEmail("new@test.com")).thenReturn(Optional.empty());
        when(workerRepository.findByUserEmailAndCompanyId("new@test.com", companyId)).thenReturn(Optional.empty());
        when(loyalUserRepository.findByEmail("new@test.com")).thenReturn(List.of());
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company));

        User newUser = new User();
        newUser.setEmail("new@test.com");
        newUser.setFirstName("new");
        newUser.setLastName("");

        when(userRepository.save(any())).thenReturn(newUser);

        Worker savedWorker = new Worker();
        savedWorker.setUser(newUser);
        savedWorker.setCompany(company);
        savedWorker.setRole(WorkerRole.ANALYST);

        when(workerRepository.save(any())).thenReturn(savedWorker);

        WorkerResponse response = workerService.invite(new WorkerInviteRequest("new@test.com", WorkerRole.ANALYST));

        assertThat(response.tempPassword()).isNotNull();
        verify(userRepository).save(any(User.class));
    }

    @Test
    void invite_alreadyWorker_throwsException() {
        when(workerRepository.findByUserEmailAndCompanyId("worker@test.com", companyId)).thenReturn(Optional.of(worker));

        assertThatThrownBy(() -> workerService.invite(new WorkerInviteRequest("worker@test.com", WorkerRole.OPERATOR)))
                .isInstanceOf(WorkerAlreadyExistsException.class);
    }

    @Test
    void changeRole_success() {
        UUID workerId = UUID.randomUUID();
        when(workerRepository.findByIdAndCompanyId(workerId, companyId)).thenReturn(Optional.of(worker));
        when(workerRepository.save(any())).thenReturn(worker);

        WorkerResponse response = workerService.changeRole(workerId, new ChangeRoleRequest(WorkerRole.ANALYST));

        assertThat(response).isNotNull();
        verify(workerRepository).save(worker);
    }

    @Test
    void changeRole_lastAdmin_throwsException() {
        worker.setRole(WorkerRole.COMPANY_ADMIN);
        UUID workerId = UUID.randomUUID();
        when(workerRepository.findByIdAndCompanyId(workerId, companyId)).thenReturn(Optional.of(worker));
        when(workerRepository.countByCompanyIdAndRole(companyId, WorkerRole.COMPANY_ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> workerService.changeRole(workerId, new ChangeRoleRequest(WorkerRole.ANALYST)))
                .isInstanceOf(LastAdminException.class);
    }

    @Test
    void remove_success() {
        UUID workerId = UUID.randomUUID();
        when(workerRepository.findByIdAndCompanyId(workerId, companyId)).thenReturn(Optional.of(worker));

        workerService.remove(workerId);

        verify(workerRepository).delete(worker);
    }

    @Test
    void remove_lastAdmin_throwsException() {
        worker.setRole(WorkerRole.COMPANY_ADMIN);
        UUID workerId = UUID.randomUUID();
        when(workerRepository.findByIdAndCompanyId(workerId, companyId)).thenReturn(Optional.of(worker));
        when(workerRepository.countByCompanyIdAndRole(companyId, WorkerRole.COMPANY_ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> workerService.remove(workerId))
                .isInstanceOf(LastAdminException.class);
    }

    @Test
    void remove_notFound_throwsException() {
        UUID workerId = UUID.randomUUID();
        when(workerRepository.findByIdAndCompanyId(workerId, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workerService.remove(workerId))
                .isInstanceOf(WorkerNotFoundException.class);
    }

    @Test
    void invite_existingLoyalUser_throws() {
        when(workerRepository.findByUserEmailAndCompanyId("worker@test.com", companyId)).thenReturn(Optional.empty());
        when(loyalUserRepository.findByEmail("worker@test.com")).thenReturn(List.of(new LoyalUser()));
        assertThatThrownBy(() -> workerService.invite(new WorkerInviteRequest("worker@test.com", WorkerRole.OPERATOR)))
                .isInstanceOf(LoyalUserCannotBeWorkerException.class);
    }

    @Test
    void getByCompany_returnsMappedList() {
        when(workerRepository.findByCompanyIdOrderByCreatedAtAsc(companyId)).thenReturn(List.of(worker));
        assertThat(workerService.getByCompany()).hasSize(1);
    }

    @Test
    void remove_self_throws() {
        UUID workerId = UUID.randomUUID();
        when(workerRepository.findByIdAndCompanyId(workerId, companyId)).thenReturn(Optional.of(worker));
        when(securityUtils.getCurrentEmail()).thenReturn("worker@test.com");
        assertThatThrownBy(() -> workerService.remove(workerId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void remove_invitedUserWithNoOtherWorkers_deletesUser() {
        UUID workerId = UUID.randomUUID();
        user.setId(UUID.randomUUID());
        user.setInvited(true);
        when(client.deleteUser(any()))
        .thenReturn(Mono.empty());
        when(workerRepository.findByIdAndCompanyId(workerId, companyId)).thenReturn(Optional.of(worker));
        when(workerRepository.countByUser_Id(user.getId())).thenReturn(0L);
        workerService.remove(workerId);
        verify(userRepository).delete(user);
    }
}
