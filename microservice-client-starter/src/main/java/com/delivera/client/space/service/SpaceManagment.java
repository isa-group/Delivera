package com.delivera.client.space.service;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;

import com.delivera.client.exception.ServerException;
import com.delivera.client.space.exception.SpaceValidationException;

import io.github.isagroup.spaceclient.SpaceClient;
import io.github.isagroup.spaceclient.types.FeatureEvaluationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class SpaceManagment {

    private final SpaceClient spaceClient;

    @Value("${app.space.service:delivera}")
    private String service;

    private boolean defaultFetchDetails = true;

    /**
     * @apiNote To use this operation without problems you required a SPACE API-KEY of type "EVALUATE"
     * @param userId
     * @param feature
     * @param expectedConsumption
     * @param details
     * @param server
     * @param resource
     */
    public void genericFeatureConsumption(
        String userId, 
        String feature, 
        Map<String, Number> expectedConsumption,
        Boolean details,
        Boolean server,
        String resource
    ) {
        String featureId = servicePrefix(feature);

        FeatureEvaluationResult withConsumption = spaceClient.features.evaluate(
            userId, 
            featureId,
            expectedConsumption,
            details,
            server
        );
        boolean isError = withConsumption.getError()!= null;
        boolean unexistingFeature = isError 
            && withConsumption.getError().getMessage().equals("FEATURE_NOT_FOUND");

        if (!isError && !withConsumption.getEval()) {
            spaceClient.features.revertEvaluation(userId, featureId, true);
            throw new SpaceValidationException(resource);
        }else if (unexistingFeature){
            log.warn("FEATURE_NOT_FOUND: {} is not in the pricing",withConsumption.getError());
        } else if (isError) {
            log.info("FEATURE_ERROR: {}",withConsumption.getError());
            throw new ServerException(503, "SERVICE_UNAVAILABLE");
        }
    }

    /**
     * 
     * @apiNote To use this operation without problems you required a SPACE API-KEY of type "MANAGMENT".
     * @param userId 
     * @param feature
     * @param quantity
     * @param server
     */
    public void executeConsumptionWithMaxLimit(
        String userId, 
        String feature, 
        Integer quantity, 
        Boolean server
    ){
        String maximunStr = maxOfFeature(feature);
        genericFeatureConsumption(
            userId,
            feature,
            addServicePrefix(Map.of(maximunStr, quantity)),
            defaultFetchDetails,
            server,
            feature
        );


    }

    private String servicePrefix(String feature) {
        return service+"-"+feature;
    }

    private String maxOfFeature(String feature) {
        return "max"+ 
            feature.substring(0,1).toUpperCase()+
            feature.substring(1);

    }

    private Map<String,Number> addServicePrefix(Map<String,Number> expectedConsumption) {
        return expectedConsumption
                .entrySet()
                .stream()
                .collect(
                    Collectors.toMap(
                        entry -> servicePrefix(entry.getKey()), 
                        entry -> entry.getValue()
                    )
                );
    }
}

