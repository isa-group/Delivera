package com.delivera.fms.service;

import com.delivera.fms.dto.RoutingResponse;
import com.delivera.fms.dto.TypeSolver;

import java.util.UUID;

public interface FmsRoutingService {

    RoutingResponse solveForCompany(UUID companyId, TypeSolver solverType);
}
