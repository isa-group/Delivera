package com.delivera.dto.config;

import com.delivera.worker.model.WorkerRoleConfig;

public record WorkerRoleConfigDto(
        String role,
        boolean canCreateOrders,
        boolean canUpdateOrderStatus,
        boolean canManageUnits,
        boolean canManageLoyalUsers,
        boolean canManageSettings
) {
    public static WorkerRoleConfigDto from(WorkerRoleConfig c) {
        return new WorkerRoleConfigDto(
                c.getRole(), c.isCanCreateOrders(), c.isCanUpdateOrderStatus(),
                c.isCanManageUnits(), c.isCanManageLoyalUsers(), c.isCanManageSettings()
        );
    }
}
