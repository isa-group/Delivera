package com.delivera.fms.routing.instance.controller;

import com.delivera.fms.routing.dto.RoutingResponse;
import com.delivera.fms.routing.dto.TypeSolver;
import com.delivera.fms.routing.instance.client.StandardInstanceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/v1/fms/instances")
public class StandardInstanceController {

    private final StandardInstanceClient client;
    private final String instancesDir;

    public StandardInstanceController(
            StandardInstanceClient client,
            @Value("${fms.instances.dir:instances-MD-CVRP}") String instancesDir) {
        this.client = client;
        this.instancesDir = instancesDir;
    }

    @PostMapping("/send")
    public ResponseEntity<RoutingResponse> sendInstance(
            @RequestParam String fileName,
            @RequestParam(defaultValue = "GREEDY") TypeSolver solverType) throws IOException {
        Path filePath = Paths.get(instancesDir, fileName);
        RoutingResponse response = client.sendInstance(filePath, solverType);
        return ResponseEntity.ok(response);
    }
}
