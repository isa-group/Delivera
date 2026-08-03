package com.delivera.data.fms.service;

import java.util.UUID;

import com.delivera.data.fms.dto.RoutingResponse;
import com.delivera.data.fms.dto.TypeSolver;

public interface FmsRoutingService {

    RoutingResponse solveForCompany(UUID companyId, TypeSolver solverType);
}
