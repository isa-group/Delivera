package com.delivera.space.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import com.delivera.client.core.SmartMicroserviceClient;
import com.delivera.exception.ContractUpdateException;
import com.delivera.exception.ForbiddenException;
import com.delivera.model.User;
import com.delivera.org.model.Organization;
import com.delivera.space.dto.ChangeAddOn;
import com.delivera.space.dto.ContractChange;
import com.delivera.space.dto.ServicePricing;

import io.github.isagroup.spaceclient.SpaceClient;
import io.github.isagroup.spaceclient.types.BillingPeriod;
import io.github.isagroup.spaceclient.types.Contract;
import io.github.isagroup.spaceclient.types.ContractToCreate;
import io.github.isagroup.spaceclient.types.Subscription;
import io.github.isagroup.spaceclient.types.ContractToCreate.BillingPeriodToCreate;
import io.github.isagroup.spaceclient.types.UserContact;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpaceContracts {

    private final SpaceClient spaceClient;
    private final SmartMicroserviceClient httpClient;

    private final Integer RETRIRES = 3;
    private final Integer TIMEOUT = 5000;

    @Value("${app.space.service:delivera}")
    private String service;

    @Value("${app.space.version:1.0.0}")
    private String version;

    @Value("${app.space.basic-plan:MICRO}")
    private String basicPlan;

    @Value("${space.client.url}")
    private String baseUrl;

    /**
     * @apiNote To use this operation without problems you required a SPACE API-KEY of type "MANAGMENT"
     * @param user
     * @param organization
     */
    public void createBasicContract(User user,Organization organization) {
        createContract(user, organization, basicPlan);
    }

   /**
    * @apiNote To use this operation without problems you required a SPACE API-KEY of type "MANAGMENT"
    * @param user
    * @param organization
    * @param plan
    */
    public void createContract(User user,Organization organization, String plan) {
        UserContact userContact = new UserContact(
            organization.getId().toString(), 
            organization.getHandle(), 
            organization.getName(), 
            user.getFirstName() + " "+ user.getLastName(), 
            user.getEmail(),
            user.getPhone()
        );

        BillingPeriodToCreate billingPeriod = new BillingPeriodToCreate(true, 30);

        Map<String, String> contractedServices = new HashMap<>();
		contractedServices.put(service, version);

        Map<String, String> subscriptionPlans = new HashMap<>();
		subscriptionPlans.put(service, plan);




        ContractToCreate contractToCreate = new ContractToCreate(
            userContact,
            billingPeriod,
            contractedServices,
            organization.getId().toString(),
            subscriptionPlans,
            new HashMap<>()
        );

        this.spaceClient.contracts.addContract(contractToCreate);

    }

    /**
     * @apiNote To use this operation without problems you required a SPACE API-KEY of type "ALL"
     * @param userId
     */
    public void removeContract(String userId) {
        if (userId == null) {
            log.warn("REMOVE CONTRACT METHOD HAS RECEIVED A NULL userId");
            return;
        }
        this.spaceClient.contracts.removeContract(userId.toString());
    }

    /**
     * @apiNote To use this operation without problems you required a SPACE API-KEY of type "ALL"
     * @param userId
     */
    public void removeAllContracts() {
        String url = this.spaceClient.getHttpUrl() + "/contracts";
        String apiKey = this.spaceClient.getApiKey();

        httpClient.request()
        .url(url)
        .header("x-api-key",apiKey)
        .method(HttpMethod.DELETE)
        .http()
        .internal()
        .failOn4xx(true)
        .failOn5xx(true)
        .retry(RETRIRES)
        .timeout(TIMEOUT)
        .log()
        .executeBasicRequest(Void.class).block();
    }

     /**
     * @apiNote To use this operation without problems you required a SPACE API-KEY of type "ALL"/"MANAGMENT"
     * @param userId
     */
    public void toggleAutoRenewContract(String userId) {
        Contract contract = spaceClient.contracts.getContract(userId);
        BillingPeriod billingPeriod = contract.getBillingPeriod();
        billingPeriod.setAutoRenew(!billingPeriod.isAutoRenew());

        String url = this.spaceClient.getHttpUrl() + "/contracts/"+userId+"/billingPeriod";
        String apiKey = this.spaceClient.getApiKey();

        httpClient.request()
        .url(url)
        .header("x-api-key",apiKey)
        .method(HttpMethod.PUT)
        .http()
        .body(billingPeriod)
        .internal()
        .failOn4xx(true)
        .failOn5xx(true)
        .retry(RETRIRES)
        .timeout(TIMEOUT)
        .log()
        .executeBasicRequest(Void.class).block();
    }


    public ServicePricing getCurrentPricing(){
        String url = this.spaceClient.getHttpUrl() + "/services/"+service+"/pricings/"+version;
        String apiKey = this.spaceClient.getApiKey();

        ServicePricing pricing = httpClient.request()
        .url(url)
        .header("x-api-key",apiKey)
        .method(HttpMethod.GET)
        .http()
        .failOn4xx(true)
        .failOn5xx(true)
        .retry(RETRIRES)
        .timeout(TIMEOUT)
        .log()
        .executeBasicRequest(ServicePricing.class).block();
        pricing.setYamlPath(baseUrl+pricing.getYamlPath());
        return pricing;

    }



    public Contract getContract(String userId){
        Contract contract = spaceClient.contracts.getContract(userId);
        return contract;
     

    }

    



    public Contract updateContract(String userId,ContractChange newContract ) {
        Contract contract = spaceClient.contracts.getContract(userId);
        Map<String, Map<String,Integer>> addOnsByService = contract.getSubscriptionAddOns();
        Map<String,ChangeAddOn> newAddOns = newContract.getNewAddOns();

        if (!contract.getContractedServices().containsKey(service)){
            throw new ForbiddenException("YOU DON'T HAVE THIS SERVICE");
        }


        Map<String,Integer> addOns =   addOnsByService.computeIfAbsent(
            service, (service) -> new HashMap<>()
        );
        updateAddOns(addOns, newAddOns);
        addOnsByService.put(service, addOns);

        contract.getSubscriptionPlans().put(service, newContract.getPlan());

        Subscription subscription = new Subscription(
            contract.getContractedServices(),
            contract.getSubscriptionPlans(),
            addOnsByService
        );
        Contract updatedContract = spaceClient.contracts.updateContractSubscription(userId, subscription);
        if (updatedContract == null) {
            throw new ContractUpdateException();
        }
        return updatedContract;
        
    }


    private void updateAddOns(Map<String,Integer> addOns,Map<String,ChangeAddOn> newAddOns) {
        for (var entry: newAddOns.entrySet()){
            String name = entry.getKey();
            ChangeAddOn change = entry.getValue();
            if (!change.isSelected()){
                addOns.remove(name);
            }else {
                addOns.put(name, change.getQuantity());
            }
        }
    }
}
