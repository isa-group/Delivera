package com.delivera.order.service;

import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.depot.model.OperationalUnit;
import com.delivera.depot.repository.OperationalUnitRepository;
import com.delivera.exception.*;
import com.delivera.model.*;
import com.delivera.order.dto.DataOrderRequest;
import com.delivera.order.dto.OrderDetailResponse;
import com.delivera.order.dto.OrderLocationRequest;
import com.delivera.order.dto.OrderRequest;
import com.delivera.order.dto.OrderResponse;
import com.delivera.order.dto.OrderStatusRequest;
import com.delivera.order.dto.PublicOrderResponse;
import com.delivera.order.model.Order;
import com.delivera.order.model.OrderEvent;
import com.delivera.order.model.OrderPriority;
import com.delivera.order.model.OrderStatus;
import com.delivera.order.model.OrderType;
import com.delivera.order.repository.OrderRepository;
import com.delivera.org.model.Company;
import com.delivera.org.repository.CompanyRepository;
import com.delivera.repository.*;
import com.delivera.service.AppConfigService;
import com.delivera.service.EmailService;
import com.delivera.service.SubscriptionService;
import com.delivera.worker.repository.WorkerRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OperationalUnitRepository unitRepository;
    private final CompanyRepository companyRepository;
    private final LoyalUserRepository loyalUserRepository;
    private final WorkerRepository workerRepository;
    private final SecurityUtils securityUtils;
    private final AppConfigService appConfigService;
    private final SubscriptionService subscriptionService;
    private final EmailService emailService;
    private final String trackingUrlBase;
    private final OrderClient orderClient;

    public OrderService(OrderRepository orderRepository,
                        OperationalUnitRepository unitRepository,
                        CompanyRepository companyRepository,
                        LoyalUserRepository loyalUserRepository,
                        WorkerRepository workerRepository,
                        SecurityUtils securityUtils,
                        AppConfigService appConfigService,
                        SubscriptionService subscriptionService,
                        OrderClient orderClient,
                        EmailService emailService,
                        @Value("${app.tracking-url-base:https://delivera.app/track/}") String trackingUrlBase) {
        this.orderRepository = orderRepository;
        this.unitRepository = unitRepository;
        this.companyRepository = companyRepository;
        this.loyalUserRepository = loyalUserRepository;
        this.workerRepository = workerRepository;
        this.securityUtils = securityUtils;
        this.appConfigService = appConfigService;
        this.subscriptionService = subscriptionService;
        this.emailService = emailService;
        this.orderClient = orderClient;
        this.trackingUrlBase = trackingUrlBase;
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
    public OrderResponse createB2C(OrderRequest request) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        subscriptionService.checkOrderLimit(companyId);

        OrderType orderType = request.orderType();

        Company company = companyRepository.findById(companyId)
                .orElseThrow(CompanyContextException::new);
        if (orderType != OrderType.B2C) {
            throw new ForbiddenException("YOU CAN'T DO THIS OPERATION");
        }
            
       
        if (request.recipientEmail() == null || request.recipientEmail().isBlank()) {
            throw new MissingClientEmailException();
        }
        String recipientEmail = request.recipientEmail().toLowerCase().trim();
        String recipientName = request.recipientName() != null ? request.recipientName().trim() : null;
    
        if (!workerRepository.findByUserEmailOrderByCreatedAtAsc(recipientEmail).isEmpty()) {
            throw new WorkerCannotBeLoyalUserException();
        }
        LoyalUser loyalUser = loyalUserRepository.findByEmail(recipientEmail).stream().findFirst()
                .orElseGet(() -> {
                    LoyalUser lu = new LoyalUser();
                    lu.setEmail(recipientEmail);
                    return lu;
                });
        LoyalUserCompany link = loyalUser.linkFor(company);
        if (link.getName() == null && recipientName != null) link.setName(recipientName);
        String reqAddr = request.recipientAddress() != null && !request.recipientAddress().isBlank()
                ? request.recipientAddress().trim() : null;
        if (link.getAddress() == null && reqAddr != null) link.setAddress(reqAddr);
        loyalUser = loyalUserRepository.save(loyalUser);

        RecipientCoords coords = resolveRecipientAddress(
            request, 
            loyalUser.findLink(companyId)
            .orElse(null)
        );

        DataOrderRequest finalRequest = buildFinalRequest(
            recipientEmail,
            recipientName,
            request,
            coords,
            loyalUser.getId(),
            loyalUser.getUser() != null
        );
        


        return orderClient.executeB2CRequest(finalRequest);
    }

    private DataOrderRequest buildFinalRequest(
        String recipientEmail, 
        String recipientName,
        OrderRequest request,
        RecipientCoords coords,
        UUID loyalUserId,
        Boolean claimed
    ){
        DataOrderRequest finalRequest = new DataOrderRequest();
        finalRequest.setOriginId(request.originId());
        finalRequest.setDestinationId(request.destinationId());
        finalRequest.setRecipientEmail(recipientEmail);
        finalRequest.setRecipientName(recipientName);
        finalRequest.setRecipientAddress(coords.addr());
        finalRequest.setRecipientLatitude(coords.lat());
        finalRequest.setRecipientLongitude(coords.lon());
        finalRequest.setOrderType(request.orderType());
        finalRequest.setPriority(request.priority());
        finalRequest.setNotes(request.notes());
        finalRequest.setLoyalUserId(loyalUserId);
        finalRequest.setClaimed(claimed);
        return finalRequest;
    }


    @Transactional
    public OrderDetailResponse updateStatus(UUID id, OrderStatusRequest request) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        String email = securityUtils.getCurrentEmail();

        Order order = orderRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(OrderNotFoundException::new);

        validateTransition(order.getStatus(), request.status());

        order.setStatus(request.status());

        OrderEvent event = new OrderEvent();
        event.setOrder(order);
        event.setStatus(request.status());
        event.setNote(request.note() != null ? request.note().trim() : null);
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

    private void validateTransition(OrderStatus current, OrderStatus next) {
        appConfigService.validateTransition(current.name(), next.name());
    }

    private record RecipientCoords(String addr, BigDecimal lat, BigDecimal lon) {}

    private RecipientCoords resolveRecipientAddress(OrderRequest request, LoyalUserCompany matchedLink) {
        String addr = request.recipientAddress() != null && !request.recipientAddress().isBlank()
                ? request.recipientAddress().trim() : null;
        BigDecimal lat = request.recipientLatitude();
        BigDecimal lon = request.recipientLongitude();
        if (addr != null && (lat == null || lon == null)) throw new MissingRecipientAddressException();
        if (addr == null && matchedLink != null) {
            RecipientCoords c = resolveFromLink(matchedLink);
            addr = c.addr();
            lat = c.lat();
            lon = c.lon();
        }
        if (addr == null || lat == null || lon == null) throw new MissingRecipientAddressException();
        return new RecipientCoords(addr,lat,lon);
    }

    private RecipientCoords resolveFromLink(LoyalUserCompany link) {
        String addr = link.getAddress();
        BigDecimal lat = link.getLatitude();
        BigDecimal lon = link.getLongitude();
        LoyalUser lu = link.getLoyalUser();
        if ((addr == null || lat == null) && lu != null && lu.getUser() != null) {
            if (addr == null) addr = lu.getUser().getAddress();
            if (lat == null) lat = lu.getUser().getLatitude();
            if (lon == null) lon = lu.getUser().getLongitude();
        }
        if (lat == null || lon == null) return new RecipientCoords(null, null, null);
        return new RecipientCoords(addr, lat, lon);
    }

    private String generateReference() {
        long seq = orderRepository.nextReferenceSeq();
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return String.format("DEL-%s-%04d", date, seq);
    }
}
