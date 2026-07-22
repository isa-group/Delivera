package com.delivera.service;

import com.delivera.auth.dto.RequestClientData;
import com.delivera.auth.service.AuthClient;
import com.delivera.auth.service.AuthService;
import com.delivera.dto.auth.*;
import com.delivera.exception.*;
import java.math.BigDecimal;
import com.delivera.model.*;
import com.delivera.order.model.Order;
import com.delivera.order.repository.OrderRepository;
import com.delivera.org.model.Company;
import com.delivera.org.model.Organization;
import com.delivera.org.repository.CompanyRepository;
import com.delivera.org.repository.OrganizationRepository;
import com.delivera.repository.*;
import com.delivera.worker.model.Worker;
import com.delivera.worker.model.WorkerRole;
import com.delivera.worker.repository.WorkerRepository;

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
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private WorkerRepository workerRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private LoyalUserRepository loyalUserRepository;
    @Mock
    private ActivityTypeRepository activityTypeRepository;
    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;
    @Mock
    private AuthClient client;
    @InjectMocks
    private AuthService authService;

    private User user;
    private Company company;
    private Organization organization;
    private Worker worker;
    private Order claimOrder;
    private ClaimRegisterRequest claimRequest;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setEmail("admin@test.com");

        organization = new Organization();
        organization.setId(UUID.randomUUID());
        organization.setName("TestOrg");
        organization.setHandle("test-org");

        company = new Company();
        company.setId(UUID.randomUUID());
        company.setName("TestCompany");
        company.setOrganization(organization);

        worker = new Worker();
        worker.setUser(user);
        worker.setCompany(company);
        worker.setRole(WorkerRole.COMPANY_ADMIN);

        claimOrder = new Order();
        claimOrder.setTrackingToken("testtoken");
        claimOrder.setRecipientEmail("juan@gmail.com");
        claimOrder.setCompany(company);

        claimRequest = new ClaimRegisterRequest("Juan", "García", "juan@gmail.com", "juangarcia", "Password1");
    }

  
    // --- register ---

    @Test
    void register_success() {
        RegisterRequest req = new RegisterRequest("new@test.com", "newuser", "John", null, null, "Password1", "Calle Mayor 1, Madrid", new BigDecimal("40.4168"), new BigDecimal("-3.7038"));
        when(userRepository.findByEmail("new@test.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(loyalUserRepository.findByEmail("new@test.com")).thenReturn(List.of());
        when(client.register(any(), any(), any(), any(),any(),any()))
        .thenReturn(Mono.just(new LoginResponse(
            "token", 
            "new@test.com", null, null, null, null, null,null,null) ));
     

        RegisterResponse result = authService.register(req, new RequestClientData("device", "userAgent", "ip"));
        assertThat(result.getToken()).isEqualTo("token");
        assertThat(result.getEmail()).isEqualTo("new@test.com");
        assertThat(result.getRole()).isNull();
    }

    // --- registerCompany ---

    @Test
    void registerCompany_success() {
        CompanyRegisterRequest req = new CompanyRegisterRequest(
                "ceo@test.com", "Password1", "TestOrg", "test-org",
                "TestCompany", "TRANSPORT", "ceouser", "CEO", null, null);
        when(userRepository.findByEmail("ceo@test.com")).thenReturn(Optional.empty());
        when(organizationRepository.existsByHandle("test-org")).thenReturn(false);
        when(userRepository.existsByUsername("ceouser")).thenReturn(false);
        when(userRepository.save(any())).thenReturn(user);
        
        when(client.register(any(), any(), any(), any(),any(),any()))
            .thenReturn(Mono.just(new LoginResponse(
                "company-token", 
                null, null, null, null, null, null,null,null) ));

        when(organizationRepository.saveAndFlush(any())).thenReturn(organization);
        ActivityType transport = new ActivityType(); transport.setCode("TRANSPORT");
        when(activityTypeRepository.getReferenceById("TRANSPORT")).thenReturn(transport);
        when(companyRepository.save(any())).thenReturn(company);
        when(workerRepository.save(any())).thenReturn(worker);
       

        CompanyRegisterResponse result = authService.registerCompany(req, new RequestClientData("device", "userAgent", "ip"));
        assertThat(result.token()).isEqualTo("company-token");
    }

    // --- claimRegister ---

    /* TODO
    @Test
    void claimRegister_success_noExistingLoyalUser() {
        when(orderRepository.findByTrackingToken("testtoken")).thenReturn(Optional.of(claimOrder));
        when(userRepository.findByEmail("juan@gmail.com")).thenReturn(Optional.empty());
        when(loyalUserRepository.findByCompanyIdAndEmail(company.getId(), "juan@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(loyalUserRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(client.register(any(), any(), any(), any(),any(),any()))
        .thenReturn(Mono.just(new LoginResponse(
            "jwt-token", 
            "juan@gmail.com", null, null, null, null, null,null,null) 
        ));
     

        LoginResponse result = authService.claimRegister("testtoken", claimRequest, new RequestClientData("device", "userAgent", "ip"));

        assertThat(result.getToken()).isEqualTo("jwt-token");
        assertThat(result.getEmail()).isEqualTo("juan@gmail.com");
        assertThat(result.getCompanyId()).isNull();
        verify(loyalUserRepository).save(any(LoyalUser.class));
        verify(orderRepository).save(claimOrder);
    }

    TODO
    @Test
    void claimRegister_tokenNotFound_throws() {
        when(orderRepository.findByTrackingToken("badtoken")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.claimRegister("badtoken", claimRequest, new RequestClientData("device", "userAgent", "ip")))
                .isInstanceOf(OrderNotFoundException.class);
    } */

    @Test
    void isHandleAvailable_and_isUsernameAvailable() {
        when(organizationRepository.existsByHandle("free")).thenReturn(false);
        when(userRepository.existsByUsername("free")).thenReturn(false);
        assertThat(authService.isHandleAvailable("free")).isTrue();
        assertThat(authService.isUsernameAvailable("free")).isTrue();
    }

    @Test
    void register_emailExists_throws() {
        RegisterRequest req = new RegisterRequest("dup@test.com", "u", "A", null, null, "Password1", "Calle Mayor 1, Madrid", new BigDecimal("40.4168"), new BigDecimal("-3.7038"));
        when(userRepository.findByEmail("dup@test.com")).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> authService.register(req, new RequestClientData("device", "userAgent", "ip")))
        .isInstanceOf(EmailAlreadyExistsException.class);
    }

    @Test
    void register_usernameExists_throws() {
        RegisterRequest req = new RegisterRequest("new@test.com", "taken", "A", null, null, "Password1", "Calle Mayor 1, Madrid", new BigDecimal("40.4168"), new BigDecimal("-3.7038"));
        when(userRepository.findByEmail("new@test.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("taken")).thenReturn(true);
        assertThatThrownBy(() -> authService.register(req, new RequestClientData("device", "userAgent", "ip"))).isInstanceOf(UsernameAlreadyExistsException.class);
    }

    @Test
    void registerCompany_handleConflict_throws() {
        CompanyRegisterRequest req = new CompanyRegisterRequest("c@t.com", "Password1", "Org", "taken", "C", "TR", null, "A", null, null);
        when(userRepository.findByEmail("c@t.com")).thenReturn(Optional.empty());
        when(organizationRepository.existsByHandle("taken")).thenReturn(true);
        assertThatThrownBy(() -> authService.registerCompany(req, new RequestClientData("device", "userAgent", "ip"))).isInstanceOf(HandleConflictException.class);
    }
    /* TODO
    @Test
    void claimRegister_alreadyClaimed_throws() {
        LoyalUser lu = new LoyalUser();
        lu.setUser(user);
        claimOrder.setLoyalUser(lu);
        when(orderRepository.findByTrackingToken("testtoken")).thenReturn(Optional.of(claimOrder));
        assertThatThrownBy(() -> authService.claimRegister("testtoken", claimRequest, new RequestClientData("device", "userAgent", "ip")))
                .isInstanceOf(OrderAlreadyClaimedException.class);
    }

    @Test
    void claimRegister_emailMismatch_throws() {
        when(orderRepository.findByTrackingToken("testtoken")).thenReturn(Optional.of(claimOrder));
        ClaimRegisterRequest req = new ClaimRegisterRequest("A", "B", "other@gmail.com", "", "Password1");
        assertThatThrownBy(() -> authService.claimRegister("testtoken", req, new RequestClientData("device", "userAgent", "ip")))
                .isInstanceOf(OrderClaimEmailMismatchException.class);
    }*/
}
