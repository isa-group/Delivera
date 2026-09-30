package com.delivera.fms.routing.instance.controller;

import com.delivera.fms.routing.config.OpenApiExamples;
import com.delivera.fms.routing.dto.RoutingResponse;
import com.delivera.fms.routing.dto.TypeSolver;
import com.delivera.fms.routing.instance.client.StandardInstanceClient;
import com.delivera.fms.routing.instance.dto.InstanceCatalog;
import com.delivera.fms.routing.instance.dto.InstanceDetail;
import com.delivera.fms.routing.instance.service.StandardInstanceRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/fms/instances")
@Tag(name = "Instances", description = "Loading and solving of MD-CVRP benchmark instances")
public class StandardInstanceController {

    private final StandardInstanceClient client;
    private final StandardInstanceRegistry registry;

    public StandardInstanceController(StandardInstanceClient client, StandardInstanceRegistry registry) {
        this.client = client;
        this.registry = registry;
    }

    @Operation(summary = "List the available standard instances",
            description = "Returns the MD-CVRP instance bank mounted in the gateway, with the "
                    + "properties of each one: number of depots and customers, fleet, capacity, maximum "
                    + "route duration and total demand. They are the classic Cordeau instances, the same "
                    + "ones the benchmark uses. The catalogue is derived from the contents of the instance "
                    + "directory, so adding a file is enough for it to show up here. "
                    + "The 'name' field is the one to send as fileName when solving an instance.")
    @ApiResponse(responseCode = "200", description = "Instance catalogue",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = InstanceCatalog.class),
                    examples = @ExampleObject(
                            name = "Cordeau bank catalogue",
                            description = "Response trimmed to 3 of the 33 instances in the bank",
                            value = INSTANCE_CATALOG_EXAMPLE)))
    @GetMapping
    public ResponseEntity<InstanceCatalog> listInstances() throws IOException {
        return ResponseEntity.ok(InstanceCatalog.of(registry.findAll()));
    }

    @Operation(summary = "Get a specific instance",
            description = "Returns the given instance with all its nodes, already translated to the "
                    + "gateway model: not the file as is, but the depots, customers and vehicles that "
                    + "would be sent to the engine. The identifiers are the same ones that later appear "
                    + "in the solution routes, so a response from "
                    + "POST /api/v1/fms/instances/send can be read against this representation. "
                    + "The distance matrix is not included: it is computed from these coordinates "
                    + "when solving.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Instance found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = InstanceDetail.class),
                            examples = @ExampleObject(
                                    name = "Instance p01",
                                    description = "4 depots and 50 customers. The customer and vehicle "
                                            + "lists are shown trimmed",
                                    value = INSTANCE_DETAIL_EXAMPLE))),
            @ApiResponse(responseCode = "404",
                    description = "The instance is not in the instance directory",
                    content = @Content)
    })
    @GetMapping("/{name}")
    public ResponseEntity<InstanceDetail> getInstance(
            @Parameter(description = "Instance name, with or without extension (e.g. p01)",
                    required = true, example = "p01")
            @PathVariable String name) throws IOException {
        return ResponseEntity.ok(registry.findByName(name));
    }

    @Operation(summary = "Send a benchmark instance to the solver",
            description = "Loads an MD-CVRP instance file (JSON) from the instance directory, " +
                    "parses it and sends it to the corresponding routing engine to be solved. " +
                    "Accepts a map of solver parameters in the body: those not sent take their " +
                    "default value from the metadata, which allows running the same instance with " +
                    "different configurations of the same algorithm.",
            // Declarado en la operacion y no en el argumento: springdoc no genera
            // cuerpo para un parametro de tipo Map, que reserva para los query params.
            // Cualificado porque el nombre corto RequestBody ya lo ocupa el de Spring,
            // que es el que necesita el argumento del metodo.
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Solver parameters by name. Optional: without a body the default " +
                            "values are used. The list each solver accepts, with the meaning and range " +
                            "of each parameter, is at GET /api/v1/fms/solvers/{type}",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(type = "object"),
                            examples = {
                                    @ExampleObject(name = "Genetic: parameter sweep",
                                            description = "Larger population and budget than the defaults",
                                            value = "{\"populationSize\": 300, \"maxEvaluations\": 150000}"),
                                    @ExampleObject(name = "Genetic: longer search",
                                            description = "maxRestarts is the parameter with the most room to lower the cost",
                                            value = "{\"maxRestarts\": 10, \"restartStagnantGenerations\": 30}"),
                                    @ExampleObject(name = "Reproducible run",
                                            description = "With the same instance, the same parameters and the same seed "
                                                    + "the solution is identical. The response always returns the seed used, "
                                                    + "also when none is sent",
                                            value = "{\"populationSize\": 300, \"seed\": 1234}")})))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Instance solved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RoutingResponse.class),
                            examples = @ExampleObject(
                                    name = "MD-CVRP solution (instance p01)",
                                    description = "Actual solution of instance p01 (4 depots, 50 customers) with the GREEDY solver",
                                    value = OpenApiExamples.ROUTING_RESPONSE))),
            @ApiResponse(responseCode = "404",
                    description = "The instance is not in the instance directory",
                    content = @Content)
    })
    @PostMapping("/send")
    public ResponseEntity<RoutingResponse> sendInstance(
            @Parameter(description = "Instance file name without extension (e.g. p01)", required = true)
            @RequestParam String fileName,
            @Parameter(description = "Solver type to use (RANDOM, GREEDY, GENETIC or ANNEALING)")
            @RequestParam(defaultValue = "GREEDY") TypeSolver solverType,
            @RequestBody(required = false) Map<String, Object> parameters) throws IOException {
        RoutingResponse response = client.sendInstance(
                registry.resolve(fileName), solverType, parameters != null ? parameters : Map.of());
        return ResponseEntity.ok(response);
    }

    /**
     * Catalogo recortado a 3 instancias. Los valores son reales; el banco completo
     * son 33 instancias, de p01 a p23 y de pr01 a pr10.
     */
    private static final String INSTANCE_CATALOG_EXAMPLE = """
            {
              "total": 33,
              "instances": [
                {
                  "name": "p01",
                  "problemType": "MDVRP",
                  "numDepots": 4,
                  "numCustomers": 50,
                  "vehiclesPerDepot": 4,
                  "vehicleCapacity": 80,
                  "maxDuration": null,
                  "totalDemand": 777,
                  "loadRatio": 0.607031
                },
                {
                  "name": "p08",
                  "problemType": "MDVRP",
                  "numDepots": 2,
                  "numCustomers": 249,
                  "vehiclesPerDepot": 14,
                  "vehicleCapacity": 500,
                  "maxDuration": 310.0,
                  "totalDemand": 12106,
                  "loadRatio": 0.864714
                },
                {
                  "name": "pr01",
                  "problemType": "MDVRP",
                  "numDepots": 4,
                  "numCustomers": 48,
                  "vehiclesPerDepot": 1,
                  "vehicleCapacity": 200,
                  "maxDuration": 500.0,
                  "totalDemand": 657,
                  "loadRatio": 0.82125
                }
              ]
            }
            """;

    /**
     * Detalle real de p01, con las listas de clientes y vehiculos recortadas: la
     * respuesta completa trae sus 50 clientes y sus 16 vehiculos.
     */
    private static final String INSTANCE_DETAIL_EXAMPLE = """
            {
              "summary": {
                "name": "p01",
                "problemType": "MDVRP",
                "numDepots": 4,
                "numCustomers": 50,
                "vehiclesPerDepot": 4,
                "vehicleCapacity": 80,
                "maxDuration": null,
                "totalDemand": 777,
                "loadRatio": 0.607031
              },
              "depots": [
                { "id": "1", "lat": 20.0, "lng": 20.0, "matrixIndex": 0, "maxDuration": 0.0 },
                { "id": "2", "lat": 40.0, "lng": 30.0, "matrixIndex": 1, "maxDuration": 0.0 },
                { "id": "3", "lat": 30.0, "lng": 50.0, "matrixIndex": 2, "maxDuration": 0.0 },
                { "id": "4", "lat": 50.0, "lng": 60.0, "matrixIndex": 3, "maxDuration": 0.0 }
              ],
              "customers": [
                { "id": "1", "demand": 7,  "lat": 52.0, "lng": 37.0, "matrixIndex": 4, "serviceDuration": 0.0 },
                { "id": "2", "demand": 30, "lat": 49.0, "lng": 49.0, "matrixIndex": 5, "serviceDuration": 0.0 },
                { "id": "3", "demand": 16, "lat": 64.0, "lng": 52.0, "matrixIndex": 6, "serviceDuration": 0.0 }
              ],
              "vehicles": [
                { "id": "V1-1", "capacity": 80, "startDepotId": "1" },
                { "id": "V1-2", "capacity": 80, "startDepotId": "1" },
                { "id": "V2-1", "capacity": 80, "startDepotId": "2" }
              ]
            }
            """;
}
