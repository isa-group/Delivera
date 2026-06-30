package com.delivera.fms.service;

import com.delivera.fms.dto.RoutingResponse;

import java.util.UUID;

public interface FmsRoutingService {

    RoutingResponse solveForCompany(UUID companyId);
}
