package com.delivera.service;

import com.delivera.order.model.OrderStatusConfig;
import com.delivera.order.repository.OrderPriorityConfigRepository;
import com.delivera.order.repository.OrderStatusConfigRepository;
import com.delivera.repository.WorkerRoleConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.delivera.exception.InvalidStatusTransitionException;

@ExtendWith(MockitoExtension.class)
class AppConfigServiceTest {

    @Mock
    private OrderStatusConfigRepository statusConfigRepository;
    @Mock
    private OrderPriorityConfigRepository priorityConfigRepository;
    @Mock
    private WorkerRoleConfigRepository roleConfigRepository;

    private AppConfigService appConfigService;

    private OrderStatusConfig pendingConfig;

    @BeforeEach
    void setUp() {
        appConfigService = new AppConfigService(statusConfigRepository, priorityConfigRepository, roleConfigRepository, 2097152L);
        pendingConfig = new OrderStatusConfig();
        ReflectionTestUtils.setField(pendingConfig, "status", "PENDING");
        ReflectionTestUtils.setField(pendingConfig, "uiSeverity", "warn");
        ReflectionTestUtils.setField(pendingConfig, "allowedTransitions", "IN_TRANSIT,CANCELLED");
        ReflectionTestUtils.setField(pendingConfig, "terminal", false);
        ReflectionTestUtils.setField(pendingConfig, "sortOrder", 1);
    }

    @Test
    void getConfig_returnsResponse() {
        org.mockito.Mockito.when(statusConfigRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(pendingConfig));
        org.mockito.Mockito.when(priorityConfigRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of());
        org.mockito.Mockito.when(roleConfigRepository.findAll()).thenReturn(List.of());

        var result = appConfigService.getConfig();
        assertThat(result.orderStatuses()).hasSize(1);
        assertThat(result.orderPriorities()).isEmpty();
    }

    @Test
    void validateTransition_validTransition_doesNotThrow() {
        org.mockito.Mockito.when(statusConfigRepository.findById("PENDING")).thenReturn(Optional.of(pendingConfig));
        appConfigService.validateTransition("PENDING", "IN_TRANSIT");
    }

    @Test
    void validateTransition_invalidNext_throws() {
        org.mockito.Mockito.when(statusConfigRepository.findById("PENDING")).thenReturn(Optional.of(pendingConfig));
        assertThatThrownBy(() -> appConfigService.validateTransition("PENDING", "DELIVERED"))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void validateTransition_unknownStatus_throws() {
        org.mockito.Mockito.when(statusConfigRepository.findById("UNKNOWN")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> appConfigService.validateTransition("UNKNOWN", "ANY"))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void getFileMaxUploadBytes_returnsConfiguredValue() {
        assertThat(appConfigService.getFileMaxUploadBytes()).isEqualTo(2097152L);
    }

    @Test
    void checkUploadSize_acceptsNullAndSmallPayload() {
        appConfigService.checkUploadSize(null);
        appConfigService.checkUploadSize("data:image/png;base64,YWJj"); // ~3 bytes
    }

    @Test
    void checkUploadSize_throwsWhenExceedsLimit() {
        // 3 MB de base64 ≈ 2.25 MB binarios → debe superar el límite de 2 MB
        String payload = "x".repeat(3 * 1024 * 1024);
        assertThatThrownBy(() -> appConfigService.checkUploadSize(payload))
                .isInstanceOf(com.delivera.exception.FileTooLargeException.class);
    }
}
