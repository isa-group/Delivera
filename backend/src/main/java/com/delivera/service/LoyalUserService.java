package com.delivera.service;


import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.dto.loyaluser.LoyalUserRequest;
import com.delivera.dto.loyaluser.LoyalUserResponse;
import com.delivera.dto.order.OrderResponse;
import com.delivera.exception.CompanyContextException;
import com.delivera.exception.LoyalUserConflictException;
import com.delivera.exception.LoyalUserNotFoundException;
import com.delivera.exception.UserNotFoundException;
import com.delivera.model.Company;
import com.delivera.model.LoyalUser;
import com.delivera.repository.CompanyRepository;
import com.delivera.repository.LoyalUserRepository;
import com.delivera.repository.OrderRepository;
import com.delivera.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class LoyalUserService {

    private final LoyalUserRepository loyalUserRepository;
    private final OrderRepository orderRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final SecurityUtils securityUtils;
    private final SubscriptionService subscriptionService;

    public LoyalUserService(LoyalUserRepository loyalUserRepository,
                            OrderRepository orderRepository,
                            CompanyRepository companyRepository,
                            UserRepository userRepository,
                            SecurityUtils securityUtils,
                            SubscriptionService subscriptionService) {
        this.loyalUserRepository = loyalUserRepository;
        this.orderRepository = orderRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.securityUtils = securityUtils;
        this.subscriptionService = subscriptionService;
    }

    @Transactional(readOnly = true)
    public List<LoyalUserResponse> getByCompany() {
        UUID companyId = securityUtils.getCurrentCompanyId();
        return loyalUserRepository.findWithOrderCountByCompanyId(companyId).stream()
                .map(row -> LoyalUserResponse.from((LoyalUser) row[0], (Long) row[1]))
                .toList();
    }

    @Transactional
    public LoyalUserResponse add(LoyalUserRequest request) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        subscriptionService.checkLoyalUserLimit(companyId);
        String email = request.email().toLowerCase().trim();

        if (loyalUserRepository.findByCompaniesIdAndEmail(companyId, email).isPresent()) {
            throw new LoyalUserConflictException();
        }

        Company company = companyRepository.findById(companyId)
                .orElseThrow(CompanyContextException::new);

        LoyalUser lu = loyalUserRepository.findByEmail(email).stream().findFirst()
                .orElseGet(() -> {
                    LoyalUser newLu = new LoyalUser();
                    newLu.setEmail(email);
                    userRepository.findByEmail(email).ifPresent(newLu::setUser);
                    return newLu;
                });
        lu.getCompanies().add(company);
        if (request.name() != null && !request.name().isBlank()) lu.setName(request.name().trim());
        if (request.phone() != null && !request.phone().isBlank()) lu.setPhone(request.phone().trim());
        if (request.address() != null && !request.address().isBlank()) {
            lu.setAddress(request.address());
            lu.setLatitude(request.latitude());
            lu.setLongitude(request.longitude());
        }

        return LoyalUserResponse.from(loyalUserRepository.save(lu));
    }

    @Transactional
    public LoyalUserResponse updateAddress(UUID loyalUserId, LoyalUserRequest request) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        LoyalUser lu = loyalUserRepository.findByIdAndCompaniesId(loyalUserId, companyId)
                .orElseThrow(UserNotFoundException::new);
        boolean hasAddress = request.address() != null && !request.address().isBlank();
        lu.setAddress(hasAddress ? request.address() : null);
        lu.setLatitude(hasAddress ? request.latitude() : null);
        lu.setLongitude(hasAddress ? request.longitude() : null);
        return LoyalUserResponse.from(loyalUserRepository.save(lu),
                orderRepository.countByLoyalUserId(lu.getId()));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersForLoyalUser(UUID loyalUserId) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        loyalUserRepository.findByIdAndCompaniesId(loyalUserId, companyId)
                .orElseThrow(LoyalUserNotFoundException::new);
        return orderRepository.findByLoyalUserIdOrderByCreatedAtDesc(loyalUserId)
                .stream().map(OrderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders() {
        String email = securityUtils.getCurrentEmail();
        return orderRepository.findByRecipientEmailOrderByCreatedAtDesc(email)
                .stream().map(OrderResponse::from).toList();
    }
}
