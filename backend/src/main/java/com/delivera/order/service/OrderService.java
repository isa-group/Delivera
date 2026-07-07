package com.delivera.order.service;

import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.depot.model.OperationalUnit;
import com.delivera.depot.repository.OperationalUnitRepository;
import com.delivera.exception.*;
import com.delivera.model.*;
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
import com.delivera.repository.*;
import com.delivera.service.AppConfigService;
import com.delivera.service.EmailService;
import com.delivera.service.SubscriptionService;
import com.delivera.worker.repository.WorkerRepository;

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
    private final CompanyRepository companyRepository;
    private final LoyalUserRepository loyalUserRepository;
    private final WorkerRepository workerRepository;
    private final SecurityUtils securityUtils;
    private final AppConfigService appConfigService;
    private final SubscriptionService subscriptionService;
    private final EmailService emailService;
    private final String trackingUrlBase;

    public OrderService(OrderRepository orderRepository,
                        OperationalUnitRepository unitRepository,
                        CompanyRepository companyRepository,
                        LoyalUserRepository loyalUserRepository,
                        WorkerRepository workerRepository,
                        SecurityUtils securityUtils,
                        AppConfigService appConfigService,
                        SubscriptionService subscriptionService,
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
    public OrderResponse create(OrderRequest request) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        subscriptionService.checkOrderLimit(companyId);

        OrderType orderType = request.orderType();

        Company company = companyRepository.findById(companyId)
                .orElseThrow(CompanyContextException::new);

        OperationalUnit origin = unitRepository.findByIdAndCompanyId(request.originId(), companyId)
                .orElseThrow(InvalidOrderUnitsException::new);

        OperationalUnit destination = null;
        if (orderType == OrderType.INTERNAL) {
            destination = unitRepository.findByIdAndCompanyId(request.destinationId(), companyId)
                    .orElseThrow(InvalidOrderUnitsException::new);
            if (origin.getId().equals(destination.getId())) {
                throw new InvalidOrderUnitsException();
            }
        } else if (orderType == OrderType.B2B) {
            destination = unitRepository.findById(request.destinationId())
                    .orElseThrow(InvalidOrderUnitsException::new);
            if (destination.getCompany().getId().equals(companyId)) {
                throw new InvalidOrderUnitsException();
            }
        }

        Order order = new Order();
        order.setCompany(company);
        order.setOrderType(orderType);
        order.setReference(generateReference());
        order.setOrigin(origin);
        order.setDestination(destination);
        order.setStatus(OrderStatus.PENDING);
        order.setPriority(resolveDefaultPriority(request.priority(), origin, company));
        order.setNotes(request.notes() != null ? request.notes().trim() : null);

        if (orderType == OrderType.B2C && request.recipientEmail() != null) {
            String recipientEmail = request.recipientEmail().toLowerCase().trim();
            String recipientName = request.recipientName() != null ? request.recipientName().trim() : null;
            String token = UUID.randomUUID().toString().replace("-", "");
            order.setRecipientEmail(recipientEmail);
            order.setRecipientName(recipientName);
            order.setTrackingToken(token);
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
            order.setLoyalUser(loyalUser);

            resolveRecipientAddress(order, request, loyalUser.findLink(companyId).orElse(null));
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
        initialEvent.setAuthorEmail(securityUtils.getCurrentEmail());
        order.getEvents().add(initialEvent);

        return OrderResponse.from(orderRepository.save(order));
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

    private void resolveRecipientAddress(Order order, OrderRequest request, LoyalUserCompany matchedLink) {
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
        if (addr == null || lat == null) throw new MissingRecipientAddressException();
        order.setRecipientAddress(addr);
        order.setRecipientLatitude(lat);
        order.setRecipientLongitude(lon);
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

    public static OrderPriority resolveDefaultPriority(OrderPriority requested,
                                               OperationalUnit originUnit,
                                               Company company) {
        if (requested != null) return requested;
        boolean locked = company != null && company.isDefaultPriorityLocked();
        if (!locked && originUnit != null && originUnit.getDefaultPriority() != null) return originUnit.getDefaultPriority();
        if (company != null && company.getDefaultPriority() != null) return company.getDefaultPriority();
        return OrderPriority.NORMAL;
    }

    private String generateReference() {
        long seq = orderRepository.nextReferenceSeq();
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return String.format("DEL-%s-%04d", date, seq);
    }
}
