# Architecture

## Modules

Solver-FMS is five independent Spring Boot applications, each with its own `pom.xml`, its own
`Dockerfile` and its own container, plus a library shared by the two metaheuristic engines.

| Module | Artifact | Port | Responsibility |
|---|---|---|---|
| `fms-gateway` | `fms-gateway` | 8090 | Public API, validation, dispatch, instance loading, OpenAPI |
| `engines/greedy-engine` | `greedy-engine` | 8091 | Greedy solving |
| `engines/random-engine` | `random-engine` | 8092 | Random solving |
| `engines/genetic-engine` | `genetic-engine` | 8093 | Solving with a genetic algorithm |
| `engines/annealing-engine` | `annealing-engine` | 8094 | Solving with simulated annealing |
| `engines/routing-core` | `routing-core` | - | Shared objective function and local search (not a service) |

Java 22, Spring Boot 3.4.0. The two metaheuristic engines add jMetal 6.6: the genetic one uses its
`PermutationSolution` representation and its random generator; the annealing one uses the
`AbstractLocalSearch` template and the same generator. The algorithms themselves are hand-written.

There is no parent POM: each engine inherits from `spring-boot-starter-parent` and builds on its
own. There is an **aggregator** in `engines/pom.xml`, which exists only so that the engines that
depend on `routing-core` build in order with a single command:

```bash
mvn -f engines/pom.xml -pl annealing-engine -am package
```

Without it you would have to `mvn install` `routing-core` by hand before building the engine.

### The shared core

`routing-core` is a library with no Spring and no jMetal, holding what **every metaheuristic engine
must compute exactly the same way** for their costs to be comparable:

```
model/    Depot, Customer, Route, RoutingProblem     the problem, with the constraints inside the depot
split/    RouteSplitter                              Prins' optimal split: the objective function
search/   RouteOptimizer                             intra-route 2-opt + relocate between routes of one depot
          DepotRebalancer                            reassignment between depots: fleet repair + improvement
```

Each engine translates its DTOs to `RoutingProblem` once, when the request arrives
(`ProblemMapper`), and from then on every cost computation happens in the core. **That is not
reuse, it is experimental control**: if the genetic and the annealing engines measured cost with
different code, a difference between them could come from the measurement and not from the search.

The extraction was done with the genetic engine already pinned by
[`GeneticRegressionTest`](../engines/genetic-engine/src/test/java/com/delivera/fms/engine/genetic/benchmark/GeneticRegressionTest.java):
same costs to the tenth decimal before and after, so the core is the same algorithm that lived
inside the engine, not a reimplementation.

### Why the DTOs are duplicated

Each engine has its **own copy** of `CustomerDto`, `DepotDto`, `VehicleDto`, `RouteDto`,
`RoutingRequest` and `RoutingResponse`, in its own package. `routing-core` shares the problem
model, not the HTTP contract: the DTOs remain each engine's own.

The practical consequence: **an engine only understands the fields its copy declares**. The gateway
sends `DepotDto.maxDuration` and `CustomerDto.serviceDuration`, but only the genetic and annealing
engines declare them; greedy and random silently ignore them. This works because Spring Boot
disables `FAIL_ON_UNKNOWN_PROPERTIES` by default, so an extra field does not break
deserialisation. The same happens in the other direction: only the annealing engine returns
`trace`, and the gateway declares it optional in its `RoutingResponse` to let it through.

If you add a field to the contract, bear this in mind: **it has to be replicated in the copy of
every engine that must use it**, and leaving it out of the ones that do not need it is a decision,
not an oversight.

## Request flow

```
1. POST /api/v1/fms/routing/solve            RoutingController
2. Bean Validation                           @Valid on RoutingRequest
3. Consistency validation                    validateConsistency()
4. WebClient selection by solverType         EngineDispatcher
5. Solver parameter resolution               SolverRegistry.resolveParameters()
6. POST http://<engine>/api/v1/engine/solve  300 s timeout
7. The engine solves                         <X>RouteSolver.solve()
8. The response is returned untouched        ResponseEntity.ok(response)
```

### Gateway validations

`RoutingController.validateConsistency` checks, before dispatching:

- The distance matrix is square and of size exactly `depots + customers`.
- The `matrixIndex` values of depots and customers are **unique across both** and within range.
- Every vehicle references a `startDepotId` that exists among the depots.

