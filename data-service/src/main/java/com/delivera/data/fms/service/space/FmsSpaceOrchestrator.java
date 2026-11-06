package com.delivera.data.fms.service.space;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import com.delivera.client.core.SmartMicroserviceClient;
import com.delivera.client.exception.ServerException;
import com.delivera.client.space.exception.SpaceValidationException;
import com.delivera.client.space.service.SpaceManagment;

import io.github.isagroup.spaceclient.SpaceClient;
import io.github.isagroup.spaceclient.types.Contract;
import io.github.isagroup.spaceclient.types.FeatureEvaluationResult;
import io.github.isagroup.spaceclient.types.UsageLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FmsSpaceOrchestrator {

    // Clients 

    private final SpaceClient spaceClient;

    private final SpaceManagment spaceManagment;

    private final SmartMicroserviceClient client;

    // SOLVERS
    private final SpaceAnnealingEngine annealing;

    private final SpaceGeneticEngine genetic;

    private final SpaceGreedyEngine greedy;

    private final SpaceDwaveEngine dwave;

    private final SpaceQiskitEngine qiskit;

    private final SpaceHgsEngine hgs;


    @Value("${app.space.service:delivera}")
    private String service;



    public Set<String> geUserSolvers(String orgId) {
        Contract contract = spaceClient.contracts.getContract(orgId);

        Set<String> solvers = contract.getSubscriptionAddOns().get(service)
        .entrySet().stream()
        .filter(kv -> kv.getValue() > 0)
        .map(
            kv -> kv.getKey().replace("SolverAvailability","")
        ).collect(Collectors.toSet());
        getUsageLimitByFeature(orgId, "annealingSolverExecutions");
        return solvers;
    }



    public FeatureEvaluationResult getUsageLimitByFeature(String orgId, String feature) {
        Double quantity = 0d;
        String maximunStr = spaceManagment.maxOfFeature(feature);
        FeatureEvaluationResult evaluation = spaceManagment.genericFeatureConsumptionWithEvaluation(
            orgId, feature, 
            spaceManagment.addServicePrefix(Map.of(maximunStr, quantity)),
            false,
            false,
            feature
            
        );

        return evaluation;
    }




}
