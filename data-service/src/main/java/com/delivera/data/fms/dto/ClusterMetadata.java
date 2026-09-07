package com.delivera.data.fms.dto;

import java.util.List;

import com.delivera.data.fms.service.Haversine;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
class ClusterMetadata {

    double centroidLat = 0;
    double centroidLng = 0;

    double maxDistanceToCentroid = 0;
    double avgDistanceToCentroid = 0;

    double maxNearestNeighborDistance = 0;
    double avgNearestNeighborDistance = 0;

    int customerCount = 0;
    int totalDemand = 0;

    double minLat = Double.MAX_VALUE;
    double maxLat = Double.NEGATIVE_INFINITY;

    double minLng = Double.MAX_VALUE;
    double maxLng = Double.NEGATIVE_INFINITY;

    double area = 0;

    double density = 0;

    double cohesion = 0;

    double coverageAreaKm2 = 0;
    double coverageRadiusKm = 0;

    double minDemand = Double.MAX_VALUE;

    public static ClusterMetadata of(){
        return new ClusterMetadata();
    }

    private void updateDemand(int demand) {
        totalDemand += demand;
        if (minDemand > demand) {
            minDemand = demand;
        }
    }
    private void addCustomer() {
        customerCount += 1;
    }

    private void updateBoundingBox(double lat, double lng) {
        if (minLng > lng) {
            minLng = lng;
        }

        if (minLat > lat) {
            minLat = lat;
        }

        if (maxLng < lng) {
            maxLng = lng;
        }

        if (maxLat < lat) {
            maxLat = lat;
        }
    }


    private void updateCentroid(double lat, double lng) {
        centroidLat = centroidLat + (lat - centroidLat)/customerCount;
        centroidLng = centroidLng +(lng - centroidLng)/customerCount; 

    }

    private void updateArea() {
        Double widthKm = Haversine.distanceKm(
            centroidLat,
            minLng,
            centroidLat,
            maxLng
        );

        Double heightKm = Haversine.distanceKm(
            minLat,
            centroidLng,
            maxLat,
            centroidLng
        );

        area = widthKm * heightKm;
    }

    private void updateDensity() {
        if (customerCount <= 1) {
            density = 0;
            return;
        }
        density = customerCount / area;
    }

    private void updateMetricsWithCustomers(List<CustomerDto> customers) {
    
        double totalDistanceToCentroid = 0;
        double totalNearestNeighborDistance = 0;
    
        maxDistanceToCentroid = 0;
        maxNearestNeighborDistance = 0;

        if (customerCount <= 1) {

            density = 0;
        
            cohesion = 1;
        
            coverageRadiusKm = 0;
        
            coverageAreaKm2 = 0;
        
            return;
        }
    
        for (CustomerDto customer : customers) {
    
            double distanceToCentroid =
                Haversine.distanceKm(
                    centroidLat,
                    centroidLng,
                    customer.lat(),
                    customer.lng()
                );
    
            totalDistanceToCentroid +=
                distanceToCentroid;
    
            maxDistanceToCentroid =
                Math.max(
                    maxDistanceToCentroid,
                    distanceToCentroid
                );
    
            double nearestDistance = Double.MAX_VALUE;
    
            for (CustomerDto other : customers) {
    
                if (customer.matrixIndex() == other.matrixIndex()) {
                    continue;
                }
    
                double distance = Haversine.distanceKm(
                    customer.lat(),
                    customer.lng(),
                    other.lat(),
                    other.lng()
                );
    
                nearestDistance = Math.min(
                    nearestDistance,
                    distance
                );
            }
    
            if (nearestDistance != Double.MAX_VALUE) {
    
                totalNearestNeighborDistance += nearestDistance;
    
                maxNearestNeighborDistance = Math.max(
                    maxNearestNeighborDistance,
                    nearestDistance
                );
            }
        }
    
        avgDistanceToCentroid = totalDistanceToCentroid/ customerCount;
    
        avgNearestNeighborDistance = totalNearestNeighborDistance/ customerCount;

        cohesion = avgDistanceToCentroid / maxDistanceToCentroid;
        coverageRadiusKm = maxDistanceToCentroid;
        coverageAreaKm2 =Math.PI * maxDistanceToCentroid * maxDistanceToCentroid;
    }

    public void close(List<CustomerDto> customers) {
        updateMetricsWithCustomers(customers);
        updateArea();
        updateDensity();
    }

    public void newCustomer(CustomerDto customer) {
        updateDemand(customer.demand());
        addCustomer();
        updateCentroid(customer.lat(), customer.lng());
        updateBoundingBox(customer.lat(), customer.lng());

    }

    
}