package com.delivera.auth.service;

import com.delivera.dto.auth.ClaimRegisterRequest;
import com.delivera.dto.auth.CompanyRegisterRequest;
import com.delivera.dto.auth.CompanyRegisterResponse;
import com.delivera.dto.auth.LoginResponse;
import com.delivera.dto.auth.RegisterRequest;
import com.delivera.dto.auth.RegisterResponse;
import com.delivera.exception.*;
import com.delivera.model.*;
import com.delivera.repository.*;
import com.delivera.service.JwtService;

import org.springframework.util.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;


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
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthClient client;

    public AuthService(UserRepository userRepository,
                       OrganizationRepository organizationRepository,
                       CompanyRepository companyRepository,
                       WorkerRepository workerRepository,
                       OrderRepository orderRepository,
                       LoyalUserRepository loyalUserRepository,
                       ActivityTypeRepository activityTypeRepository,
                       SubscriptionPlanRepository subscriptionPlanRepository,
                       PasswordEncoder passwordEncoder,
                       AuthClient client,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.companyRepository = companyRepository;
        this.workerRepository = workerRepository;
        this.orderRepository = orderRepository;
        this.loyalUserRepository = loyalUserRepository;
        this.activityTypeRepository = activityTypeRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.passwordEncoder = passwordEncoder;
        this.client = client;
        this.jwtService = jwtService;
    }

    public LoginResponse login(String identifier, String password) {
        User user = userRepository.findByEmailOrUsername(identifier)
                .filter(u -> passwordEncoder.matches(password, u.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);

        List<Worker> workers = workerRepository.findByUserEmailOrderByCreatedAtAsc(user.getEmail());
        if (!workers.isEmpty()) {
            Worker worker = workers.get(0);
            Organization org = worker.getCompany().getOrganization();
            String token = jwtService.generateToken(user.getEmail(), worker.getCompany().getId(), worker.getRole());
            return new LoginResponse(token, user.getEmail(), worker.getCompany().getId(),
                    worker.getRole().name(), worker.getCompany().getName(), org.getHandle(), org.getName());
        }

        boolean isLoyal = !loyalUserRepository.findByEmail(user.getEmail()).isEmpty();
        String token = jwtService.generateToken(user.getEmail(), isLoyal ? LOYAL_USER_ROLE : null);
        return new LoginResponse(token, user.getEmail(), null, isLoyal ? LOYAL_USER_ROLE : null, null, null, null);
    }

    public LoginResponse switchCompany(String email, UUID targetCompanyId) {
        Worker worker = workerRepository.findByUserEmailAndCompanyId(email, targetCompanyId)
                .orElseThrow(InvalidCredentialsException::new);
        Organization org = worker.getCompany().getOrganization();
        String token = jwtService.generateToken(email, targetCompanyId, worker.getRole());
        return new LoginResponse(token, email, targetCompanyId, worker.getRole().name(),
                worker.getCompany().getName(), org.getHandle(), org.getName());
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new EmailAlreadyExistsException();
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new UsernameAlreadyExistsException();
        }
        User user = buildUser(request.email(), request.username(), request.firstName(), request.lastName(), request.phone(), request.password());
        userRepository.save(user);
        List<LoyalUser> loyalUsers = loyalUserRepository.findByEmail(user.getEmail());
        loyalUsers.forEach(lu -> {
            lu.setUser(user);
            loyalUserRepository.save(lu);
        });
        String role = loyalUsers.isEmpty() ? null : LOYAL_USER_ROLE;
        String token = jwtService.generateToken(user.getEmail(), role);
        return new RegisterResponse(token, user.getEmail(), role);
    }

    public boolean isHandleAvailable(String handle) {
        return !organizationRepository.existsByHandle(handle);
    }

    public boolean isUsernameAvailable(String username) {
        return !userRepository.existsByUsername(username);
    }

    @Transactional
    public CompanyRegisterResponse registerCompany(CompanyRegisterRequest request) {
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

        User user = buildUser(request.email(), request.username(), request.firstName(), request.lastName(), request.phone(), request.password());
        userRepository.save(user);

        Organization organization = new Organization();
        organization.setName(request.orgName());
        organization.setHandle(request.orgHandle());
        organizationRepository.saveAndFlush(organization);

        Company company = new Company();
        company.setOrganization(organization);
        company.setName(request.companyName());
        ActivityType activityType = activityTypeRepository.getReferenceById(request.activityType());
        company.setActivityType(activityType);
        company.setPlan(subscriptionPlanRepository.getReferenceById("FREE"));
        companyRepository.save(company);

        Worker worker = new Worker();
        worker.setUser(user);
        worker.setCompany(company);
        worker.setRole(WorkerRole.COMPANY_ADMIN);
        workerRepository.save(worker);

        String token = jwtService.generateToken(user.getEmail(), company.getId(), WorkerRole.COMPANY_ADMIN);
        return new CompanyRegisterResponse(token, user.getEmail(), company.getId(),
                WorkerRole.COMPANY_ADMIN.name(), company.getName(), organization.getHandle(), organization.getName());
    }

    @Transactional
    public LoginResponse claimRegister(String token, ClaimRegisterRequest request) {
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

        User user = buildUser(email, null, request.firstName(), request.lastName(), null, request.password());
        userRepository.save(user);

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

        String jwtToken = jwtService.generateToken(user.getEmail(), LOYAL_USER_ROLE);
        return new LoginResponse(jwtToken, user.getEmail(), null, LOYAL_USER_ROLE, null, null, null);
    }

    private User buildUser(String email, String username, String firstName, String lastName, String phone, String password) {
        User user = new User();
        user.setEmail(email);
        if (StringUtils.hasText(username)) user.setUsername(username);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setPhone(StringUtils.hasText(phone) ? phone : null);
        user.setPasswordHash(passwordEncoder.encode(password));
        return user;
    }
}
