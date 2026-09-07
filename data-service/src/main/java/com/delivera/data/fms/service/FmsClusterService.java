package com.delivera.data.fms.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.delivera.data.fms.dto.Cluster;
import com.delivera.data.fms.dto.ClusterConfig;
import com.delivera.data.fms.dto.CustomerDto;
import com.delivera.data.fms.dto.DbscanResult;
import com.delivera.data.fms.dto.DepotDto;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FmsClusterService {


    public DbscanResult clusterClients(
        ClusterConfig config,
        List<CustomerDto> customers,
        List<DepotDto> depots, 
        double[][] distanceMatrix
    ){
        long init = System.currentTimeMillis();

        DbscanResult result =  executeDBSCAN(
            config,
            customers,
            depots,
            distanceMatrix
        );

        result = silenceNoise(config, result, distanceMatrix);

        result = subdivideByLimit(config, result, distanceMatrix);

        closeClusters(result);

        result = assignDepots(config, result, depots, distanceMatrix);

        result.setDepots(depots);

        result.setConfig(config);

        result.setComputationTimeMs(System.currentTimeMillis() - init);

        System.out.println("CLUSTER EXECUTION TIME IS: "+(result.getComputationTimeMs())+" ms");

        return result;

    }

    public void closeClusters(DbscanResult dbscanResult) {
        Integer indx = 0;
        for (Cluster cluster: dbscanResult.getClusters()) {
            cluster.setId(indx);
            cluster.close();
            indx += 1;
        }
    }

    public DbscanResult subdivideByLimit(
        ClusterConfig config,
        DbscanResult dbscanResult,
        double[][] distanceMatrix
    ){
        List<Cluster> clusters = dbscanResult.getClusters();
        List<Cluster> finalClusters = new ArrayList<>();
        for (Cluster cluster:clusters) {
            if (cluster.totalClients() > config.getMaxClusterSize()) {
                finalClusters.addAll(
                    subdivideCluster(config, cluster, distanceMatrix)
                );
            } else {
                finalClusters.add(cluster);
            }
        }
        return DbscanResult.builder().clusters(finalClusters).build();

    }

    public Map<String,CustomerDto> getEdges(Cluster cluster,double[][] distanceMatrix) {
        CustomerDto left = null;
        CustomerDto right = null;
        double maxDistance = Double.NEGATIVE_INFINITY;
        for (CustomerDto customer: cluster.getCustomers()) {
            for (CustomerDto other: cluster.getCustomers()) {
                if (customer.equals(other)) {
                    continue;
                }
                double distance = distanceMatrix[customer.matrixIndex()][other.matrixIndex()];
                if (distance > maxDistance) {
                    maxDistance = distance;
                    left = customer;
                    right = other;
                }
            }
        }
        return Map.of(
            "LEFT", left,
            "RIGHT", right
        );

    }

    public List<Cluster> subdivideCluster(
        ClusterConfig config, 
        Cluster cluster, 
        double[][] distanceMatrix
    ) {
        if (cluster.totalClients() == 1) {
            return List.of(cluster);
        }
        
        Map<String,CustomerDto> edges = getEdges(cluster, distanceMatrix);
        List<Cluster> newClusters = new ArrayList<>();
        CustomerDto left = edges.get("LEFT");
        CustomerDto right = edges.get("RIGHT");
        Cluster clusterLeft = Cluster.of();
        Cluster clusterRight = Cluster.of();
        clusterLeft.add(left);
        clusterRight.add(right);
        newClusters.add(clusterLeft);
        newClusters.add(clusterRight);

        for (CustomerDto customer: cluster.getCustomers()) {
            if (customer.equals(left) || customer.equals(right)) {
                continue;
            }
            double distanceLeft = distanceMatrix[customer.matrixIndex()][left.matrixIndex()];
            double distanceRight = distanceMatrix[customer.matrixIndex()][right.matrixIndex()];
            if (distanceRight  > distanceLeft) {
                clusterLeft.add(customer);
            } else if (distanceLeft > distanceRight) {
                clusterRight.add(customer);   
            } else {
                if (clusterLeft.totalClients() > clusterRight.totalClients()) {
                    clusterRight.add(customer);
                }else {
                    clusterLeft.add(customer);
                }
            }
        }
        List<Cluster> clusters = new ArrayList<>();
        for (Cluster newCluster:newClusters) {
            if (newCluster.totalClients() > config.getMaxClusterSize()) {
                clusters.addAll(
                    subdivideCluster(config, newCluster, distanceMatrix)
                );
            } else {
                clusters.add(newCluster);
            }
        }

        return clusters;
    }


    public DbscanResult assignDepots(
        ClusterConfig config,
        DbscanResult dbscanResult,
        List<DepotDto> depots, 
        double[][] distanceMatrix
    ){
        List<Cluster> clusters = dbscanResult.getClusters();
        for (Cluster cluster:clusters) {
            Set<Integer> possibles = new HashSet<>();
            for (DepotDto depot:depots) {
                double clusterDistance = Haversine.distanceKm(
                    cluster.lat(), cluster.lng(), 
                    depot.getLat(), depot.getLng()
                );
                if (clusterDistance <= config.getMaxDepotRadiusKm()){
                    possibles.add(depot.matrixIndex());
                }
            }
            cluster.setDepots(possibles);
        }

        return DbscanResult.builder().clusters(clusters).build();

    }


    public DbscanResult executeDBSCAN(
        ClusterConfig config,
        List<CustomerDto> customers,
        List<DepotDto> depots, 
        double[][] distanceMatrix
    ){
        Set<Integer> visited = new HashSet<>();

        List<Cluster> clusters = new ArrayList<>();

        List<CustomerDto> noise = new ArrayList<>();

        for (CustomerDto customer : customers) {

            if (visited.contains(customer.matrixIndex())) {
                continue;
            }
        
            visited.add(customer.matrixIndex());
        
            List<CustomerDto> neighbors = getNeighbors(
                config.getMaxRadiusKm(),
                customer,
                customers,
                distanceMatrix
            );
        
            if (neighbors.size() < config.getMinClusterSize()) {
                noise.add(customer);
                continue;
        
            }
        
            Cluster cluster = Cluster.of();
        
            expandCluster(
                config,
                cluster,
                customer,
                customers,
                neighbors,
                visited,
                distanceMatrix
            );

            clusters.add(cluster);
        }

        return DbscanResult.builder().clusters(clusters).noise(noise).build();

    }

    public List<CustomerDto> getNeighbors(
        double radiusKm, 
        CustomerDto customer,
        List<CustomerDto> customers, 
        double[][] distanceMatrix
    ) {

        List<CustomerDto> neighbors =
            new ArrayList<>();

        for (CustomerDto other : customers) {
            if (other.matrixIndex() == customer.matrixIndex()) {
                continue;
            }
            double distance =
                distanceMatrix[customer.matrixIndex()][other.matrixIndex()];

            if (distance <= radiusKm) {
                neighbors.add(other);
            }
        }

        return neighbors;
    }

    public void expandCluster(
        ClusterConfig config,
        Cluster cluster,
        CustomerDto customer,
        List<CustomerDto> customers,
        List<CustomerDto> neighbors,
        Set<Integer> visited,
        double[][] distanceMatrix
    ) {

        cluster.add(customer);

        Queue<CustomerDto> queue =
            new LinkedList<>(neighbors);

        while (!queue.isEmpty()) {

            CustomerDto current = queue.poll();

            if (!visited.contains(current.matrixIndex())) {

                visited.add(current.matrixIndex());

                List<CustomerDto> currentNeighbors =
                    getNeighbors(
                        config.getMaxRadiusKm(),
                        current,
                        customers, 
                        distanceMatrix 
                    );

                if (currentNeighbors.size() >= config.getMinClusterSize()) {
                    queue.addAll(currentNeighbors);
                }
            }

            if (!cluster.contains(current)) {
                cluster.add(current);

            }
        }
    }


    public DbscanResult silenceNoise(
        ClusterConfig config,
        DbscanResult dbscanResult,
        double[][] distanceMatrix
    ) {
        List<CustomerDto> noise = dbscanResult.getNoise();
        List<Cluster> clusters = dbscanResult.getClusters();
        List<CustomerDto> newNoise = new ArrayList<>();
        for (CustomerDto customer:noise) {
            Cluster bestCluster = null;
            double bestDistance = Double.MAX_VALUE;

            for (Cluster cluster:clusters) {
                double clusterDistance = Haversine.distanceKm(
                    cluster.lat(), cluster.lng(), 
                    customer.getLat(), customer.getLng()
                );
                if (clusterDistance <= config.getNoiseClusterMaxDistanceKm()) {
                    double minDistanace = Double.MAX_VALUE;
                    for (CustomerDto other :cluster.getCustomers()) {
                        double customerDistance  = distanceMatrix[customer.matrixIndex()][other.matrixIndex()];
                        if (customerDistance <= config.getNoiseMaxDistanceKm()) {
                            minDistanace = Math.min(minDistanace, customerDistance);
                        }
                    }
                    if (bestDistance > minDistanace) {
                        bestDistance = minDistanace;
                        bestCluster = cluster;
                    }
                }
            }
            if (bestCluster != null) {
                bestCluster.add(customer);
            } else {
                newNoise.add(customer);
            }
        }
        for (CustomerDto customer: newNoise) {
            Cluster cluster = Cluster.of();
            cluster.add(customer);
            clusters.add(cluster);
        }

        return DbscanResult.builder().clusters(clusters).noise(List.of()).build();
    }


}
