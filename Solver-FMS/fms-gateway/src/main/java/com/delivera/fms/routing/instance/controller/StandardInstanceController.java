package com.delivera.fms.routing.instance.controller;

import com.delivera.fms.routing.config.OpenApiExamples;
import com.delivera.fms.routing.dto.RoutingResponse;
import com.delivera.fms.routing.dto.TypeSolver;
import com.delivera.fms.routing.instance.client.StandardInstanceClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

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
    @ApiResponse(responseCode = "200", description = "Instancia resuelta correctamente",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = RoutingResponse.class),
                    examples = @ExampleObject(
                            name = "Solucion MD-CVRP (instancia p01)",
                            description = "Resolucion real de la instancia p01 (4 depositos, 50 clientes) con el solver GREEDY",
                            value = OpenApiExamples.ROUTING_RESPONSE)))
    @PostMapping("/send")
    public ResponseEntity<RoutingResponse> sendInstance(
            @Parameter(description = "Nombre del archivo de instancia sin extension (ej. p01)", required = true)
            @RequestParam String fileName,
            @Parameter(description = "Tipo de solver a utilizar (RANDOM, GREEDY o GENETIC)")
            @RequestParam(defaultValue = "GREEDY") TypeSolver solverType,
            @RequestBody(required = false) Map<String, Object> parameters) throws IOException {
        String jsonFileName = fileName.endsWith(".json") ? fileName : fileName + ".json";
        Path basePath = Paths.get(instancesDir).toAbsolutePath().normalize();
        Path filePath = basePath.resolve(jsonFileName).normalize();
        if (!filePath.startsWith(basePath)) {
            throw new IllegalArgumentException("Invalid file name: " + fileName);
        }
        RoutingResponse response = client.sendInstance(
                filePath, solverType, parameters != null ? parameters : Map.of());
        return ResponseEntity.ok(response);
    }
}
