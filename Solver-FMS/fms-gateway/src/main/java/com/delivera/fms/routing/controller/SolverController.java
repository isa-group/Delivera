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
@Tag(name = "Solvers", description = "Catalogo de solvers y su metainformacion")
public class SolverController {

    private final SolverRegistry registry;

    public SolverController(SolverRegistry registry) {
        this.registry = registry;
    }

    @Operation(summary = "Listar solvers disponibles",
            description = "Devuelve todos los solvers registrados en el gateway con su metainformacion completa: " +
                    "descripcion, parametros con sus valores por defecto y restricciones sobre los problemas " +
                    "que pueden abordar. El catalogo se deriva de la configuracion de motores, por lo que crece " +
                    "automaticamente segun se van incorporando nuevos algoritmos. " +
                    "El valor del campo 'type' es el que debe enviarse como solverType al resolver un problema.")
    @ApiResponse(responseCode = "200", description = "Catalogo de solvers",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = SolverCatalog.class),
                    examples = @ExampleObject(
                            name = "Catalogo resumido",
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
            description = "Devuelve la metainformacion del solver indicado: descripcion, parametros " +
                    "admitidos con sus valores por defecto y restricciones sobre los problemas que soporta. " +
                    "El ejemplo de respuesta es el descriptor real del solver GENETIC, con los 14 parametros " +
                    "que acepta y para que sirve cada uno; son los que pueden enviarse en el mapa 'parameters' " +
                    "al resolver un problema.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solver encontrado",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SolverInfo.class),
                            examples = @ExampleObject(
                                    name = "Descriptor del solver GENETIC",
                                    description = "Parametros del algoritmo genetico con su significado, " +
                                            "valor por defecto y rango admitido",
                                    value = GENETIC_SOLVER_EXAMPLE))),
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

    /**
     * Descriptor completo del solver GENETIC. Es una copia de lo que devuelve el
     * endpoint con la configuracion actual: sirve para que Swagger muestre los
     * parametros y su significado sin tener que invocarlo. Si cambian los
     * parametros en application.yml, este ejemplo hay que actualizarlo.
     */
    private static final String GENETIC_SOLVER_EXAMPLE = """
            {
              "type": "GENETIC",
              "name": "Algoritmo Genetico",
              "description": "Metaheuristica evolutiva con cruce BCRC, mutaciones intra e inter deposito, troceado optimo de Prins y busqueda local",
              "strategy": "Metaheuristica",
              "technology": "Java 22 / Spring Boot + jMetal 6.6",
              "version": "2.0.0",
              "deterministic": false,
              "parameters": [
                {
                  "name": "populationSize",
                  "description": "Individuos por generacion. Mas poblacion explora mas soluciones distintas a costa de mas tiempo por generacion",
                  "type": "INTEGER",
                  "defaultValue": 150,
                  "min": 10.0,
                  "max": 2000.0,
                  "required": false
                },
                {
                  "name": "maxEvaluations",
                  "description": "Tope de evaluaciones de la funcion objetivo. Rara vez llega a actuar: en la practica la ejecucion termina antes por estancamiento, asi que subirlo no mejora el coste por si solo",
                  "type": "INTEGER",
                  "defaultValue": 75000,
                  "min": 1000.0,
                  "max": 5000000.0,
                  "required": false
                },
                {
                  "name": "minGenerations",
                  "description": "Generaciones que se ejecutan siempre antes de permitir un reinicio o la parada anticipada. Evita cortar una busqueda que aun no ha arrancado",
                  "type": "INTEGER",
                  "defaultValue": 100,
                  "min": 1.0,
                  "max": 100000.0,
                  "required": false
                },
                {
                  "name": "crossoverProbability",
                  "description": "Probabilidad de cruzar cada pareja seleccionada con BCRC, que reinserta los clientes del otro padre en su mejor posicion. Bajarla conserva mas padres intactos",
                  "type": "DECIMAL",
                  "defaultValue": 0.9,
                  "min": 0.0,
                  "max": 1.0,
                  "required": false
                },
                {
                  "name": "intraDepotMutationProbability",
                  "description": "Probabilidad de reordenar clientes dentro de un mismo deposito. Afina rutas ya asignadas sin cambiar el reparto entre depositos",
                  "type": "DECIMAL",
                  "defaultValue": 0.2,
                  "min": 0.0,
                  "max": 1.0,
                  "required": false
                },
                {
                  "name": "interDepotMutationProbability",
                  "description": "Probabilidad de mover clientes frontera a otro deposito. Es el unico operador que cambia el reparto entre depositos, del que depende la calidad en instancias con depositos proximos",
                  "type": "DECIMAL",
                  "defaultValue": 0.3,
                  "min": 0.0,
                  "max": 1.0,
                  "required": false
                },
                {
                  "name": "elitismCount",
                  "description": "Mejores individuos distintos que pasan intactos a la siguiente generacion. Protege del retroceso; subirlo demasiado reduce la diversidad",
                  "type": "INTEGER",
                  "defaultValue": 5,
                  "min": 0.0,
                  "max": 100.0,
                  "required": false
                },
                {
                  "name": "tournamentSize",
                  "description": "Individuos que compiten en cada seleccion. Mas torneo es mas presion selectiva y convergencia mas rapida, con mas riesgo de optimo local",
                  "type": "INTEGER",
                  "defaultValue": 3,
                  "min": 2.0,
                  "max": 20.0,
                  "required": false
                },
                {
                  "name": "localSearchFrequency",
                  "description": "Cada cuantas generaciones se aplica 2-opt y relocate a los mejores individuos. Un valor menor intensifica la busqueda y encarece cada generacion",
                  "type": "INTEGER",
                  "defaultValue": 10,
                  "min": 1.0,
                  "max": 1000.0,
                  "required": false
                },
                {
                  "name": "interDepotFrequency",
                  "description": "Cada cuantas generaciones se aplica la mutacion inter deposito a una muestra de la descendencia",
                  "type": "INTEGER",
                  "defaultValue": 5,
                  "min": 1.0,
                  "max": 1000.0,
                  "required": false
                },
                {
                  "name": "restartStagnantGenerations",
                  "description": "Generaciones sin mejora que disparan un reinicio de poblacion conservando el mejor individuo. Junto con maxRestarts es lo que de verdad termina la ejecucion",
                  "type": "INTEGER",
                  "defaultValue": 20,
                  "min": 1.0,
                  "max": 10000.0,
                  "required": false
                },
                {
                  "name": "maxRestarts",
                  "description": "Reinicios de poblacion permitidos antes de parar. Es el parametro con mas recorrido para mejorar el coste, a cambio de alargar la ejecucion",
                  "type": "INTEGER",
                  "defaultValue": 3,
                  "min": 0.0,
                  "max": 100.0,
                  "required": false
                },
                {
                  "name": "heuristicSeedRatio",
                  "description": "Fraccion de la poblacion inicial construida con vecino mas cercano aleatorizado; el resto se genera al azar. Arranca desde mejores soluciones a costa de diversidad",
                  "type": "DECIMAL",
                  "defaultValue": 0.2,
                  "min": 0.0,
                  "max": 1.0,
                  "required": false
                },
                {
                  "name": "seed",
                  "description": "Semilla del generador aleatorio. Fijarla hace reproducible la ejecucion, que sin ella difiere en cada llamada; imprescindible para comparar configuraciones",
                  "type": "INTEGER",
                  "defaultValue": null,
                  "min": null,
                  "max": null,
                  "required": false
                }
              ],
              "status": "UNKNOWN"
            }
            """;

    private static final String SOLVER_CATALOG_EXAMPLE = """
            {
              "total": 3,
              "solvers": [
                {
                  "type": "GREEDY",
                  "name": "Voraz",
                  "description": "Heuristica constructiva de vecino mas cercano con asignacion al deposito mas proximo",
                  "strategy": "Heuristica constructiva",
                  "technology": "Java 22 / Spring Boot",
                  "version": "1.0.0",
                  "deterministic": true,
                  "parameters": [],
                  "status": "UNKNOWN"
                },
                {
                  "type": "GENETIC",
                  "name": "Algoritmo Genetico",
                  "description": "Metaheuristica evolutiva con cruce BCRC, mutaciones intra e inter deposito y busqueda local",
                  "strategy": "Metaheuristica",
                  "technology": "Java 22 / Spring Boot + jMetal 6.6",
                  "version": "2.0.0",
                  "deterministic": false,
                  "parameters": [
                    {
                      "name": "populationSize",
                      "description": "Numero de individuos de la poblacion",
                      "type": "INTEGER",
                      "defaultValue": 150,
                      "min": 10.0,
                      "max": 2000.0,
                      "required": false
                    },
                    {
                      "name": "seed",
                      "description": "Semilla del generador aleatorio. Fijarla hace reproducible la ejecucion",
                      "type": "INTEGER",
                      "defaultValue": null,
                      "min": null,
                      "max": null,
                      "required": false
                    }
                  ],
                  "status": "UNKNOWN"
                }
              ]
            }
            """;
}
