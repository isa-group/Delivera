package com.delivera.auth.service;


import com.delivera.auth.dto.DeliveraOrgContext;
import com.delivera.auth.dto.RefreshCookieData;
import com.delivera.auth.dto.RequestClientData;
import com.delivera.dto.auth.ClaimRegisterRequest;
import com.delivera.dto.auth.CompanyRegisterRequest;
import com.delivera.dto.auth.CompanyRegisterResponse;
import com.delivera.dto.auth.LoginResponse;
import com.delivera.dto.auth.RegisterRequest;
import com.delivera.dto.auth.RegisterResponse;
import com.delivera.exception.*;
import com.delivera.model.*;
import com.delivera.order.model.Order;
import com.delivera.order.repository.OrderRepository;
import com.delivera.repository.*;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;



@Service
public class AuthService {

    private static final String LOYAL_USER_ROLE = "LOYAL_USER";

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final CompanyRepository companyRepository;
    private final WorkerRepository workerRepository;
    private final OrderRepository orderRepository;
    private final LoyalUserRepository loyalUserRepository;
    private final ActivityTypeRepository activityTypeRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final AuthClient client;

    
    @Value("${app.gateway.enabled}")
    private Boolean activeGateway;

    public AuthService(UserRepository userRepository,
                       OrganizationRepository organizationRepository,
                       CompanyRepository companyRepository,
                       WorkerRepository workerRepository,
                       OrderRepository orderRepository,
                       LoyalUserRepository loyalUserRepository,
                       ActivityTypeRepository activityTypeRepository,
                       SubscriptionPlanRepository subscriptionPlanRepository,
                       PasswordEncoder passwordEncoder,
                       AuthClient client) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.companyRepository = companyRepository;
        this.workerRepository = workerRepository;
        this.orderRepository = orderRepository;
        this.loyalUserRepository = loyalUserRepository;
        this.activityTypeRepository = activityTypeRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.client = client;
    }

    public String getIp(HttpServletRequest httpRequest) {
        String ip = httpRequest.getHeader("X-Forwarded-For");
        if (ip == null || activeGateway) {
            ip = httpRequest.getRemoteAddr();
        }
        return ip;
    }

    public String getUserAgent(HttpServletRequest httpRequest) {
        String userAgent = httpRequest.getHeader("User-Agent");

        if (userAgent == null) {
            userAgent = "unknown-agent";
        }

        return userAgent.length() > 1000
            ? userAgent.substring(0, 1000)
            : userAgent;

    }

    public String getDeviceId(HttpServletRequest httpRequest) {
        String device = httpRequest.getHeader("X-Device-Id");
        if (device == null) {
            device = "unknown";
        }
        return device;
    }

    public ResponseCookie refreshCookie(RefreshCookieData refreshCookieData) {
        return ResponseCookie.from("refresh_token", refreshCookieData.token())
            .httpOnly(true)
            .secure(refreshCookieData.secureRefreshCookie()) 
            .path(refreshCookieData.pathRefreshCookie())
            .maxAge(Duration.ofDays(refreshCookieData.daysToRefresh()))
            .sameSite("Lax")
            .domain(refreshCookieData.domainRefreshCookie())
            .build();
    }
    

   

    @Transactional
    public RegisterResponse register(RegisterRequest request, RequestClientData requestClientData) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new EmailAlreadyExistsException();
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new UsernameAlreadyExistsException();
        }
        User user = buildUser(request.email(), request.username(), request.firstName(), request.lastName(), request.phone());
        User savedUser = userRepository.save(user);
       
        List<LoyalUser> loyalUsers = loyalUserRepository.findByEmail(user.getEmail());
        loyalUsers.forEach(lu -> {
            lu.setUser(user);
            loyalUserRepository.save(lu);
        });
        String role = loyalUsers.isEmpty() ? null : LOYAL_USER_ROLE;
        LoginResponse loginResponse = client.register(
            savedUser.getId(), 
            request.email(),
            request.username(), 
            request.password(), 
            new DeliveraOrgContext(null, null, role, null, null),
            requestClientData
        ).block();
        return new RegisterResponse(loginResponse.getToken(), user.getEmail(), role, loginResponse.getRefreshCookie());
    }

    public boolean isHandleAvailable(String handle) {
        return !organizationRepository.existsByHandle(handle);
    }

    public boolean isUsernameAvailable(String username) {
        return !userRepository.existsByUsername(username);
    }

    @Transactional
    public CompanyRegisterResponse registerCompany(CompanyRegisterRequest request, RequestClientData requestClientData) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new EmailAlreadyExistsException();
        }
        if (organizationRepository.existsByHandle(request.orgHandle())) {
            throw new HandleConflictException(request.orgHandle(), null);
        }

        if (request.username() != null && !request.username().isBlank()
                && userRepository.existsByUsername(request.username())) {
            throw new UsernameAlreadyExistsException();
        }

        User user = buildUser(request.email(), request.username(), request.firstName(), request.lastName(), request.phone());
        User savedUser = userRepository.save(user);


        Organization organization = new Organization();
        organization.setName(request.orgName());
        organization.setHandle(request.orgHandle());
        Organization savedOrganization  = organizationRepository.saveAndFlush(organization);

        Company company = new Company();
        company.setOrganization(organization);
        company.setName(request.companyName());
        ActivityType activityType = activityTypeRepository.getReferenceById(request.activityType());
        company.setActivityType(activityType);
        company.setPlan(subscriptionPlanRepository.getReferenceById("FREE"));
        Company savedCompany = companyRepository.save(company);

        Worker worker = new Worker();
        worker.setUser(user);
        worker.setCompany(company);
        worker.setRole(WorkerRole.COMPANY_ADMIN);
        workerRepository.save(worker);

        LoginResponse response = client.register(
            savedUser.getId(), request.email(), request.username(), request.password(), 
            new DeliveraOrgContext(
                savedCompany.getId(), WorkerRole.COMPANY_ADMIN, savedCompany.getName(),
                savedOrganization.getHandle(),savedOrganization.getName() ),
            requestClientData
        ).block();

        return new CompanyRegisterResponse(response.getToken(), user.getEmail(), company.getId(),
                WorkerRole.COMPANY_ADMIN.name(), company.getName(), organization.getHandle(), organization.getName(),
                response.getRefreshCookie()
        );
    }

    @Transactional
    public LoginResponse claimRegister(String token, ClaimRegisterRequest request, RequestClientData requestClientData) {
        Order order = orderRepository.findByTrackingToken(token)
                .orElseThrow(OrderNotFoundException::new);

        if (order.getLoyalUser() != null && order.getLoyalUser().getUser() != null) {
            throw new OrderAlreadyClaimedException();
        }

        String email = request.email().toLowerCase().trim();
        if (!email.equals(order.getRecipientEmail())) {
            throw new OrderClaimEmailMismatchException();
        }

        if (userRepository.findByEmail(email).isPresent()) {
            throw new EmailAlreadyExistsException();
        }

        User user = buildUser(email, null, request.firstName(), request.lastName(), null);

        User savedUser = userRepository.save(user);

          

        LoyalUser loyalUser = loyalUserRepository
                .findByCompaniesIdAndEmail(order.getCompany().getId(), email)
                .orElseGet(() -> {
                    LoyalUser lu = new LoyalUser();
                    lu.getCompanies().add(order.getCompany());
                    lu.setEmail(email);
                    return lu;
                });
        loyalUser.setUser(user);
        loyalUserRepository.save(loyalUser);

        order.setLoyalUser(loyalUser);
        orderRepository.save(order);

        return client.register(
            savedUser.getId(), email, null, request.password(), 
            new DeliveraOrgContext(
                null, null, 
                LOYAL_USER_ROLE, null, null
            ),
            requestClientData
        ).block(); 

    }

    private User buildUser(String email, String username, String firstName, String lastName, String phone) {
        User user = new User();
        user.setEmail(email);
        if (StringUtils.hasText(username)) user.setUsername(username);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setPhone(StringUtils.hasText(phone) ? phone : null);
        return user;
    }



}
