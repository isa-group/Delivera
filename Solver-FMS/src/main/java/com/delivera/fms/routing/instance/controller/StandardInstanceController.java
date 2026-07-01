package com.delivera.fms.routing.instance.controller;

import com.delivera.fms.routing.dto.RoutingResponse;
import com.delivera.fms.routing.dto.TypeSolver;
import com.delivera.fms.routing.instance.client.StandardInstanceClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Instancias", description = "Carga y procesamiento de instancias de benchmark MD-CVRP")
public class StandardInstanceController {

    private final StandardInstanceClient client;
    private final String instancesDir;

    public StandardInstanceController(
            StandardInstanceClient client,
            @Value("${fms.instances.dir:instances-MD-CVRP}") String instancesDir) {
        this.client = client;
        this.instancesDir = instancesDir;
    }

    @Operation(summary = "Enviar instancia de benchmark al solver",
            description = "Carga un archivo de instancia MD-CVRP desde el directorio de instancias, " +
                    "lo parsea y lo envia al servicio de ruteo para su resolucion.")
    @PostMapping("/send")
    public ResponseEntity<RoutingResponse> sendInstance(
            @Parameter(description = "Nombre del archivo de instancia (ej. p01)", required = true)
            @RequestParam String fileName,
            @Parameter(description = "Tipo de solver a utilizar (RANDOM o GREEDY)")
            @RequestParam(defaultValue = "GREEDY") TypeSolver solverType) throws IOException {
        Path basePath = Paths.get(instancesDir).toAbsolutePath().normalize();
        Path filePath = basePath.resolve(fileName).normalize();
        if (!filePath.startsWith(basePath)) {
            throw new IllegalArgumentException("Invalid file name: " + fileName);
        }
        RoutingResponse response = client.sendInstance(filePath, solverType);
        return ResponseEntity.ok(response);
    }
}
