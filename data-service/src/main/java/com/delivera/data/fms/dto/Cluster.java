package com.delivera.data.fms.dto;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Cluster {

    private Integer id;

    private List<CustomerDto> customers = new ArrayList<>();

    private Set<Integer> depots = new HashSet<>();

    ClusterMetadata metadata = ClusterMetadata.of();

    public static Cluster of(){
        return new Cluster();
    }

    public void add(CustomerDto customer) {
        customers.add(customer);
        metadata.newCustomer(customer);
    }

    public boolean contains(CustomerDto customer) {
        return customers.contains(customer);
    }

    public void close() {
        metadata.close(customers);
    }

    public double lng() {
        return metadata.getCentroidLng();
    }

    public double lat() {
        return metadata.getCentroidLat();
    }

    public Integer totalClients() {
        return metadata.getCustomerCount();
    }

}
