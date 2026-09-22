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
@Tag(name = "Instancias", description = "Carga y procesamiento de instancias de benchmark MD-CVRP")
public class StandardInstanceController {

    private final StandardInstanceClient client;
    private final StandardInstanceRegistry registry;

    public StandardInstanceController(StandardInstanceClient client, StandardInstanceRegistry registry) {
        this.client = client;
        this.registry = registry;
    }

    @Operation(summary = "Listar instancias estandar disponibles",
            description = "Devuelve el banco de instancias MD-CVRP que tiene montado el gateway, con las "
                    + "propiedades de cada una: numero de depositos y clientes, flota, capacidad, duracion "
                    + "maxima de ruta y demanda total. Son las instancias clasicas de Cordeau, las mismas "
                    + "que usa el benchmark. El catalogo se deriva del contenido del directorio de "
                    + "instancias, asi que anadir un fichero basta para que aparezca aqui. "
                    + "El campo 'name' es el que debe enviarse como fileName al resolver una instancia.")
    @ApiResponse(responseCode = "200", description = "Catalogo de instancias",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = InstanceCatalog.class),
                    examples = @ExampleObject(
                            name = "Catalogo del banco Cordeau",
                            description = "Respuesta recortada a 3 de las 33 instancias del banco",
                            value = INSTANCE_CATALOG_EXAMPLE)))
    @GetMapping
    public ResponseEntity<InstanceCatalog> listInstances() throws IOException {
        return ResponseEntity.ok(InstanceCatalog.of(registry.findAll()));
    }

    @Operation(summary = "Obtener una instancia concreta",
            description = "Devuelve la instancia indicada con todos sus nodos, ya traducida al modelo "
                    + "del gateway: no es el fichero tal cual, sino los depositos, clientes y vehiculos "
                    + "que se le enviarian al motor. Los identificadores son los mismos que aparecen "
                    + "despues en las rutas de la solucion, de modo que una respuesta de "
                    + "POST /api/v1/fms/instances/send se puede leer contra esta representacion. "
                    + "La matriz de distancias no se incluye: se calcula al resolver a partir de "
                    + "estas coordenadas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Instancia encontrada",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = InstanceDetail.class),
                            examples = @ExampleObject(
                                    name = "Instancia p01",
                                    description = "4 depositos y 50 clientes. Las listas de clientes y "
                                            + "vehiculos se muestran recortadas",
                                    value = INSTANCE_DETAIL_EXAMPLE))),
            @ApiResponse(responseCode = "404",
                    description = "La instancia no esta en el directorio de instancias",
                    content = @Content)
    })
    @GetMapping("/{name}")
    public ResponseEntity<InstanceDetail> getInstance(
            @Parameter(description = "Nombre de la instancia, con o sin extension (ej. p01)",
                    required = true, example = "p01")
            @PathVariable String name) throws IOException {
        return ResponseEntity.ok(registry.findByName(name));
    }

    @Operation(summary = "Enviar instancia de benchmark al solver",
            description = "Carga un archivo de instancia MD-CVRP (JSON) desde el directorio de instancias, " +
                    "lo parsea y lo envia al motor de ruteo correspondiente para su resolucion. " +
                    "Admite en el cuerpo un mapa de parametros del solver: los que no se envien toman su " +
                    "valor por defecto segun los metadatos, lo que permite repetir la misma instancia con " +
                    "distintas configuraciones del mismo algoritmo.",
            // Declarado en la operacion y no en el argumento: springdoc no genera
            // cuerpo para un parametro de tipo Map, que reserva para los query params.
            // Cualificado porque el nombre corto RequestBody ya lo ocupa el de Spring,
            // que es el que necesita el argumento del metodo.
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Parametros del solver por nombre. Opcional: sin cuerpo se usan los " +
                            "valores por defecto. La lista admitida por cada solver, con el significado " +
                            "y el rango de cada parametro, esta en GET /api/v1/fms/solvers/{type}",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(type = "object"),
                            examples = {
                                    @ExampleObject(name = "Genetico: barrido de parametros",
                                            description = "Poblacion y presupuesto mayores que los de por defecto",
                                            value = "{\"populationSize\": 300, \"maxEvaluations\": 150000}"),
                                    @ExampleObject(name = "Genetico: busqueda mas larga",
                                            description = "maxRestarts es el parametro con mas recorrido para bajar el coste",
                                            value = "{\"maxRestarts\": 10, \"restartStagnantGenerations\": 30}"),
                                    @ExampleObject(name = "Ejecucion reproducible",
                                            description = "Con la misma instancia, los mismos parametros y la misma semilla "
                                                    + "la solucion es identica. La respuesta devuelve siempre la semilla usada, "
                                                    + "tambien cuando no se envia",
                                            value = "{\"populationSize\": 300, \"seed\": 1234}")})))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Instancia resuelta correctamente",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RoutingResponse.class),
                            examples = @ExampleObject(
                                    name = "Solucion MD-CVRP (instancia p01)",
                                    description = "Resolucion real de la instancia p01 (4 depositos, 50 clientes) con el solver GREEDY",
                                    value = OpenApiExamples.ROUTING_RESPONSE))),
            @ApiResponse(responseCode = "404",
                    description = "La instancia no esta en el directorio de instancias",
                    content = @Content)
    })
    @PostMapping("/send")
    public ResponseEntity<RoutingResponse> sendInstance(
            @Parameter(description = "Nombre del archivo de instancia sin extension (ej. p01)", required = true)
            @RequestParam String fileName,
            @Parameter(description = "Tipo de solver a utilizar (RANDOM, GREEDY o GENETIC)")
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
