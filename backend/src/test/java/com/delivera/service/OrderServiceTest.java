package com.delivera.service;


import com.delivera.order.dto.OrderLocationRequest;
import com.delivera.order.dto.OrderRequest;
import com.delivera.order.dto.OrderResponse;
import com.delivera.order.dto.OrderStatusRequest;
import com.delivera.order.model.Order;
import com.delivera.order.model.OrderPriority;
import com.delivera.order.model.OrderStatus;
import com.delivera.order.model.OrderType;
import com.delivera.order.repository.OrderRepository;
import com.delivera.order.service.OrderClient;
import com.delivera.order.service.OrderService;
import com.delivera.org.model.Company;
import com.delivera.org.model.Organization;
import com.delivera.org.repository.CompanyRepository;
import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.depot.model.OperationalUnit;
import com.delivera.depot.model.UnitType;
import com.delivera.depot.repository.OperationalUnitRepository;
import com.delivera.exception.InvalidOrderUnitsException;
import com.delivera.exception.OrderNotFoundException;
import com.delivera.model.*;
import com.delivera.repository.*;
import com.delivera.space.service.SpaceLoyalUsers;
import com.delivera.worker.repository.WorkerRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OperationalUnitRepository unitRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private LoyalUserRepository loyalUserRepository;
    @Mock
    private WorkerRepository workerRepository;
    @Mock
    private SecurityUtils securityUtils;
    @Mock
    private AppConfigService appConfigService;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private EmailService emailService;
    @InjectMocks
    private OrderService orderService;

    @Mock 
    private OrderClient orderClient;

    @Mock 
    private  SpaceLoyalUsers spaceLoyalUsers;

    private UUID companyId;
    private Company company;
    private Organization organization;
    private OperationalUnit origin;
    private OperationalUnit destination;
    private Order order;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        organization = new Organization();
        organization.setId(UUID.randomUUID());
        organization.setName("TestOrg");
        organization.setHandle("test-org");

        company = new Company();
        company.setId(companyId);
        company.setName("TestCompany");
        company.setOrganization(organization);

        origin = new OperationalUnit();
        origin.setId(UUID.randomUUID());
        origin.setName("Origin");
        origin.setType(UnitType.WAREHOUSE);
        origin.setCompany(company);

        destination = new OperationalUnit();
        destination.setId(UUID.randomUUID());
        destination.setName("Destination");
        destination.setType(UnitType.STORE);
        destination.setCompany(company);

        order = new Order();
        order.setId(UUID.randomUUID());
        order.setCompany(company);
        order.setReference("DEL-20240101-0001");
        order.setOrigin(origin);
        order.setDestination(destination);
        order.setStatus(OrderStatus.PENDING);
        order.setPriority(OrderPriority.NORMAL);
        order.setOrderType(OrderType.INTERNAL);
    }

    @AfterEach
    void clearSync() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

  

    @Test
    void getByCompany_returnsMappedList() {
        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(orderRepository.findSentOrReceivedByCompanyId(companyId)).thenReturn(List.of(order));

        assertThat(orderService.getByCompany()).hasSize(1);
    }
    


    @Test
    void updateStatus_success() {
        OrderStatusRequest req = new OrderStatusRequest(OrderStatus.IN_TRANSIT, null);
        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(securityUtils.getCurrentEmail()).thenReturn("admin@test.com");
        when(orderRepository.findByIdAndCompanyId(order.getId(), companyId)).thenReturn(Optional.of(order));
        doNothing().when(appConfigService).validateTransition(any(), any());
        when(orderRepository.save(order)).thenReturn(order);

        assertThat(orderService.updateStatus(order.getId(), req)).isNotNull();
    }

    @Test
    void updateLocation_setsCoordinatesAndTimestamp() {
        OrderLocationRequest req = new OrderLocationRequest(new java.math.BigDecimal("40.4"), new java.math.BigDecimal("-3.7"));
        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(orderRepository.findByIdAndCompanyId(order.getId(), companyId)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        orderService.updateLocation(order.getId(), req);

        assertThat(order.getCurrentLat()).isEqualByComparingTo("40.4");
        assertThat(order.getCurrentLon()).isEqualByComparingTo("-3.7");
        assertThat(order.getCurrentLocationAt()).isNotNull();
    }

    @Test
    void updateLocation_orderNotFoundThrows() {
        OrderLocationRequest req = new OrderLocationRequest(new java.math.BigDecimal("40"), new java.math.BigDecimal("-3"));
        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(orderRepository.findByIdAndCompanyId(any(), any())).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> orderService.updateLocation(order.getId(), req))
                .isInstanceOf(com.delivera.exception.OrderNotFoundException.class);
    }

    @Test
    void delete_success() {
        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(orderRepository.findByIdAndCompanyId(order.getId(), companyId)).thenReturn(Optional.of(order));

        orderService.delete(order.getId());
        verify(orderRepository).delete(order);
    }

    @Test
    void getPublicByToken_found() {
        order.setTrackingToken("abc123");
        when(orderRepository.findByTrackingToken("abc123")).thenReturn(Optional.of(order));
        assertThat(orderService.getPublicByToken("abc123")).isNotNull();
    }

    @Test
    void getPublicByReference_normalizesAndFinds() {
        when(orderRepository.findByReference("DEL-X")).thenReturn(Optional.of(order));
        assertThat(orderService.getPublicByReference(" del-x ")).isNotNull();
    }

    @Test
    void getDetail_found() {
        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(orderRepository.findByIdForCompany(order.getId(), companyId)).thenReturn(Optional.of(order));
        assertThat(orderService.getDetail(order.getId())).isNotNull();
    }

    @Test
    void create_b2cOrder_withRecipientAddress_success() {

        OrderResponse response =
        new OrderResponse();

        OrderRequest req = new OrderRequest(
                null,
                null,
                "c@t.com",
                "Client",
                "Street 1",
                new BigDecimal("40.0"),
                new BigDecimal("-3.0"),
                OrderType.B2C,
                null,
                null,
                null,
                null
        );

        when(securityUtils.getCurrentCompanyId())
                .thenReturn(companyId);

        when(securityUtils.getCurrentOrgId())
                .thenReturn(UUID.randomUUID());

        when(companyRepository.findById(companyId))
                .thenReturn(Optional.of(company));

        when(workerRepository.findByUserEmailOrderByCreatedAtAsc("c@t.com"))
                .thenReturn(List.of());

        when(loyalUserRepository.findByEmail("c@t.com"))
                .thenReturn(List.of());

        when(loyalUserRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        when(orderClient.executeB2CRequest(any()))
                .thenReturn(response);

        assertThat(
                orderService.createB2C(req)
        ).isSameAs(response);
    }

    
    @Test
    void create_b2c_usesLoyalUserAddress() {

        LoyalUser loyalUser = new LoyalUser();

        loyalUser.setId(UUID.randomUUID());
        loyalUser.setEmail("c@t.com");

        LoyalUserCompany link =
                loyalUser.linkFor(company);

        link.setAddress("Loyal St");

        link.setLatitude(
                new BigDecimal("1.0")
        );

        link.setLongitude(
                new BigDecimal("2.0")
        );

        OrderRequest req =
                new OrderRequest(
                        null,
                        null,
                        "c@t.com",
                        null,
                        null,
                        null,
                        null,
                        OrderType.B2C,
                        null,
                        null,
                        null,
                        null
                );

        OrderResponse response =
        new OrderResponse();

        when(
                securityUtils.getCurrentCompanyId()
        ).thenReturn(companyId);

        when(
                securityUtils.getCurrentOrgId()
        ).thenReturn(UUID.randomUUID());

        when(
                companyRepository.findById(companyId)
        ).thenReturn(Optional.of(company));

        when(
                workerRepository.findByUserEmailOrderByCreatedAtAsc("c@t.com")
        ).thenReturn(List.of());

        when(
                loyalUserRepository.findByEmail("c@t.com")
        ).thenReturn(List.of(loyalUser));

        when(
                loyalUserRepository.save(any())
        ).thenAnswer(i -> i.getArgument(0));

        when(
                orderClient.executeB2CRequest(any())
        ).thenReturn(response);

        assertThat(
                orderService.createB2C(req)
        ).isSameAs(response);
    }


}
