package com.delivera.service;

import com.delivera.dto.config.*;
import com.delivera.exception.InvalidStatusTransitionException;
import com.delivera.order.model.OrderStatusConfig;
import com.delivera.order.repository.OrderPriorityConfigRepository;
import com.delivera.order.repository.OrderStatusConfigRepository;
import com.delivera.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AppConfigService {

    private final OrderStatusConfigRepository statusConfigRepository;
    private final OrderPriorityConfigRepository priorityConfigRepository;
    private final WorkerRoleConfigRepository roleConfigRepository;
    private final long fileMaxUploadBytes;

    public AppConfigService(OrderStatusConfigRepository statusConfigRepository,
                            OrderPriorityConfigRepository priorityConfigRepository,
                            WorkerRoleConfigRepository roleConfigRepository,
                            @Value("${app.file.max-upload-bytes:2097152}") long fileMaxUploadBytes) {
        this.statusConfigRepository = statusConfigRepository;
        this.priorityConfigRepository = priorityConfigRepository;
        this.roleConfigRepository = roleConfigRepository;
        this.fileMaxUploadBytes = fileMaxUploadBytes;
    }

    public long getFileMaxUploadBytes() {
        return fileMaxUploadBytes;
    }

    /**
     * Valida que el contenido base64 (data URL incluido) no supere el límite.
     * Una cadena base64 de N caracteres representa ~0.75 N bytes binarios.
     */
    public void checkUploadSize(String base64Data) {
        if (base64Data == null) return;
        long approxBytes = (long) (base64Data.length() * 0.75);
        if (approxBytes > fileMaxUploadBytes) {
            throw new com.delivera.exception.FileTooLargeException();
        }
    }

    @Transactional(readOnly = true)
    public AppConfigResponse getConfig() {
        List<OrderStatusConfigDto> statuses = statusConfigRepository.findAllByOrderBySortOrderAsc()
                .stream().map(OrderStatusConfigDto::from).toList();
        List<OrderPriorityConfigDto> priorities = priorityConfigRepository.findAllByOrderBySortOrderAsc()
                .stream().map(OrderPriorityConfigDto::from).toList();
        List<WorkerRoleConfigDto> roles = roleConfigRepository.findAll()
                .stream().map(WorkerRoleConfigDto::from).toList();
        return new AppConfigResponse(statuses, priorities, roles, fileMaxUploadBytes);
    }

    @Transactional(readOnly = true)
    public void validateTransition(String current, String next) {
        OrderStatusConfig config = statusConfigRepository.findById(current)
                .orElseThrow(InvalidStatusTransitionException::new);
        if (config.isTerminal() || !config.getAllowedTransitionsList().contains(next)) {
            throw new InvalidStatusTransitionException();
        }
    }
}
