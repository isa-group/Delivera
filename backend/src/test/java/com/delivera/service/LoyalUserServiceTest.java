package com.delivera.service;


import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.dto.loyaluser.LoyalUserRequest;
import com.delivera.exception.CompanyContextException;
import com.delivera.exception.LoyalUserConflictException;
import com.delivera.exception.OrderNotFoundException;
import com.delivera.model.*;
import com.delivera.repository.*;
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
class LoyalUserServiceTest {

    @Mock
    private LoyalUserRepository loyalUserRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SecurityUtils securityUtils;
    @Mock
    private SubscriptionService subscriptionService;
    @InjectMocks
    private LoyalUserService loyalUserService;

    private UUID companyId;
    private Company company;
    private LoyalUser loyalUser;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        company = new Company();
        company.setId(companyId);

        loyalUser = new LoyalUser();
        loyalUser.setId(UUID.randomUUID());
        loyalUser.setEmail("loyal@test.com");
        loyalUser.getCompanies().add(company);
    }

    @Test
    void getByCompany_returnsUsersWithOrders() {
        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(loyalUserRepository.findWithOrderCountByCompanyId(companyId))
                .thenReturn(List.<Object[]>of(new Object[]{loyalUser, 2L}));

        assertThat(loyalUserService.getByCompany()).hasSize(1);
    }

    @Test
    void add_success_noRegisteredUser() {
        LoyalUserRequest req = new LoyalUserRequest("new@test.com", null, null, null, null, null);
        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(loyalUserRepository.findByCompaniesIdAndEmail(companyId, "new@test.com")).thenReturn(Optional.empty());
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company));
        when(userRepository.findByEmail("new@test.com")).thenReturn(Optional.empty());
        when(loyalUserRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = loyalUserService.add(req);
        assertThat(result.email()).isEqualTo("new@test.com");
        verify(loyalUserRepository).save(any());
    }

    @Test
    void getMyOrders_returnsOrdersByRecipientEmail() {
        when(securityUtils.getCurrentEmail()).thenReturn("loyal@test.com");
        when(orderRepository.findByRecipientEmailOrderByCreatedAtDesc("loyal@test.com")).thenReturn(List.of());

        assertThat(loyalUserService.getMyOrders()).isEmpty();
        verify(orderRepository).findByRecipientEmailOrderByCreatedAtDesc("loyal@test.com");
    }

    @Test
    void updateAddress_success() {
        LoyalUserRequest req = new LoyalUserRequest("loyal@test.com", null, null, "New Addr", new java.math.BigDecimal("1.0"), new java.math.BigDecimal("2.0"));
        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(loyalUserRepository.findByIdAndCompaniesId(loyalUser.getId(), companyId)).thenReturn(Optional.of(loyalUser));
        when(loyalUserRepository.save(loyalUser)).thenReturn(loyalUser);

        var result = loyalUserService.updateAddress(loyalUser.getId(), req);
        assertThat(result.address()).isEqualTo("New Addr");
    }

    @Test
    void getOrdersForLoyalUser_returnsOrders() {
        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(loyalUserRepository.findByIdAndCompaniesId(loyalUser.getId(), companyId)).thenReturn(Optional.of(loyalUser));
        when(orderRepository.findByLoyalUserIdOrderByCreatedAtDesc(loyalUser.getId())).thenReturn(List.of());

        assertThat(loyalUserService.getOrdersForLoyalUser(loyalUser.getId())).isEmpty();
    }

    @Test
    void add_alreadyExists_throws() {
        LoyalUserRequest req = new LoyalUserRequest("dup@test.com", null, null, null, null, null);
        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(loyalUserRepository.findByCompaniesIdAndEmail(companyId, "dup@test.com")).thenReturn(Optional.of(loyalUser));
        assertThatThrownBy(() -> loyalUserService.add(req)).isInstanceOf(LoyalUserConflictException.class);
    }

    @Test
    void add_withAddress_setsCoordinates() {
        LoyalUserRequest req = new LoyalUserRequest("new@test.com", null, null, "St 1",
                new java.math.BigDecimal("5.0"), new java.math.BigDecimal("6.0"));
        when(securityUtils.getCurrentCompanyId()).thenReturn(companyId);
        when(loyalUserRepository.findByCompaniesIdAndEmail(companyId, "new@test.com")).thenReturn(Optional.empty());
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company));
        when(loyalUserRepository.findByEmail("new@test.com")).thenReturn(List.of());
        when(userRepository.findByEmail("new@test.com")).thenReturn(Optional.empty());
        when(loyalUserRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        var result = loyalUserService.add(req);
        assertThat(result.address()).isEqualTo("St 1");
    }
}
