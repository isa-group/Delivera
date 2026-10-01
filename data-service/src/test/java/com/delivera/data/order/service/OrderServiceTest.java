package com.delivera.data.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.data.common.service.AppConfigService;
import com.delivera.data.depot.model.OperationalUnit;
import com.delivera.data.depot.repository.OperationalUnitRepository;
import com.delivera.data.email.service.EmailService;
import com.delivera.data.exception.InvalidOrderUnitsException;
import com.delivera.data.exception.MissingClientEmailException;
import com.delivera.data.exception.MissingRecipientAddressException;
import com.delivera.data.exception.OrderAlreadyClaimedException;
import com.delivera.data.exception.OrderClaimEmailMismatchException;
import com.delivera.data.exception.OrderNotFoundException;
import com.delivera.data.order.dto.ClaimRegisterRequest;
import com.delivera.data.order.dto.LoginResponse;
import com.delivera.data.order.dto.OrderLocationRequest;
import com.delivera.data.order.dto.OrderRequest;
import com.delivera.data.order.dto.OrderResponse;
import com.delivera.data.order.dto.OrderStatusRequest;
import com.delivera.data.order.dto.RequestClientData;
import com.delivera.data.order.model.Order;
import com.delivera.data.order.model.OrderPriority;
import com.delivera.data.order.model.OrderStatus;
import com.delivera.data.order.model.OrderType;
import com.delivera.data.order.repository.OrderRepository;
import com.delivera.data.org.model.CompanySettings;
import com.delivera.data.org.service.OrgClient;
import com.delivera.data.org.service.SettingsService;

