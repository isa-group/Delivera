package com.delivera.fms.routing.controller;

import com.delivera.fms.routing.dto.SolverCatalog;
import com.delivera.fms.routing.dto.SolverInfo;
import com.delivera.fms.routing.dto.TypeSolver;
import com.delivera.fms.routing.service.SolverRegistry;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fms/solvers")
@Tag(name = "Solvers", description = "Catalogo de algoritmos de resolucion disponibles")
public class SolverController {

    private final SolverRegistry registry;

    public SolverController(SolverRegistry registry) {
        this.registry = registry;
    }

    @Operation(summary = "Listar solvers disponibles",
            description = "Devuelve todos los solvers registrados en el gateway con sus metadatos. " +
                    "El catalogo se deriva de la configuracion de motores, por lo que crece " +
                    "automaticamente segun se van incorporando nuevos algoritmos. " +
                    "El valor del campo 'type' es el que debe enviarse como solverType al resolver un problema.")
    @ApiResponse(responseCode = "200", description = "Catalogo de solvers",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = SolverCatalog.class),
                    examples = @ExampleObject(
                            name = "Catalogo con 3 solvers",
                            description = "Respuesta sin comprobacion de estado (includeStatus=false)",
                            value = SOLVER_CATALOG_EXAMPLE)))
    @GetMapping
    public ResponseEntity<SolverCatalog> listSolvers(
            @Parameter(description = "Si es true, consulta la salud de cada motor y rellena el campo status. " +
                    "Anade latencia a la respuesta (hasta 2 segundos por motor no disponible).")
            @RequestParam(defaultValue = "false") boolean includeStatus) {
        return ResponseEntity.ok(SolverCatalog.of(registry.findAll(includeStatus)));
    }

    @Operation(summary = "Obtener un solver concreto",
            description = "Devuelve los metadatos del solver indicado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solver encontrado",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SolverInfo.class))),
            @ApiResponse(responseCode = "404", description = "El solver no esta registrado en el gateway",
                    content = @Content)
    })
    @GetMapping("/{type}")
    public ResponseEntity<SolverInfo> getSolver(
            @Parameter(description = "Tipo de solver", required = true, example = "GENETIC")
            @PathVariable TypeSolver type,
            @Parameter(description = "Si es true, consulta la salud del motor y rellena el campo status")
            @RequestParam(defaultValue = "false") boolean includeStatus) {
        return ResponseEntity.ok(registry.findByType(type, includeStatus));
    }

    private static final String SOLVER_CATALOG_EXAMPLE = """
            {
              "total": 3,
              "solvers": [
                {
                  "type": "RANDOM",
                  "name": "Aleatorio",
                  "description": "Asigna clientes a vehiculos de forma aleatoria respetando la capacidad. Sirve como linea base de comparacion",
                  "strategy": "Baseline",
                  "deterministic": false,
                  "status": "UNKNOWN"
                },
                {
                  "type": "GREEDY",
                  "name": "Voraz",
                  "description": "Heuristica constructiva de vecino mas cercano con asignacion al deposito mas proximo",
                  "strategy": "Heuristica constructiva",
                  "deterministic": true,
                  "status": "UNKNOWN"
                },
                {
                  "type": "GENETIC",
                  "name": "Algoritmo Genetico",
                  "description": "Metaheuristica evolutiva con cruce BCRC, mutaciones intra e inter deposito y busqueda local",
                  "strategy": "Metaheuristica",
                  "deterministic": false,
                  "status": "UNKNOWN"
                }
              ]
            }
            """;
}
