package com.delivera.data.fms.service;

import java.util.UUID;

import com.delivera.data.fms.dto.RoutingResponse;

public interface FmsRoutingService {

    RoutingResponse solveForCompany(UUID companyId);
}
