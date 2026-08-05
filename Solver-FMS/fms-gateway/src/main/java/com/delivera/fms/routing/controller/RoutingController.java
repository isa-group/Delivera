package com.delivera.fms.routing.controller;

import com.delivera.fms.routing.config.OpenApiExamples;
import com.delivera.fms.routing.dto.DepotDto;
import com.delivera.fms.routing.dto.RoutingRequest;
import com.delivera.fms.routing.dto.RoutingResponse;
import com.delivera.fms.routing.service.EngineDispatcher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/fms/routing")
@Tag(name = "Routing", description = "Resolucion de rutas de vehiculos")
public class RoutingController {

    private final EngineDispatcher engineDispatcher;

    public RoutingController(EngineDispatcher engineDispatcher) {
        this.engineDispatcher = engineDispatcher;
    }

    @Operation(summary = "Resolver problema de ruteo",
            description = "Resuelve un problema de ruteo de vehiculos con capacidad para multiples almacenes (MD-CVRP) " +
                    "utilizando el solver especificado en la solicitud. " +
                    "Devuelve las rutas optimizadas con sus vehiculos, paradas, distancias y cargas. " +
                    "Los parametros del solver que no se envien se completan con los valores por defecto " +
                    "declarados en sus metadatos.")
    @ApiResponse(responseCode = "200", description = "Problema resuelto correctamente",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = RoutingResponse.class),
                    examples = @ExampleObject(
                            name = "Solucion MD-CVRP (instancia p01)",
                            description = "Resolucion real de la instancia p01 (4 depositos, 50 clientes) con el solver GREEDY",
                            value = OpenApiExamples.ROUTING_RESPONSE)))
    @PostMapping("/solve")
    public ResponseEntity<RoutingResponse> solve(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RoutingRequest.class),
                            examples = {
                                    @ExampleObject(
                                            name = "MD-CVRP (2 depositos, 5 clientes)",
                                            description = "Instancia lista para ejecutar: matriz 7x7 consistente con los indices de los nodos",
                                            value = OpenApiExamples.SOLVE_REQUEST),
                                    @ExampleObject(
                                            name = "MD-CVRP con parametros del solver",
                                            description = "Misma instancia con duracion maxima y tiempos de servicio, resuelta por el motor genetico con parametros propios",
                                            value = OpenApiExamples.SOLVE_REQUEST_TUNED)}))
            @Valid @RequestBody RoutingRequest request) {
        validateConsistency(request);
        RoutingResponse response = engineDispatcher.dispatch(request);
        return ResponseEntity.ok(response);
    }

    private void validateConsistency(RoutingRequest request) {
        int expectedSize = request.depots().size() + request.customers().size();
        double[][] matrix = request.distanceMatrix();

        if (matrix.length != expectedSize) {
            throw new IllegalArgumentException(
                    "Distance matrix rows (" + matrix.length + ") must equal depots + customers (" + expectedSize + ")");
        }
        for (int i = 0; i < matrix.length; i++) {
            if (matrix[i].length != expectedSize) {
                throw new IllegalArgumentException(
                        "Distance matrix column " + i + " size (" + matrix[i].length + ") must equal " + expectedSize);
            }
        }

        Set<Integer> indices = request.depots().stream()
                .map(DepotDto::matrixIndex)
                .collect(Collectors.toSet());
        for (var c : request.customers()) {
            if (!indices.add(c.matrixIndex())) {
                throw new IllegalArgumentException("Duplicate matrixIndex found: " + c.matrixIndex());
            }
        }
        for (int idx : indices) {
            if (idx < 0 || idx >= expectedSize) {
                throw new IllegalArgumentException("matrixIndex out of range: " + idx);
            }
        }

        if (request.vehicles() != null) {
            Set<String> depotIds = request.depots().stream()
                    .map(d -> d.id())
                    .collect(Collectors.toSet());
            for (var v : request.vehicles()) {
                if (!depotIds.contains(v.startDepotId())) {
                    throw new IllegalArgumentException("Vehicle " + v.id() + " references unknown depot: " + v.startDepotId());
                }
            }
        }
    }
}
