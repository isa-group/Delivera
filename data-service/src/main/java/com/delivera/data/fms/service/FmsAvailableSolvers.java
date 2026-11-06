package com.delivera.data.fms.service;

import java.util.Set;
import java.util.stream.Collectors;


import org.springframework.stereotype.Service;


import com.delivera.data.fms.dto.solver.info.SolverCatalog;
import com.delivera.data.fms.dto.solver.info.SolverStatus;
import com.delivera.data.fms.service.space.FmsSpaceOrchestrator;


import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
public class FmsAvailableSolvers {

    
 
    private final SolverClient solverClient;

    private final FmsSpaceOrchestrator fmsSpace;


    public Set<String> getGatewaySolversAvailable() {
        boolean includeStatus = true;
        SolverCatalog catalog = solverClient.getSolverCatalog(includeStatus);
        Set<String> solvers = catalog.solvers().stream()
        .filter(solver -> solver.status().equals(SolverStatus.UP))
        .map(solver -> solver.type().toString().toLowerCase())
        .collect(Collectors.toSet());

        return solvers;
    }

    public Set<String> getAvailableUserSolvers(String orgId) {
        Set<String> userSolvers = fmsSpace.geUserSolvers(orgId);
        Set<String> gatewaySolvers = getGatewaySolversAvailable();
      
        userSolvers.retainAll(gatewaySolvers);

        return userSolvers;

    }




}
