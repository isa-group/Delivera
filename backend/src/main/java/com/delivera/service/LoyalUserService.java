package com.delivera.service;


import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.dto.loyaluser.LoyalUserRequest;
import com.delivera.dto.loyaluser.LoyalUserResponse;
import com.delivera.exception.CompanyContextException;
import com.delivera.exception.LoyalUserConflictException;
import com.delivera.exception.LoyalUserNotFoundException;
import com.delivera.exception.UserNotFoundException;
import com.delivera.model.LoyalUser;
import com.delivera.order.dto.OrderResponse;
import com.delivera.order.repository.OrderRepository;
import com.delivera.org.model.Company;
import com.delivera.org.repository.CompanyRepository;
import com.delivera.model.LoyalUserCompany;
import com.delivera.repository.LoyalUserRepository;
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
                .map(row -> {
                    LoyalUser lu = (LoyalUser) row[0];
                    return LoyalUserResponse.from(lu, lu.findLink(companyId).orElse(null), (Long) row[1]);
                })
                .toList();
    }

    @Transactional
    public LoyalUserResponse add(LoyalUserRequest request) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        subscriptionService.checkLoyalUserLimit(companyId);
        String email = request.email().toLowerCase().trim();

        if (loyalUserRepository.findByCompanyIdAndEmail(companyId, email).isPresent()) {
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

        LoyalUserCompany link = lu.linkFor(company);
        if (request.name() != null && !request.name().isBlank()) link.setName(request.name().trim());
        if (request.phone() != null && !request.phone().isBlank()) link.setPhone(request.phone().trim());
        if (request.address() != null && !request.address().isBlank()) {
            link.setAddress(request.address());
            link.setLatitude(request.latitude());
            link.setLongitude(request.longitude());
        }

        LoyalUser saved = loyalUserRepository.save(lu);
        return LoyalUserResponse.from(saved, saved.findLink(companyId).orElse(null));
    }

    @Transactional
    public LoyalUserResponse updateAddress(UUID loyalUserId, LoyalUserRequest request) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        LoyalUser lu = loyalUserRepository.findByIdAndCompanyId(loyalUserId, companyId)
                .orElseThrow(UserNotFoundException::new);
        LoyalUserCompany link = lu.findLink(companyId).orElseThrow(UserNotFoundException::new);
        boolean hasAddress = request.address() != null && !request.address().isBlank();
        link.setAddress(hasAddress ? request.address() : null);
        link.setLatitude(hasAddress ? request.latitude() : null);
        link.setLongitude(hasAddress ? request.longitude() : null);
        LoyalUser saved = loyalUserRepository.save(lu);
        return LoyalUserResponse.from(saved, saved.findLink(companyId).orElse(null),
                orderRepository.countByLoyalUserId(loyalUserId));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersForLoyalUser(UUID loyalUserId) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        loyalUserRepository.findByIdAndCompanyId(loyalUserId, companyId)
                .orElseThrow(LoyalUserNotFoundException::new);
        return orderRepository.findByLoyalUserIdAndCompanyIdOrderByCreatedAtDesc(loyalUserId, companyId)
                .stream().map(OrderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders() {
        String email = securityUtils.getCurrentEmail();
        return loyalUserRepository.findByEmail(email).stream()
                .findFirst()
                .map(lu -> orderRepository.findByLoyalUserIdOrderByCreatedAtDesc(lu.getId()))
                .orElse(List.of())
                .stream().map(OrderResponse::from).toList();
    }
}
