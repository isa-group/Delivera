package com.delivera.data.fms.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


import org.springframework.stereotype.Service;


import com.delivera.data.fms.dto.solver.info.SolverCatalog;
import com.delivera.data.fms.dto.solver.info.SolverStatus;
import com.delivera.data.fms.dto.solver.info.SolverSummary;
import com.delivera.data.fms.service.space.FmsSpaceOrchestrator;


import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;


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

    public List<SolverSummary> getAvailableSolvers(String orgId){
        boolean includeStatus = true;
        // TODO: concurrent calls?
        Set<String> userSolvers = fmsSpace.geUserSolvers(orgId);
        SolverCatalog catalog = solverClient.getSolverCatalog(includeStatus);
      

        return catalog.solvers().stream().map(solverInfo ->{
            SolverSummary summary = new SolverSummary();
            summary.setActive(solverInfo.status().equals(SolverStatus.UP));
            summary.setOwn(userSolvers.contains(solverInfo.type().toString().toLowerCase()));
            summary.setParameters(solverInfo.parameters());
            summary.setSolver(solverInfo.type());           
            return summary;
        }).toList();

    }



}