Past that, `EngineDispatcher` completes the **solver parameters** missing from the request with the
default values declared in its metadata, so the engine always receives the full configuration. An
undeclared parameter is discarded with a log warning; one outside the declared range returns 400.
See [solver-metadata.md](solver-metadata.md).

The engines take for granted that this has already been validated and **do not check it again**. If
you call an engine directly on port 8091-8094, bypassing the gateway, a badly sized matrix will
produce an `ArrayIndexOutOfBoundsException`, not a 400 error.

## Endpoints

### Gateway (8090)

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/fms/routing/solve` | Solves a problem sent in the request body |
| `POST` | `/api/v1/fms/instances/send?fileName=p01&solverType=GREEDY` | Loads an instance from disk, maps it and solves it |
| `GET` | `/api/v1/fms/instances` | Catalogue of standard instances with each one's properties |
| `GET` | `/api/v1/fms/instances/{name}` | One instance with its depots, customers and vehicles |
| `GET` | `/api/v1/fms/solvers` | Solver catalogue with their metadata |
| `GET` | `/api/v1/fms/solvers/{type}` | Metadata of one solver |
| `GET` | `/api-docs` | OpenAPI specification |
| `GET` | `/swagger-ui/index.html` | Swagger UI |
| `GET` | `/actuator/health` | Status |

> **Mind the OpenAPI path.** It is customised with `springdoc.api-docs.path: /api-docs`, so it is
> **not** at the default `/v3/api-docs`. Any client that discovers the contract automatically must
> point at `/api-docs`.

`StandardInstanceRegistry` is the only place that translates an instance name into a file path: it
normalises the path and checks that it stays inside the instance directory, so that a name with
`../` cannot read arbitrary files. A name that does not exist returns 404, both when queried and
when sent for solving.

The instance catalogue is not declared anywhere: it is derived from the directory contents, just as
the solver catalogue is derived from `fms.engines`. Dropping a new file in the volume is enough for
it to be listed.

### Engines (8091, 8092, 8093, 8094)

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/engine/solve` | Solves the received problem |
| `GET` | `/actuator/health` | Status |

The engines expose exactly the same contract. Adding a new engine means: implement those two
endpoints — in whatever technology —, add the value to the `TypeSolver` enum and declare its block
under `fms.engines` in `application.yml`. No other Java class changes: the HTTP client, the
dispatch, the catalogue and the parameters are derived from that configuration. What to declare is
detailed in [solver-metadata.md](solver-metadata.md). That is how the simulated annealing engine was
registered: the only gateway Java code it touched was the enum and the optional `trace` field of
the response.

## Configuration

Each engine is declared under `fms.engines.<engine>`: besides the URL, that is where its metadata
lives (description, algorithmic family and parameters with their defaults). `docker-compose.yml`
only overrides the URLs, through environment variables:

| Variable | Default value |
|---|---|
| `FMS_ENGINES_GREEDY_URL` | `http://greedy-engine:8091` |
| `FMS_ENGINES_RANDOM_URL` | `http://random-engine:8092` |
| `FMS_ENGINES_GENETIC_URL` | `http://genetic-engine:8093` |
| `FMS_ENGINES_ANNEALING_URL` | `http://annealing-engine:8094` |
| `FMS_INSTANCES_DIR` | `/app/instances-MD-CVRP-JSON` |

The instance directory is mounted as a read-only volume from `./instances-MD-CVRP-JSON`, so
instances can be added without rebuilding the image.

## Error handling

`EngineDispatcher` distinguishes two cases:

- If the engine answers with an HTTP error code, the `WebClientResponseException` is rethrown as is
  and the gateway's `GlobalExceptionHandler` translates it.
- Any other communication failure (engine down, 300 s timeout, empty response) is wrapped in
  `EngineUnavailableException`.

## Deployment

```bash
docker compose up --build
```

The containers share the `fms-network` network. The gateway declares `depends_on` with
`condition: service_healthy`, so it does not start until the four engines answer their
*healthcheck*.

The engines that depend on `routing-core` (genetic and annealing) are built with `./engines` as
Docker context and `engines/<engine>/Dockerfile` as the file, because the image has to compile the
core before the engine. Greedy and random keep using their own directory as context.

For development, each module can be started on its own:

```bash
mvn spring-boot:run
```

In that case the URLs must point at `localhost`, because the names `greedy-engine`,
`random-engine`, `genetic-engine` and `annealing-engine` only resolve inside the Docker network.
