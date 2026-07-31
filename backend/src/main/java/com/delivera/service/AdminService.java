package com.delivera.service;

import com.delivera.auth.service.AuthClient;
import com.delivera.depot.repository.OperationalUnitRepository;
import com.delivera.dto.admin.*;
import com.delivera.exception.ForbiddenException;
import com.delivera.model.*;
import com.delivera.order.model.OrderStatus;
import com.delivera.order.repository.OrderEventRepository;
import com.delivera.order.repository.OrderMessageRepository;
import com.delivera.order.repository.OrderRepository;
import com.delivera.org.model.Company;
import com.delivera.org.model.Organization;
import com.delivera.org.repository.CompanyRepository;
import com.delivera.org.repository.OrganizationRepository;
import com.delivera.org.service.SettingsClient;
import com.delivera.repository.*;
import com.delivera.worker.model.Worker;
import com.delivera.worker.model.WorkerRole;
import com.delivera.worker.repository.WorkerRepository;
import com.delivera.worker.service.UnitWorkerClient;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;


import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminService {

    private static final String SYSTEM_ORG_HANDLE = "delivera";

    private final OrganizationRepository organizationRepository;
    private final CompanyRepository companyRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final WorkerRepository workerRepository;
    private final OperationalUnitRepository unitRepository;
    private final OrderMessageRepository orderMessageRepository;
    private final OrderEventRepository orderEventRepository;
    private final LoyalUserCompanyRepository loyalUserCompanyRepository;
    private final LoyalUserRepository loyalUserRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final UnitWorkerClient unitWorkerClient;
    private final AuthClient authClient;
    private final SettingsClient settingsClient;

    // ── Listing ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<OrganizationSummary> listOrganizations() {
        return organizationRepository.findAllSummaries().stream()
                .map(row -> new OrganizationSummary(
                        (UUID) row[0],
                        (String) row[1],
                        (String) row[2],
                        (Instant) row[3],
                        (Long) row[4],
                        (Long) row[5],
                        (Long) row[6]))
                .toList();
    }

    @Transactional(readOnly = true)
    public GlobalMetrics getGlobalMetrics() {
        Instant monthStart = LocalDate.now(ZoneOffset.UTC)
                .with(TemporalAdjusters.firstDayOfMonth())
                .atStartOfDay().toInstant(ZoneOffset.UTC);
        return new GlobalMetrics(
                organizationRepository.countTenants(),
                companyRepository.countTenants(),
                orderRepository.countByCreatedAtAfter(monthStart),
                userRepository.count() - 1);  // excluye al admin de sistema
    }

    @Transactional(readOnly = true)
    public List<CompanySummary> listCompanies() {
        return companyRepository.findAll().stream()
                .filter(c -> !SYSTEM_ORG_HANDLE.equals(c.getOrganization().getHandle()))
                .map(c -> new CompanySummary(
                        c.getId(),
                        c.getName(),
                        c.getOrganization().getId(),
                        c.getOrganization().getName(),
                        workerRepository.countByCompanyId(c.getId()),
                        orderRepository.countByOrganizationId(c.getOrganization().getId()),
                        c.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderAdminSummary> listOrders() {
        return orderRepository.findAll().stream()
                .map(o -> new OrderAdminSummary(
                        o.getId(),
                        o.getReference(),
                        o.getStatus().name(),
                        o.getOrderType().name(),
                        o.getCompany().getName(),
                        o.getCompany().getOrganization().getName(),
                        o.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserSummary> listUsers() {
        return userRepository.findAll().stream()
                .map(u -> {
                    boolean isWorker = workerRepository.countByUser_Id(u.getId()) > 0;
                    boolean isGlobalAdmin = workerRepository.existsByUser_IdAndRole(u.getId(), WorkerRole.GLOBAL_ADMIN);
                    return new UserSummary(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(),
                            u.getCreatedAt(), isWorker, isGlobalAdmin);
                })
                .filter(u -> u.isGlobalAdmin() || !u.isWorker())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkerAdminSummary> listWorkers() {
        return workerRepository.findAll().stream()
                .filter(w -> !SYSTEM_ORG_HANDLE.equals(w.getCompany().getOrganization().getHandle()))
                .map(w -> new WorkerAdminSummary(
                        w.getId(),
                        w.getUser().getId(),
                        w.getUser().getEmail(),
                        w.getUser().getFirstName(),
                        w.getUser().getLastName(),
                        w.getRole().name(),
                        w.getCompany().getName(),
                        w.getCompany().getOrganization().getName(),
                        w.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UnitAdminSummary> listUnits() {
        return unitRepository.findAll().stream()
                .filter(u -> u.getLatitude() != null && u.getLongitude() != null)
                .map(u -> new UnitAdminSummary(
                        u.getId(),
                        u.getName(),
                        u.getType().name(),
                        u.getLatitude(),
                        u.getLongitude(),
                        u.getCompany().getName(),
                        u.getCompany().getOrganization().getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RouteAdminEntry> getActiveRoutes() {
        return orderRepository.findAll().stream()
                .filter(o -> o.getStatus() == OrderStatus.PENDING || o.getStatus() == OrderStatus.IN_TRANSIT)
                .filter(o -> o.getOrigin() != null
                        && o.getOrigin().getLatitude() != null
                        && o.getOrigin().getLongitude() != null)
                .filter(o -> {
                    boolean unitDest = o.getDestination() != null
                            && o.getDestination().getLatitude() != null
                            && o.getDestination().getLongitude() != null;
                    boolean recipientDest = o.getRecipientLatitude() != null && o.getRecipientLongitude() != null;
                    return unitDest || recipientDest;
                })
                .map(o -> {
                    boolean useUnit = o.getDestination() != null
                            && o.getDestination().getLatitude() != null
                            && o.getDestination().getLongitude() != null;
                    double destLat = useUnit
                            ? o.getDestination().getLatitude().doubleValue()
                            : o.getRecipientLatitude().doubleValue();
                    double destLon = useUnit
                            ? o.getDestination().getLongitude().doubleValue()
                            : o.getRecipientLongitude().doubleValue();
                    UUID destId = useUnit ? o.getDestination().getId() : null;
                    String destName = useUnit ? o.getDestination().getName()
                            : (o.getRecipientName() != null ? o.getRecipientName() : o.getRecipientEmail());
                    return new RouteAdminEntry(
                            o.getId(), o.getReference(), o.getStatus().name(),
                            o.getOrigin().getLatitude().doubleValue(), o.getOrigin().getLongitude().doubleValue(),
                            o.getOrigin().getId(), o.getOrigin().getName(),
                            destLat, destLon, destId, destName);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CompanyRankingEntry> getCompanyRanking(String period) {
        Instant from = periodStart(period);
        return orderRepository.rankCompaniesByOrderCount(from).stream()
                .map(row -> new CompanyRankingEntry(
                        (UUID) row[0],
                        (String) row[1],
                        (String) row[2],
                        ((Number) row[3]).longValue()))
                .toList();
    }
    // ── Delete operations ─────────────────────────────────────────────────────

    @Transactional
    public void deleteOrganization(UUID orgId) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (SYSTEM_ORG_HANDLE.equals(org.getHandle())) {
            throw new ForbiddenException("FORBIDDEN");
        }
        Set<UUID> companyIds = companyRepository.findIdsByOrganization(orgId);
        Set<UUID> userIds = workerRepository.findWorkersAccounts(companyIds);
   

        loyalUserCompanyRepository.deleteByCompanyIds(companyIds);
        apiKeyRepository.deleteByCompanyIds(companyIds);
        workerRepository.deleteByCompanyIds(companyIds);

        companyRepository.deleteByOrganizationId(orgId);
        organizationRepository.deleteById(orgId);
        userRepository.deleteByUserIds(userIds);

        settingsClient.deleteOrganization(companyIds);
        authClient.deleteUsers(userIds);
    }

    @Transactional
    public void deleteCompany(UUID companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (SYSTEM_ORG_HANDLE.equals(company.getOrganization().getHandle())) {
            throw new ForbiddenException("FORBIDDEN");
        }
        deleteCompanyCascade(company.getOrganization().getId(),companyId);
        companyRepository.deleteById(companyId);
    }

    @Transactional
    public void deleteOrder(UUID orderId) {
        if (!orderRepository.existsById(orderId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        orderMessageRepository.deleteByOrderId(orderId);
        orderEventRepository.deleteByOrderId(orderId);
        orderRepository.deleteById(orderId);
    }

    @Transactional
    public void deleteUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (workerRepository.existsByUser_IdAndRole(userId, WorkerRole.GLOBAL_ADMIN)) {
            throw new ForbiddenException("FORBIDDEN");
        }
        //orderMessageRepository.deleteBySenderId(userId); TODO: USER || WORKER IN FRONTEND
        //workerRepository.deleteUnitWorkersByUserId(userId);
        //workerRepository.deleteByUserId(userId);
        loyalUserRepository.findByUserId(userId).ifPresent(lu -> {
            lu.setUser(null);
            loyalUserRepository.save(lu);
        });
        authClient.deleteUser(userId);
        userRepository.delete(user);
    }

    @Transactional
    public void deleteWorker(UUID workerId) {
        Worker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (worker.getRole() == WorkerRole.GLOBAL_ADMIN) {
            throw new ForbiddenException("FORBIDDEN");
        }
        unitWorkerClient.unassignWorkerOfAllUnits(workerId);
        authClient.deleteUser(worker.getUser().getId());
        workerRepository.deleteById(workerId);
    }

    @Transactional
    public void resetDatabase(String adminEmail) {
        Worker adminWorker = workerRepository.findByUserEmailOrderByCreatedAtAsc(adminEmail).stream()
                .filter(w -> w.getRole() == WorkerRole.GLOBAL_ADMIN)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));

        UUID adminUserId = adminWorker.getUser().getId();
        UUID adminCompanyId = adminWorker.getCompany().getId();
        UUID adminOrgId = adminWorker.getCompany().getOrganization().getId();

     

        // Delete loyal user data
        loyalUserCompanyRepository.deleteAllLinks();
        loyalUserRepository.deleteAllLoyalUsers();

        // Delete API keys except admin's company
        apiKeyRepository.deleteAllExceptCompany(adminCompanyId);

        // Delete workers except GLOBAL_ADMIN
        workerRepository.deleteAllExceptRole(WorkerRole.GLOBAL_ADMIN);

        // Delete companies except admin's
        companyRepository.deleteAllExcept(adminCompanyId);

        // Delete organizations except admin's
        organizationRepository.deleteAllExcept(adminOrgId);

        // Delete users except admin
        userRepository.deleteAllExcept(adminUserId);

        settingsClient.cleanDataServiceDB(adminCompanyId);
        authClient.cleanAuthServiceDB(adminUserId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static Instant periodStart(String period) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return switch (period) {
            case "TODAY" -> today.atStartOfDay().toInstant(ZoneOffset.UTC);
            case "WEEK"  -> today.with(DayOfWeek.MONDAY).atStartOfDay().toInstant(ZoneOffset.UTC);
            default      -> today.with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay().toInstant(ZoneOffset.UTC);
        };
    }

    private void deleteCompanyCascade(UUID orgId, UUID companyId) {
        // Own orders
        // SETTINGS orderMessageRepository.deleteByCompanyId(companyId);
        // SETTINGS orderRepository.deleteEventsByCompanyId(companyId);
        // SETTINGS orderRepository.deleteByCompanyId(companyId);
        // Cross-company orders whose origin unit belongs to this company (origin is NOT NULL)
         // SETTINGS orderMessageRepository.deleteByOriginCompanyId(companyId);
         // SETTINGS orderRepository.deleteEventsByOriginCompanyId(companyId);
         // SETTINGS orderRepository.deleteByOriginCompanyId(companyId);
        // Null out destinations pointing to this company's units (destination is nullable)
         // SETTINGS  orderRepository.nullifyDestinationByCompanyId(companyId);
        loyalUserCompanyRepository.deleteByCompanyId(companyId);
        apiKeyRepository.deleteByCompanyId(companyId);
        // SETTINGS workerRepository.deleteUnitWorkersByCompanyId(companyId);
        // SETTINGS unitRepository.deleteByCompanyId(companyId);
        Set<UUID> userIds = workerRepository.findAccountsToDelete(orgId, companyId);
        workerRepository.deleteByCompanyId(companyId);
        userRepository.deleteByUserIds(userIds);
        settingsClient.deleteAllByCompany(companyId, true);
        authClient.deleteUsers(userIds);
    }
}
