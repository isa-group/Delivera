package com.delivera.fms.routing.service;

import com.delivera.fms.routing.dto.RoutingRequest;
import com.delivera.fms.routing.dto.RoutingResponse;

public interface RouteSolver {

    RoutingResponse solve(RoutingRequest request);

    String getSolverId();
}
