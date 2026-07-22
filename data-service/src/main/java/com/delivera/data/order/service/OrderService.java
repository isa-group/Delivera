package com.delivera.data.order.service;

import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.data.depot.model.OperationalUnit;
import com.delivera.data.depot.repository.OperationalUnitRepository;
import com.delivera.data.depot.repository.WorkerRepository;
import com.delivera.data.email.service.EmailService;
import com.delivera.data.exception.InvalidOrderUnitsException;
import com.delivera.data.exception.MissingClientEmailException;
import com.delivera.data.exception.MissingRecipientAddressException;
import com.delivera.data.exception.OrderAlreadyClaimedException;
import com.delivera.data.exception.OrderClaimEmailMismatchException;
import com.delivera.data.exception.OrderNotFoundException;
import com.delivera.data.order.dto.ClaimData;
import com.delivera.data.order.dto.ClaimRegisterRequest;
import com.delivera.data.order.dto.ClaimResponse;
import com.delivera.data.order.dto.LoginResponse;
import com.delivera.data.order.dto.OrderDetailResponse;
import com.delivera.data.order.dto.OrderLocationRequest;
import com.delivera.data.order.dto.OrderRequest;
import com.delivera.data.order.dto.OrderResponse;
import com.delivera.data.order.dto.OrderStatusRequest;
import com.delivera.data.order.dto.PublicOrderResponse;
import com.delivera.data.order.dto.RequestClientData;
import com.delivera.data.order.model.Order;
import com.delivera.data.order.model.OrderEvent;
import com.delivera.data.order.model.OrderPriority;
import com.delivera.data.order.model.OrderStatus;
import com.delivera.data.order.model.OrderType;
import com.delivera.data.order.repository.OrderRepository;
import com.delivera.data.org.model.CompanySettings;
import com.delivera.data.org.service.OrgClient;
import com.delivera.data.org.service.SettingsService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OperationalUnitRepository unitRepository;
    // TODO:P002-Company private final CompanyRepository companyRepository;
    private final SettingsService settingsService;
    // TODO:P001-LoyalUser private final LoyalUserRepository loyalUserRepository;
    private final WorkerRepository workerRepository;
    private final SecurityUtils securityUtils;
    //TODO:P003-States private final AppConfigService appConfigService;
    // TODO:P008-SPACE private final SubscriptionService subscriptionService;
    private final EmailService emailService;
    private final String trackingUrlBase;
    private final OrgClient orgClient;

    public OrderService(OrderRepository orderRepository,
                        OperationalUnitRepository unitRepository,
                        //TODO:P002-Company CompanyRepository companyRepository,
                        SettingsService settingsService,
                        OrgClient orgClient,
                        //TODO:P001-LoyalUser LoyalUserRepository loyalUserRepository,
                        WorkerRepository workerRepository,
                        SecurityUtils securityUtils,
                        //TODO:P003-States AppConfigService appConfigService,
                        //TODO:P008-SPACE SubscriptionService subscriptionService,
                        EmailService emailService,
                        @Value("${app.tracking-url-base:https://delivera.app/track/}") String trackingUrlBase) {
        this.orderRepository = orderRepository;
        this.unitRepository = unitRepository;
        //TODO:P002-Company this.companyRepository = companyRepository;
        this.settingsService = settingsService;
        //TODO:P001-LoyalUser this.loyalUserRepository = loyalUserRepository;
        this.workerRepository = workerRepository;
        this.securityUtils = securityUtils;
        this.orgClient = orgClient;
        //TODO:P003-States this.appConfigService = appConfigService;
        //TODO:P008-SPACE this.subscriptionService = subscriptionService;
        this.emailService = emailService;
        this.trackingUrlBase = trackingUrlBase;
    }

    @Transactional(readOnly = true)
    public ClaimResponse validateClaim(String token, String email) {
        ClaimResponse response = new ClaimResponse();
        Optional<String> orderEmail = orderRepository.findNotClaimedByToken(token);
        if (orderEmail.isPresent()) {
            //MAYBE TIMING ATTACK --> IT HAS TO SPLIT NETWORK, SERVER LOAD, ...
            Boolean sameEmail = orderEmail.get().equalsIgnoreCase(email);

            response.setValid(sameEmail);
            response.setError(
                !sameEmail?
                    "INCORRECT_EMAIL":
                    null
            );  
            return response;
        } 
        response.setValid(false);
        response.setError(
            "ORDER_CLAIMED_OR_NOT_FOUND"
        );
        return response;
    }


    @Transactional(readOnly = true)
    public List<OrderResponse> getByCompany() {
        UUID companyId = securityUtils.getCurrentCompanyId();
        return orderRepository.findSentOrReceivedByCompanyId(companyId)
                .stream()
                .map(OrderResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getDetail(UUID id) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        Order order = orderRepository.findByIdForCompany(id, companyId)
                .orElseThrow(OrderNotFoundException::new);
        return OrderDetailResponse.from(order);
    }

    @Transactional
    public OrderResponse create(OrderRequest request) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        String email = securityUtils.getCurrentEmail();
        request.setReference(generateReference());
        request.setCreatedAt(null);
        request.setClaimed(false);
        return create(request, companyId, email);
    }

    @Transactional
    public OrderResponse createB2C(OrderRequest request) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        String email = securityUtils.getCurrentEmail();
        request.setReference(generateReference());
        request.setCreatedAt(null);
        System.out.println(request.getClaimed());
        return create(request, companyId, email);
    }

    @Transactional
    public OrderResponse create(OrderRequest request, UUID companyId, String email) {
        // TODO:P008-SPACE subscriptionService.checkOrderLimit(companyId);

        OrderType orderType = request.getOrderType();

        OperationalUnit origin = unitRepository.findByIdAndCompanyId(request.getOriginId(), companyId)
                .orElseThrow(InvalidOrderUnitsException::new);

        OperationalUnit destination = null;
        if (orderType == OrderType.INTERNAL) {
            destination = unitRepository.findByIdAndCompanyId(request.getDestinationId(), companyId)
                    .orElseThrow(InvalidOrderUnitsException::new);
            if (origin.getId().equals(destination.getId())) {
                throw new InvalidOrderUnitsException();
            }
        } else if (orderType == OrderType.B2B) {
            destination = unitRepository.findById(request.getDestinationId())
                    .orElseThrow(InvalidOrderUnitsException::new);
            if (destination.getCompanyId().equals(companyId)) {
                throw new InvalidOrderUnitsException();
            }
        }

        Order order = new Order();
        order.setCompanyId(companyId);
        order.setOrderType(orderType);
        order.setReference(request.getReference());
        order.setOrigin(origin);
        order.setDestination(destination);
        order.setStatus(OrderStatus.PENDING);
        order.setClaimed(request.getClaimed());
        order.setCreatedAt(request.getCreatedAt());

        order.setPriority(resolveDefaultPriority(request.getPriority(), origin));

        order.setNotes(request.getNotes() != null ? request.getNotes().trim() : null);

        if (orderType == OrderType.B2C) {
            // IN THIS CASE THE DATA COMES FROM DELIVERA
            if (request.getRecipientEmail() == null || request.getRecipientEmail().isBlank()) {
                throw new MissingClientEmailException();
            }

            if (request.getRecipientAddress() == null
                || request.getRecipientAddress().isBlank()
                || request.getRecipientLatitude() == null
                || request.getRecipientLongitude() == null) {
                throw new MissingRecipientAddressException();
            }

            String recipientEmail = request.getRecipientEmail().toLowerCase().trim();
            String recipientName = request.getRecipientName() != null ? request.getRecipientName().trim() : null;
            String token = UUID.randomUUID().toString().replace("-", "");
            order.setRecipientEmail(recipientEmail);
            order.setRecipientName(recipientName);
            order.setTrackingToken(token);
          
            
            order.setLoyalUserId(request.getLoyalUserId());
            order.setRecipientAddress(request.getRecipientAddress());
            order.setRecipientLatitude( request.getRecipientLatitude());
            order.setRecipientLongitude(request.getRecipientLongitude());
            // Send tracking link after commit to avoid eager flush
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        String url = trackingUrlBase + token;
                        emailService.sendTrackingLink(recipientEmail, recipientName, order.getReference(), url);
                    }
                });

            
        }

        OrderEvent initialEvent = new OrderEvent();
        initialEvent.setOrder(order);
        initialEvent.setStatus(OrderStatus.PENDING);
        initialEvent.setAuthorEmail(email);
        initialEvent.setCreatedAt(request.getCreatedAt());
        order.getEvents().add(initialEvent);

        return OrderResponse.from(orderRepository.save(order));
    }

    
    public OrderDetailResponse updateStatus(UUID id, OrderStatusRequest request) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        String email = securityUtils.getCurrentEmail();
        request.setEmail(email);
        request.setCompanyId(companyId);
        return updateStatusSeed(id, request);
    }

    @Transactional
    public OrderDetailResponse updateStatusSeed(UUID id, OrderStatusRequest request) {
        UUID companyId = request.getCompanyId();
        String email = request.getEmail();

        Order order = orderRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(OrderNotFoundException::new);

        validateTransition(order.getStatus(), request.getStatus());

        order.setStatus(request.getStatus());

        OrderEvent event = new OrderEvent();
        event.setOrder(order);
        event.setStatus(request.getStatus());
        event.setNote(request.getNote() != null ? request.getNote().trim() : null);
        event.setAuthorEmail(email);
        order.getEvents().add(event);

        return OrderDetailResponse.from(orderRepository.save(order));
    }

    @Transactional
    public OrderDetailResponse updateLocation(UUID id, OrderLocationRequest request) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        Order order = orderRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(OrderNotFoundException::new);
        order.setCurrentLat(request.lat());
        order.setCurrentLon(request.lon());
        order.setCurrentLocationAt(Instant.now());
        return OrderDetailResponse.from(orderRepository.save(order));
    }

    @Transactional
    public void delete(UUID id) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        Order order = orderRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(OrderNotFoundException::new);
        orderRepository.delete(order);
    }


    @Transactional(readOnly = true)
    public PublicOrderResponse getPublicByToken(String token) {
        Order order = orderRepository.findByTrackingToken(token)
                .orElseThrow(OrderNotFoundException::new);
        return PublicOrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public PublicOrderResponse getPublicByReference(String reference) {
        Order order = orderRepository.findByReference(reference.toUpperCase().trim())
                .orElseThrow(OrderNotFoundException::new);
        return PublicOrderResponse.from(order);
    }

    @Transactional
    public LoginResponse claimOrder(
        ClaimRegisterRequest request, 
        RequestClientData clientData, 
        String trackingToken
    ) {
       Order order = orderRepository.findByTrackingToken(trackingToken)
       .orElseThrow(OrderNotFoundException::new);

        if (order.getClaimed()) {
            throw new OrderAlreadyClaimedException();
        }
        String email = request.email().toLowerCase().trim();
        if (!order.getRecipientEmail().equalsIgnoreCase(email.trim())) {
            throw new OrderClaimEmailMismatchException();
        } 
        

        LoginResponse loginResponse = orgClient.claimRegister(
            new ClaimData(
                email, order.getCompanyId(), 
                order.getRecipientAddress(), 
                request, clientData
            )
        );
        order.setClaimed(true);
        order.setLoyalUserId(loginResponse.getLoyalUserId());
        orderRepository.save(order);
        loginResponse.setLoyalUserId(null);
        return loginResponse;
    }

    private void validateTransition(OrderStatus current, OrderStatus next) {
       /*TODO:P003-States
       appConfigService.validateTransition(current.name(), next.name()); 
       */
    }

   

    public OrderPriority resolveDefaultPriority(OrderPriority requested,
                                               OperationalUnit originUnit
                                               //Company company
                                            ) {
        CompanySettings settings = settingsService.get(originUnit.getCompanyId());
        // TODO:P002-Company
        if (requested != null) return requested;
        boolean locked = settings.isDefaultPriorityLocked();
        if (!locked && originUnit != null && originUnit.getDefaultPriority() != null) return originUnit.getDefaultPriority();
        if (settings.getDefaultPriority() != null) return settings.getDefaultPriority(); 
        return OrderPriority.NORMAL;
    }

    private String generateReference() {
        long seq = orderRepository.nextReferenceSeq();
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return String.format("DEL-%s-%04d", date, seq);
    }
}
