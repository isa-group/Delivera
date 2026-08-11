package com.delivera.auth.service;


import com.delivera.auth.dto.ClaimData;
import com.delivera.auth.dto.DeliveraOrgContext;
import com.delivera.auth.dto.RefreshCookieData;
import com.delivera.auth.dto.RequestClientData;
import com.delivera.client.transaction.annotation.Compensable;
import com.delivera.client.transaction.compensation.Compensations;
import com.delivera.dto.auth.ClaimRegisterRequest;
import com.delivera.dto.auth.CompanyRegisterRequest;
import com.delivera.dto.auth.CompanyRegisterResponse;
import com.delivera.dto.auth.LoginResponse;
import com.delivera.dto.auth.RegisterRequest;
import com.delivera.dto.auth.RegisterResponse;
import com.delivera.exception.*;
import com.delivera.model.*;
import com.delivera.org.dto.CompanySettingsDTO;
import com.delivera.org.model.Company;
import com.delivera.org.model.Organization;
import com.delivera.org.repository.CompanyRepository;
import com.delivera.org.repository.OrganizationRepository;
import com.delivera.org.service.SettingsClient;
import com.delivera.repository.*;
import com.delivera.space.service.SpaceContracts;
import com.delivera.worker.model.Worker;
import com.delivera.worker.model.WorkerRole;
import com.delivera.worker.repository.WorkerRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.Duration;
import java.util.List;



@Service
@RequiredArgsConstructor
public class AuthService {

    private static final WorkerRole LOYAL_USER_ROLE = WorkerRole.LOYAL_USER;

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final CompanyRepository companyRepository;
    private final WorkerRepository workerRepository;
    private final LoyalUserRepository loyalUserRepository;
    private final ActivityTypeRepository activityTypeRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final AuthClient authClient;
    private final SpaceContracts spaceContracts;
    private final SettingsClient settingsClient;


    
    @Value("${app.gateway.enabled}")
    private Boolean activeGateway;


    public String getIp(HttpServletRequest httpRequest) {
        String ip = httpRequest.getHeader("X-Forwarded-For");
        if (ip == null || activeGateway) {
            ip = httpRequest.getRemoteAddr();
        }
        ip = ip.split(",")[0].trim();
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
        if (StringUtils.hasText(request.address())) user.setAddress(request.address());
        user.setLatitude(request.latitude());
        user.setLongitude(request.longitude());
        User savedUser = userRepository.save(user);
       
        List<LoyalUser> loyalUsers = loyalUserRepository.findByEmail(user.getEmail());
        loyalUsers.forEach(lu -> {
            lu.setUser(user);
            loyalUserRepository.save(lu);
        });
        WorkerRole role = LOYAL_USER_ROLE;
        LoginResponse loginResponse = authClient.register(
            savedUser.getId(), 
            request.email(),
            request.username(), 
            request.password(), 
            new DeliveraOrgContext(null, role, null , null, null,null),
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

    @Compensable
    @Transactional
    //@SpaceTransaction
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

        spaceContracts.createBasicContract(user, organization);
        Compensations.registerRollback(() -> {
            spaceContracts.removeContract(savedOrganization.getId().toString());
        });

        LoginResponse response = authClient.register(
            savedUser.getId(), request.email(), request.username(), request.password(), 
            new DeliveraOrgContext(
                savedCompany.getId(), WorkerRole.COMPANY_ADMIN, savedCompany.getName(),
                savedOrganization.getHandle(),savedOrganization.getName(),savedOrganization.getId() ),
            requestClientData
        ).block();

        settingsClient.createSettings(new CompanySettingsDTO(savedCompany.getId(),null, false));

        return new CompanyRegisterResponse(response.getToken(), user.getEmail(), company.getId(),
                WorkerRole.COMPANY_ADMIN.name(), company.getName(), organization.getHandle(), organization.getName(),
                response.getRefreshCookie()
        );
    }

    @Transactional
    public LoginResponse claimRegister(ClaimData claimData) {
        ClaimRegisterRequest request = claimData.getRequest();
        String email = request.email().toLowerCase().trim();
        RequestClientData requestClientData = claimData.getClientData();


        User user = buildUser(email, request.username(), request.firstName(), request.lastName(), null);

        if (claimData.getAddress() != null) user.setAddress(claimData.getAddress());
        User savedUser = userRepository.save(user);

        Company company = companyRepository.findById(claimData.getCompanyId())
        .orElseThrow(() -> new ForbiddenException("COMPANY NOT FOUND EXCEPTION"));
          

        LoyalUser loyalUser = loyalUserRepository
                .findByCompanyIdAndEmail(claimData.getCompanyId(), email)
                .orElseGet(() -> {
                    LoyalUser lu = loyalUserRepository.findByEmail(email).stream().findFirst()
                            .orElseGet(() -> {
                                LoyalUser fresh = new LoyalUser();
                                fresh.setEmail(email);
                                return fresh;
                            });
                    lu.linkFor(company);
                    return lu;
                });
        loyalUser.setUser(user);
        LoyalUser lu = loyalUserRepository.save(loyalUser);

        LoginResponse loginResponse =  authClient.register(
            savedUser.getId(), email, null, request.password(), 
            new DeliveraOrgContext(
                null, LOYAL_USER_ROLE,null
                , null, null,null
            ),
            requestClientData
        ).block();
        loginResponse.setLoyalUserId(lu.getId());

        return loginResponse;


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