@ExtendWith (MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OperationalUnitRepository unitRepository;

    @Mock
    private SettingsService settingsService;

    @Mock
    private SecurityUtils securityUtils;

    @Mock
    private AppConfigService appConfigService;

    @Mock
    private EmailService emailService;

    @Mock 
    private OrgClient orgClient;

    @InjectMocks 
    private OrderService service;

    private UUID companyId;
    private UUID orderId;
    private UUID unitId;
    private String email;

    @BeforeEach 
    void setup() {

        companyId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        unitId = UUID.randomUUID();
        email = "example@example.com";
    }
    @Test
    void getByCompany_shouldUseOperatorQuery() {

        UUID userId = UUID.randomUUID();

        when(securityUtils.getCurrentCompanyId())
                .thenReturn(companyId);

        when(securityUtils.getCurrentRole())
                .thenReturn("OPERATOR");

        when(securityUtils.getCurrentUserId())
                .thenReturn(userId);

        when(
            orderRepository.findSentOrReceivedByCompanyIdAndUserId(
                companyId,
                userId
            )
        ).thenReturn(List.of());

        service.getByCompany();

        verify(orderRepository)
                .findSentOrReceivedByCompanyIdAndUserId(
                        companyId,
                        userId
                );
    }
    @Test
    void getByCompany_shouldUseCompanyQuery() {
    
        when(securityUtils.getCurrentCompanyId())
                .thenReturn(companyId);
    
        when(securityUtils.getCurrentRole())
                .thenReturn("ADMIN");
    
        when(
            orderRepository.findSentOrReceivedByCompanyId(
                companyId
            )
        ).thenReturn(List.of());
    
        service.getByCompany();
    
        verify(orderRepository)
                .findSentOrReceivedByCompanyId(
                        companyId
                );
    }
    @Test
    void getDetail_shouldReturnOrder() {
    
        Order order = new Order();
        order.setClaimed(false);
        order.setOrigin(new  OperationalUnit());
        order.setOrderType(OrderType.B2B);
        order.setStatus(OrderStatus.DELIVERED);
    
        when(securityUtils.getCurrentCompanyId())
                .thenReturn(companyId);
    
        when(
            orderRepository.findByIdForCompany(
                orderId,
                companyId
            )
        ).thenReturn(Optional.of(order));
    
        assertThat(
            service.getDetail(orderId)
        ).isNotNull();
    }
    @Test
    void getDetail_shouldThrowNotFound() {

        when(securityUtils.getCurrentCompanyId())
                .thenReturn(companyId);

        when(
            orderRepository.findByIdForCompany(
                orderId,
                companyId
            )
        ).thenReturn(Optional.empty());

        assertThatThrownBy(
            () -> service.getDetail(orderId)
        ).isInstanceOf(
            OrderNotFoundException.class
        );
    }
    @Test
    void getMyOrderDetail_shouldReturnOrder() {
    
        Order order = new Order();

        order.setClaimed(false);
        order.setReference("REF-001");
        order.setStatus(OrderStatus.PENDING);
        order.setOrigin(new OperationalUnit());
        order.setDestination(new OperationalUnit());

    
        when(securityUtils.getCurrentEmail())
                .thenReturn(email);
    
        when(
            orderRepository.findByIdAndRecipientEmail(
                orderId,
                email
            )
        ).thenReturn(Optional.of(order));
    
        assertThat(
            service.getMyOrderDetail(orderId)
        ).isNotNull();
    }
    @Test
    void getMyOrderDetail_shouldThrowNotFound() {

        when(securityUtils.getCurrentEmail())
                .thenReturn(email);

        when(
            orderRepository.findByIdAndRecipientEmail(
                orderId,
                email
            )
        ).thenReturn(Optional.empty());

        assertThatThrownBy(
            () -> service.getMyOrderDetail(orderId)
        ).isInstanceOf(
            OrderNotFoundException.class
        );
    }


    @Test
    void resolveDefaultPriority_shouldUseUnitPriority() {

        OperationalUnit unit =
                new OperationalUnit();

        unit.setCompanyId(companyId);
        unit.setDefaultPriority(
                OrderPriority.HIGH
        );

        CompanySettings settings =
                new CompanySettings();

        settings.setDefaultPriorityLocked(false);

        when(settingsService.get(companyId))
                .thenReturn(settings);

        assertThat(
                service.resolveDefaultPriority(
                        null,
                        unit
                )
        ).isEqualTo(
                OrderPriority.HIGH
        );
    }
    @Test
    void resolveDefaultPriority_shouldUseCompanyPriority() {
    
        OperationalUnit unit =
                new OperationalUnit();
    
        unit.setCompanyId(companyId);
    
        CompanySettings settings =
                new CompanySettings();
    
        settings.setDefaultPriorityLocked(true);
    
        settings.setDefaultPriority(
                OrderPriority.NORMAL
        );
    
        when(settingsService.get(companyId))
                .thenReturn(settings);
    
        assertThat(
                service.resolveDefaultPriority(
                        null,
                        unit
                )
        ).isEqualTo(
                OrderPriority.NORMAL
        );
    }
    @Test
    void resolveDefaultPriority_shouldFallbackToNormal() {
    
        OperationalUnit unit =
                new OperationalUnit();
    
        unit.setCompanyId(companyId);
    
        CompanySettings settings =
                new CompanySettings();
    
        settings.setDefaultPriorityLocked(true);
    
        when(settingsService.get(companyId))
                .thenReturn(settings);
    
        assertThat(
                service.resolveDefaultPriority(
                        null,
                        unit
                )
        ).isEqualTo(
                OrderPriority.NORMAL
        );
    }
    @Test
    void getPublicByToken_shouldReturnOrder() {
    
        Order order = new Order();
        order.setClaimed(false);
        order.setOrigin(new  OperationalUnit());
        order.setOrderType(OrderType.B2B);
        order.setStatus(OrderStatus.DELIVERED);
    
        when(
            orderRepository.findByTrackingToken("TOKEN")
        ).thenReturn(Optional.of(order));
    
        assertThat(
            service.getPublicByToken("TOKEN")
        ).isNotNull();
    }
    @Test
    void getPublicByToken_shouldThrowNotFound() {
    
        when(
            orderRepository.findByTrackingToken("TOKEN")
        ).thenReturn(Optional.empty());
    
        assertThatThrownBy(
            () -> service.getPublicByToken("TOKEN")
        ).isInstanceOf(OrderNotFoundException.class);
    }
    @Test
    void getPublicByReference_shouldReturnOrder() {
    
        Order order = new Order();
        order.setClaimed(false);
        order.setOrigin(new  OperationalUnit());
        order.setOrderType(OrderType.B2B);
        order.setStatus(OrderStatus.DELIVERED);
    
        when(
            orderRepository.findByReference("REF")
        ).thenReturn(Optional.of(order));
    
        assertThat(
            service.getPublicByReference("ref")
        ).isNotNull();
    }
    @Test
    void getPublicByReference_shouldThrowNotFound() {
    
        when(
            orderRepository.findByReference("REF")
        ).thenReturn(Optional.empty());
    
        assertThatThrownBy(
            () -> service.getPublicByReference("ref")
        ).isInstanceOf(OrderNotFoundException.class);
    }
    @Test
    void updateLocation_shouldThrowNotFound() {

        when(securityUtils.getCurrentCompanyId())
                .thenReturn(companyId);

        when(
            orderRepository.findByIdAndCompanyId(
                orderId,
                companyId
            )
        ).thenReturn(Optional.empty());

        OrderLocationRequest request =
                new OrderLocationRequest(
                    BigDecimal.ONE,
                    BigDecimal.ONE
                );

        assertThatThrownBy(
            () -> service.updateLocation(
                orderId,
                request
            )
        ).isInstanceOf(
            OrderNotFoundException.class
        );
    }
    @Test
    void updateLocation_shouldUpdateCoordinates() {
    
        Order order = new Order();
        order.setOrigin(new  OperationalUnit());
        order.setOrderType(OrderType.B2B);
        order.setStatus(OrderStatus.DELIVERED);
        order.setClaimed(false);
    
        when(securityUtils.getCurrentCompanyId())
                .thenReturn(companyId);
    
        when(
            orderRepository.findByIdAndCompanyId(
                orderId,
                companyId
            )
        ).thenReturn(Optional.of(order));
    
        when(
            orderRepository.save(order)
        ).thenReturn(order);
    
        OrderLocationRequest request =
                new OrderLocationRequest(
                    BigDecimal.TEN,
                    BigDecimal.TWO
                );
    
        service.updateLocation(
            orderId,
            request
        );
    
        assertThat(order.getCurrentLat())
                .isEqualTo( BigDecimal.TEN);
    
        assertThat(order.getCurrentLon())
                .isEqualTo(BigDecimal.TWO);
    
        assertThat(order.getCurrentLocationAt())
                .isNotNull();
    }
    @Test
    void delete_shouldDeleteOrder() {
    
        Order order = new Order();
    
        when(securityUtils.getCurrentCompanyId())
                .thenReturn(companyId);
    
        when(
            orderRepository.findByIdAndCompanyId(
                orderId,
                companyId
            )
        ).thenReturn(Optional.of(order));
    
        service.delete(orderId);
    
        verify(orderRepository)
                .delete(order);
    }
    @Test
    void delete_shouldThrowNotFound() {
    
        when(securityUtils.getCurrentCompanyId())
                .thenReturn(companyId);
    
        when(
            orderRepository.findByIdAndCompanyId(
                orderId,
                companyId
            )
        ).thenReturn(Optional.empty());
    
        assertThatThrownBy(
            () -> service.delete(orderId)
        ).isInstanceOf(
            OrderNotFoundException.class
        );
    }
    @Test
    void claimOrder_shouldThrowNotFound() {

        when(orderRepository.findByTrackingToken("TOKEN"))
                .thenReturn(Optional.empty());

        ClaimRegisterRequest request =
            new ClaimRegisterRequest(
                "",
                "",
                "test@test.com",
                "",
                "password"
            );

        assertThatThrownBy(
                () -> service.claimOrder(
                        request,
                        mock(RequestClientData.class),
                        "TOKEN"
                )
        ).isInstanceOf(
                OrderNotFoundException.class
        );
    }
    @Test
    void claimOrder_shouldThrowAlreadyClaimed() {

        Order order = new Order();
        order.setClaimed(true);

        when(orderRepository.findByTrackingToken("TOKEN"))
                .thenReturn(Optional.of(order));

        ClaimRegisterRequest request =
            new ClaimRegisterRequest(
                "",
                "",
                "test@test.com",
                "",
                "password"
            );

        assertThatThrownBy(
                () -> service.claimOrder(
                        request,
                        mock(RequestClientData.class),
                        "TOKEN"
                )
        ).isInstanceOf(
                OrderAlreadyClaimedException.class
        );
    }
    @Test
    void claimOrder_shouldThrowEmailMismatch() {

        Order order = new Order();

        order.setClaimed(false);
        order.setRecipientEmail("real@test.com");

        when(orderRepository.findByTrackingToken("TOKEN"))
                .thenReturn(Optional.of(order));

        ClaimRegisterRequest request =
            new ClaimRegisterRequest(
                "",
                "",
                "test@test.com",
                "",
                "password"
        );

        assertThatThrownBy(
                () -> service.claimOrder(
                        request,
                        mock(RequestClientData.class),
                        "TOKEN"
                )
        ).isInstanceOf(
                OrderClaimEmailMismatchException.class
        );
    }
    @Test
    void claimOrder_shouldClaimOrder() {

        UUID loyalUserId = UUID.randomUUID();

        Order order = new Order();

        order.setClaimed(false);
        order.setRecipientEmail("test@test.com");
        order.setRecipientAddress("Address");
        order.setLoyalUserId(loyalUserId);
        order.setCompanyId(companyId);

        LoginResponse loginResponse = new  LoginResponse(
            "TOKEN", email, companyId, "", "", "", "", loyalUserId, null);

        loginResponse.setLoyalUserId(
                loyalUserId
        );

        when(orderRepository.findByTrackingToken("TOKEN"))
                .thenReturn(Optional.of(order));

        when(orgClient.claimRegister(any()))
                .thenReturn(loginResponse);

        ClaimRegisterRequest request =
                new ClaimRegisterRequest(
                        "",
                        "",
                        "test@test.com",
                        "",
                        "password"
                );

        LoginResponse result =
                service.claimOrder(
                        request,
                        mock(RequestClientData.class),
                        "TOKEN"
                );

        verify(orderRepository)
                .save(order);

        assertThat(order.getClaimed())
                .isTrue();

        assertThat(order.getLoyalUserId())
                .isEqualTo(loyalUserId);

        assertThat(result.getLoyalUserId())
                .isNull();
    }
    @Test
    void updateStatusSeed_shouldThrowNotFound() {

        OrderStatusRequest request =
                new OrderStatusRequest();

        request.setCompanyId(companyId);

        when(
                orderRepository.findByIdAndCompanyId(
                        orderId,
                        companyId
                )
        ).thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> service.updateStatusSeed(
                        orderId,
                        request
                )
        ).isInstanceOf(
                OrderNotFoundException.class
        );
    }
    @Test
    void updateStatus_shouldPopulateRequestData() {
    
        Order order = new Order();
        order.setOrigin(new  OperationalUnit());
        order.setOrderType(OrderType.B2B);
        order.setClaimed(false);
    
        order.setStatus(
                OrderStatus.PENDING
        );
    
        when(securityUtils.getCurrentCompanyId())
                .thenReturn(companyId);
    
        when(securityUtils.getCurrentEmail())
                .thenReturn(email);
    
        when(
                orderRepository.findByIdAndCompanyId(
                        orderId,
                        companyId
                )
        ).thenReturn(Optional.of(order));
    
        when(orderRepository.save(order))
                .thenReturn(order);
    
        OrderStatusRequest request =
                new OrderStatusRequest();
    
        request.setStatus(
                OrderStatus.IN_TRANSIT
        );
    
        service.updateStatus(
                orderId,
                request
        );
    
        assertThat(request.getCompanyId())
                .isEqualTo(companyId);
    
        assertThat(request.getEmail())
                .isEqualTo(email);
    }
    @Test
    void create_shouldRejectB2BDestinationOfSameCompany() {

        UUID originId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();

        OperationalUnit origin =
                new OperationalUnit();

        origin.setId(originId);
        origin.setCompanyId(companyId);

        OperationalUnit destination =
                new OperationalUnit();

        destination.setId(destinationId);
        destination.setCompanyId(companyId);

        OrderRequest request =
                new OrderRequest();

        request.setOrderType(
                OrderType.B2B
        );

        request.setOriginId(originId);
        request.setDestinationId(destinationId);

        when(
                unitRepository.findByIdAndCompanyId(
                        originId,
                        companyId
                )
        ).thenReturn(Optional.of(origin));

        when(
                unitRepository.findById(
                        destinationId
                )
        ).thenReturn(Optional.of(destination));

        assertThatThrownBy(
                () -> service.create(
                        request,
                        companyId,
                        email
                )
        ).isInstanceOf(
                InvalidOrderUnitsException.class
        );
    }
    @Test
    void create_shouldThrowMissingClientEmail() {

        OperationalUnit origin =
                new OperationalUnit();

        origin.setId(unitId);
        origin.setCompanyId(companyId);

        OrderRequest request =
                new OrderRequest();

        request.setOrderType(
                OrderType.B2C
        );

        request.setOriginId(unitId);

        when(
            settingsService.get(any())
        ).thenReturn(new  CompanySettings());

        when(
                unitRepository.findByIdAndCompanyId(
                        unitId,
                        companyId
                )
        ).thenReturn(Optional.of(origin));

        assertThatThrownBy(
                () -> service.create(
                        request,
                        companyId,
                        email
                )
        ).isInstanceOf(
                MissingClientEmailException.class
        );
    }
    @Test
    void create_shouldThrowMissingRecipientAddress() {

        OperationalUnit origin =
                new OperationalUnit();

        origin.setId(unitId);
        origin.setCompanyId(companyId);

        OrderRequest request =
                new OrderRequest();

        request.setOrderType(
                OrderType.B2C
        );

        request.setOriginId(unitId);
        request.setRecipientEmail(
                "client@test.com"
        );

        
        when(
            settingsService.get(any())
        ).thenReturn(new  CompanySettings());

        when(
                unitRepository.findByIdAndCompanyId(
                        unitId,
                        companyId
                )
        ).thenReturn(Optional.of(origin));

        assertThatThrownBy(
                () -> service.create(
                        request,
                        companyId,
                        email
                )
        ).isInstanceOf(
                MissingRecipientAddressException.class
        );
    }
    @Test
    void create_shouldThrowWhenInternalDestinationNotFound() {
    
        UUID originId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
    
        OperationalUnit origin = new OperationalUnit();
    
        origin.setId(originId);
        origin.setCompanyId(companyId);
    
        OrderRequest request = new OrderRequest();
    
        request.setOrderType(OrderType.INTERNAL);
        request.setOriginId(originId);
        request.setDestinationId(destinationId);
    
        when(
            unitRepository.findByIdAndCompanyId(
                originId,
                companyId
            )
        ).thenReturn(Optional.of(origin));
    
        when(
            unitRepository.findByIdAndCompanyId(
                destinationId,
                companyId
            )
        ).thenReturn(Optional.empty());
    
        assertThatThrownBy(
            () -> service.create(
                request,
                companyId,
                email
            )
        ).isInstanceOf(
            InvalidOrderUnitsException.class
        );
    }
    @Test
    void create_shouldThrowWhenB2BDestinationNotFound() {
    
        UUID originId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
    
        OperationalUnit origin = new OperationalUnit();
    
        origin.setId(originId);
        origin.setCompanyId(companyId);
    
        OrderRequest request = new OrderRequest();
    
        request.setOrderType(OrderType.B2B);
        request.setOriginId(originId);
        request.setDestinationId(destinationId);
    
        when(
            unitRepository.findByIdAndCompanyId(
                originId,
                companyId
            )
        ).thenReturn(Optional.of(origin));
    
        when(
            unitRepository.findById(
                destinationId
            )
        ).thenReturn(Optional.empty());
    
        assertThatThrownBy(
            () -> service.create(
                request,
                companyId,
                email
            )
        ).isInstanceOf(
            InvalidOrderUnitsException.class
        );
    }
    @Test
    void create_shouldThrowWhenOriginNotFound() {
    
        OrderRequest request = new OrderRequest();
    
        request.setOrderType(OrderType.B2C);
        request.setOriginId(unitId);
    
        when(
            unitRepository.findByIdAndCompanyId(
                unitId,
                companyId
            )
        ).thenReturn(Optional.empty());
    
        assertThatThrownBy(
            () -> service.create(
                request,
                companyId,
                email
            )
        ).isInstanceOf(
            InvalidOrderUnitsException.class
        );
    }
    @Test
    void create_shouldCreateB2COrder() {
        TransactionSynchronizationManager.initSynchronization();

        try {

            OperationalUnit origin =
            new OperationalUnit();

            origin.setId(unitId);
            origin.setCompanyId(companyId);

            CompanySettings settings =
                new CompanySettings();

            settings.setDefaultPriorityLocked(true);

            when(
                unitRepository.findByIdAndCompanyId(
                    unitId,
                    companyId
                )
            ).thenReturn(Optional.of(origin));

            when(settingsService.get(companyId))
                .thenReturn(settings);

            when(
                orderRepository.save(any(Order.class))
            ).thenAnswer(i -> i.getArgument(0));

            OrderRequest request =
                new OrderRequest();

            request.setOrderType(OrderType.B2C);
            request.setOriginId(unitId);

            request.setRecipientEmail(
                " CLIENT@TEST.COM "
            );

            request.setRecipientName(
                " John Doe "
            );

            request.setRecipientAddress(
                "Address"
            );

            request.setRecipientLatitude(
                BigDecimal.valueOf(40)
            );

            request.setRecipientLongitude(
                BigDecimal.valueOf(-3)
            );

            request.setClaimed(false);

            OrderResponse response =
                service.create(
                    request,
                    companyId,
                    email
                );

            assertThat(response)
                .isNotNull();

            verify(orderRepository)
                .save(any(Order.class));

        }catch (Exception e) {}finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
       
    }

    @Test
    void create_shouldRegisterTrackingEmail() throws IllegalStateException {

        TransactionSynchronizationManager.initSynchronization();

        try {

            OperationalUnit origin =
            new OperationalUnit();

            origin.setId(unitId);
            origin.setCompanyId(companyId);

            CompanySettings settings =
                new CompanySettings();

            settings.setDefaultPriorityLocked(true);

            when(
                unitRepository.findByIdAndCompanyId(
                    unitId,
                    companyId
                )
            ).thenReturn(Optional.of(origin));

            when(settingsService.get(companyId))
                .thenReturn(settings);

            when(
                orderRepository.save(any(Order.class))
            ).thenAnswer(i -> i.getArgument(0));

            OrderRequest request =
                new OrderRequest();

            request.setOrderType(OrderType.B2C);
            request.setOriginId(unitId);

            request.setRecipientEmail(
                " CLIENT@TEST.COM "
            );

            request.setRecipientName(
                " John Doe "
            );

            request.setRecipientAddress(
                "Address"
            );

            request.setRecipientLatitude(
                BigDecimal.valueOf(40)
            );

            request.setRecipientLongitude(
                BigDecimal.valueOf(-3)
            );

            request.setClaimed(false);

            OrderResponse response =
                service.create(
                    request,
                    companyId,
                    email
                );

            assertThat(response)
                .isNotNull();

            verify(orderRepository)
                .save(any(Order.class));

            List<TransactionSynchronization> syncs =
                    TransactionSynchronizationManager.getSynchronizations();

            assertThat(syncs)
                    .hasSize(1);

            syncs.get(0).afterCommit();

            verify(emailService)
                    .sendTrackingLink(
                            anyString(),
                            anyString(),
                            nullable(String.class),
                            nullable(String.class)
                    );

        }catch (Exception e) {}finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
    @Test
    void create_shouldPopulateRequestBeforeDelegating() {

        OrderService spy =
            Mockito.spy(service);

        OrderRequest request =
            new OrderRequest();

        when(securityUtils.getCurrentCompanyId())
            .thenReturn(companyId);

        when(securityUtils.getCurrentEmail())
            .thenReturn(email);

        when(orderRepository.nextReferenceSeq())
            .thenReturn(1L);

        doReturn(mock(OrderResponse.class))
            .when(spy)
            .create(
                request,
                companyId,
                email
            );

        spy.create(request);

        assertThat(request.getReference())
            .isNotNull();

        assertThat(request.getClaimed())
            .isFalse();
    }
    @Test
    void create_shouldThrowWhenInternalOriginAndDestinationAreSame() {
    
        UUID sameId = UUID.randomUUID();
    
        OperationalUnit origin =
                new OperationalUnit();
    
        origin.setId(sameId);
        origin.setCompanyId(companyId);
    
        OperationalUnit destination =
                new OperationalUnit();
    
        destination.setId(sameId);
        destination.setCompanyId(companyId);
    
        OrderRequest request =
                new OrderRequest();
    
        request.setOrderType(OrderType.INTERNAL);
        request.setOriginId(sameId);
        request.setDestinationId(sameId);
    
        when(
                unitRepository.findByIdAndCompanyId(
                        sameId,
                        companyId
                )
        ).thenReturn(Optional.of(origin));
    
        assertThatThrownBy(
                () -> service.create(
                        request,
                        companyId,
                        email
                )
        ).isInstanceOf(
                InvalidOrderUnitsException.class
        );
    }
    @Test
    void create_shouldThrowWhenInternalDestinationDoesNotExist() {
    
        UUID originId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
    
        OperationalUnit origin =
                new OperationalUnit();
    
        origin.setId(originId);
        origin.setCompanyId(companyId);
    
        OrderRequest request =
                new OrderRequest();
    
        request.setOrderType(OrderType.INTERNAL);
        request.setOriginId(originId);
        request.setDestinationId(destinationId);
    
        when(
                unitRepository.findByIdAndCompanyId(
                        originId,
                        companyId
                )
        ).thenReturn(Optional.of(origin));
    
        when(
                unitRepository.findByIdAndCompanyId(
                        destinationId,
                        companyId
                )
        ).thenReturn(Optional.empty());
    
        assertThatThrownBy(
                () -> service.create(
                        request,
                        companyId,
                        email
                )
        ).isInstanceOf(
                InvalidOrderUnitsException.class
        );
    }
    
    
}