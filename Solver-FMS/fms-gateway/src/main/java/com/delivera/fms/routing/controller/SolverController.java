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
@Tag(name = "Solvers", description = "Solver catalogue and its metadata")
public class SolverController {

    private final SolverRegistry registry;

    public SolverController(SolverRegistry registry) {
        this.registry = registry;
    }

    @Operation(summary = "List available solvers",
            description = "Returns every solver registered in the gateway with its full metadata: " +
                    "description, algorithmic family and parameters with their default values. " +
                    "The catalogue is derived from the engine configuration, so it grows " +
                    "automatically as new algorithms are added. " +
                    "The value of the 'type' field is the one to send as solverType when solving a problem.")
    @ApiResponse(responseCode = "200", description = "Solver catalogue",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = SolverCatalog.class),
                    examples = @ExampleObject(
                            name = "Abridged catalogue",
                            description = "Response without health check (includeStatus=false)",
                            value = SOLVER_CATALOG_EXAMPLE)))
    @GetMapping
    public ResponseEntity<SolverCatalog> listSolvers(
            @Parameter(description = "If true, checks the health of each engine and fills in the status field. " +
                    "Adds latency to the response (up to 2 seconds per unavailable engine).")
            @RequestParam(defaultValue = "false") boolean includeStatus) {
        return ResponseEntity.ok(SolverCatalog.of(registry.findAll(includeStatus)));
    }

    @Operation(summary = "Get a specific solver",
            description = "Returns the metadata of the given solver: description and accepted " +
                    "parameters with their default values and ranges. " +
                    "The example response is the actual descriptor of the GENETIC solver, with the 14 parameters " +
                    "it accepts and what each one is for; they are the ones that can be sent in the 'parameters' " +
                    "map when solving a problem.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solver found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SolverInfo.class),
                            examples = @ExampleObject(
                                    name = "GENETIC solver descriptor",
                                    description = "Parameters of the genetic algorithm with their meaning, " +
                                            "default value and accepted range",
                                    value = GENETIC_SOLVER_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "The solver is not registered in the gateway",
                    content = @Content)
    })
    @GetMapping("/{type}")
    public ResponseEntity<SolverInfo> getSolver(
            @Parameter(description = "Solver type", required = true, example = "GENETIC")
            @PathVariable TypeSolver type,
            @Parameter(description = "If true, checks the engine's health and fills in the status field")
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
              "name": "Genetic Algorithm",
              "description": "Evolutionary metaheuristic with BCRC crossover, intra- and inter-depot mutations, Prins' optimal split and local search",
              "strategy": "Metaheuristic",
              "technology": "Java 22 / Spring Boot + jMetal 6.6",
              "version": "2.0.0",
              "deterministic": false,
              "parameters": [
                {
                  "name": "populationSize",
                  "description": "Individuals per generation. A larger population explores more distinct solutions at the cost of more time per generation",
                  "type": "INTEGER",
                  "defaultValue": 150,
                  "min": 10.0,
                  "max": 2000.0,
                  "required": false
                },
                {
                  "name": "maxEvaluations",
                  "description": "Cap on objective function evaluations. It rarely kicks in: in practice the run ends earlier through stagnation, so raising it does not improve the cost on its own",
                  "type": "INTEGER",
                  "defaultValue": 75000,
                  "min": 1000.0,
                  "max": 5000000.0,
                  "required": false
                },
                {
                  "name": "minGenerations",
                  "description": "Generations always run before a restart or an early stop is allowed. Prevents cutting short a search that has not taken off yet",
                  "type": "INTEGER",
                  "defaultValue": 100,
                  "min": 1.0,
                  "max": 100000.0,
                  "required": false
                },
                {
                  "name": "crossoverProbability",
                  "description": "Probability of crossing each selected pair with BCRC, which reinserts the other parent's customers in their best position. Lowering it keeps more parents intact",
                  "type": "DECIMAL",
                  "defaultValue": 0.9,
                  "min": 0.0,
                  "max": 1.0,
                  "required": false
                },
                {
                  "name": "intraDepotMutationProbability",
                  "description": "Probability of reordering customers within the same depot. Refines already assigned routes without changing the distribution among depots",
                  "type": "DECIMAL",
                  "defaultValue": 0.2,
                  "min": 0.0,
                  "max": 1.0,
                  "required": false
                },
                {
                  "name": "interDepotMutationProbability",
                  "description": "Probability of moving border customers to another depot. It is the only operator that changes the distribution among depots, on which quality depends in instances with nearby depots",
                  "type": "DECIMAL",
                  "defaultValue": 0.3,
                  "min": 0.0,
                  "max": 1.0,
                  "required": false
                },
                {
                  "name": "elitismCount",
                  "description": "Best distinct individuals that pass intact to the next generation. Protects against regression; raising it too much reduces diversity",
                  "type": "INTEGER",
                  "defaultValue": 5,
                  "min": 0.0,
                  "max": 100.0,
                  "required": false
                },
                {
                  "name": "tournamentSize",
                  "description": "Individuals competing in each selection. A larger tournament means more selective pressure and faster convergence, with more risk of a local optimum",
                  "type": "INTEGER",
                  "defaultValue": 3,
                  "min": 2.0,
                  "max": 20.0,
                  "required": false
                },
                {
                  "name": "localSearchFrequency",
                  "description": "Every how many generations 2-opt and relocate are applied to the best individuals. A lower value intensifies the search and makes each generation more expensive",
                  "type": "INTEGER",
                  "defaultValue": 10,
                  "min": 1.0,
                  "max": 1000.0,
                  "required": false
                },
                {
                  "name": "interDepotFrequency",
                  "description": "Every how many generations the inter-depot mutation is applied to a sample of the offspring",
                  "type": "INTEGER",
                  "defaultValue": 5,
                  "min": 1.0,
                  "max": 1000.0,
                  "required": false
                },
                {
                  "name": "restartStagnantGenerations",
                  "description": "Generations without improvement that trigger a population restart keeping the best individual. Together with maxRestarts, it is what really ends the run",
                  "type": "INTEGER",
                  "defaultValue": 20,
                  "min": 1.0,
                  "max": 10000.0,
                  "required": false
                },
                {
                  "name": "maxRestarts",
                  "description": "Population restarts allowed before stopping. It is the parameter with the most room to improve the cost, at the expense of a longer run",
                  "type": "INTEGER",
                  "defaultValue": 3,
                  "min": 0.0,
                  "max": 100.0,
                  "required": false
                },
                {
                  "name": "heuristicSeedRatio",
                  "description": "Fraction of the initial population built with randomised nearest neighbour; the rest is generated at random. Starts from better solutions at the expense of diversity",
                  "type": "DECIMAL",
                  "defaultValue": 0.2,
                  "min": 0.0,
                  "max": 1.0,
                  "required": false
                },
                {
                  "name": "seed",
                  "description": "Random generator seed. With the same instance, the same parameters and the same seed the solution is identical. If not sent, the engine draws one and returns it in the response's seed field, so any run can be repeated afterwards. The upper bound is 2^48-1 because java.util.Random keeps 48 bits: above it two different seeds would give the same sequence",
                  "type": "INTEGER",
                  "min": 0.0,
                  "max": 281474976710655.0,
                  "required": false
                }
              ],
              "status": "UNKNOWN"
            }
            """;

    private static final String SOLVER_CATALOG_EXAMPLE = """
            {
              "total": 4,
              "solvers": [
                {
                  "type": "GREEDY",
                  "name": "Greedy",
                  "description": "Nearest-neighbour constructive heuristic with assignment to the closest depot",
                  "strategy": "Constructive heuristic",
                  "technology": "Java 22 / Spring Boot",
                  "version": "1.0.0",
                  "deterministic": true,
                  "parameters": [],
                  "status": "UNKNOWN"
                },
                {
                  "type": "GENETIC",
                  "name": "Genetic Algorithm",
                  "description": "Evolutionary metaheuristic with BCRC crossover, intra- and inter-depot mutations, Prins' optimal split and local search",
                  "strategy": "Metaheuristic",
                  "technology": "Java 22 / Spring Boot + jMetal 6.6",
                  "version": "2.0.0",
                  "deterministic": false,
                  "parameters": [
                    {
                      "name": "populationSize",
                      "description": "Individuals per generation. A larger population explores more distinct solutions at the cost of more time per generation",
                      "type": "INTEGER",
                      "defaultValue": 150,
                      "min": 10.0,
                      "max": 2000.0,
                      "required": false
                    }
                  ],
                  "status": "UNKNOWN"
                }
              ]
            }
            """;
}
