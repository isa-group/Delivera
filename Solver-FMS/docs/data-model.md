# Data model

## The problem: MD-CVRP

Given a set of customers with demand, several depots and a fleet of capacitated vehicles, find the
routes of minimum total cost such that:

- every customer is served **exactly once**;
- every route starts and ends at the same depot;
- the load of a route does not exceed the vehicle capacity;
- the duration of a route does not exceed the depot's maximum, if it has one;
- no more vehicles are used than each depot has.

The cost being minimised is the **total distance travelled**. Service times consume route duration
but do **not** add to the cost.

## Request: `RoutingRequest`

| Field | Type | Required | Meaning |
|---|---|---|---|
| `problemId` | `String` | Yes | Free identifier, returned in the response |
| `depots` | `DepotDto[]` | Yes, non-empty | Depots |
| `customers` | `CustomerDto[]` | Yes, non-empty | Customers to serve |
| `vehicles` | `VehicleDto[]` | No | Fleet. If omitted, unlimited capacity and number are assumed |
| `distanceMatrix` | `double[][]` | Yes | Square matrix `[origin][destination]` |
| `solverType` | `RANDOM \| GREEDY \| GENETIC \| ANNEALING` | Yes | Engine to use. Gateway only |
| `parameters` | `Map<String, Object>` | No | Solver parameters. Missing ones take the default declared in its metadata |

`parameters` is resolved against the solver's metadata before dispatching: missing ones are filled
in, undeclared ones are discarded with a warning, and one outside the declared range returns 400.
See [solver-metadata.md](solver-metadata.md).

### `DepotDto`

| Field | Type | Required | Meaning |
|---|---|---|---|
| `id` | `String` | Yes | Unique identifier |
| `lat`, `lng` | `Double` | Yes | Location. Informative: distances come from the matrix |
| `matrixIndex` | `Integer` | Yes | Row/column of this node in the matrix |
| `maxDuration` | `Double` | No | Maximum duration of a route leaving from here. Absent or `0` = no limit |

### `CustomerDto`

| Field | Type | Required | Meaning |
|---|---|---|---|
| `id` | `String` | Yes | Unique identifier |
| `demand` | `Integer ≥ 1` | Yes | Units to deliver |
| `lat`, `lng` | `Double` | Yes | Location. Informative |
| `matrixIndex` | `Integer` | Yes | Row/column of this node in the matrix |
| `serviceDuration` | `Double` | No | Service time. Absent = `0`. Counts towards duration, **not** towards cost |

### `VehicleDto`

| Field | Type | Required | Meaning |
|---|---|---|---|
| `id` | `String` | Yes | Unique identifier |
| `capacity` | `Integer ≥ 1` | Yes | Load capacity |
| `startDepotId` | `String` | Yes | Home depot. Must exist in `depots` |

The fleet is deduced by counting the vehicles of each `startDepotId`. **If a depot has no declared
vehicle, it is treated as having no capacity limit and no limit on the number of routes**, not as
having no vehicles. That is the interpretation `ProblemMapper` makes in the metaheuristic engines.

## The distance matrix

It is the only source of distances. `lat`/`lng` are not used to compute anything: you can send road
distances, travel times or any other metric, and the solver honours it.

`distanceMatrix[i][j]` is the cost of going from the node with `matrixIndex = i` to the node with
`matrixIndex = j`. It must be square, of size `depots.length + customers.length`.

**Indexing convention** applied by `StandardInstanceMapper`:

```
index 0 .. D-1        →  depots, in order
index D .. D+C-1      →  customers, in order
```

It is not mandatory: the gateway only requires the indices to be unique and within range. But it is
the one the instance mapper and the benchmark test use, so it is worth keeping.

The algorithms **do not assume the matrix is symmetric**, although the Cordeau instances are
(Euclidean distance). They do implicitly assume the triangle inequality at one point: the optimal
split never prefers using more routes than necessary, because splitting a route replaces an edge
`a→b` with `a→depot` plus `depot→b`, which is never cheaper if the triangle inequality holds.

## Response: `RoutingResponse`

| Field | Type | Meaning |
|---|---|---|
| `problemId` | `String` | The same as in the request |
| `status` | `String` | `"COMPLETED"` |
| `solverUsed` | `String` | `"RANDOM"`, `"GREEDY"`, `"GENETIC"` or `"ANNEALING"` |
| `totalCost` | `Double` | Sum of `totalDistance` over all routes |
| `computationTimeMs` | `Long` | Engine solving time |
| `seed` | `Long` | Seed the solver ran with. Absent in deterministic solvers |
| `routes` | `RouteDto[]` | Routes of the solution |
| `trace` | `TracePoint[]` | Anytime curve: instants at which the best feasible solution improved. Only from engines that search incrementally (`ANNEALING`); `null` otherwise |

`seed` closes the reproducibility loop: stochastic solvers always return the seed they used,
whether you sent it in `parameters` or the engine drew it. Sending it back with the same instance
and the same parameters gives **exactly** the same solution, so any run can be repeated afterwards,
including a good one that comes up by chance in a sweep.

### `RouteDto`

| Field | Type | Meaning |
|---|---|---|
| `vehicleId` | `String` | Assigned vehicle |
| `depotId` | `String` | Depot of departure and return |
| `stops` | `String[]` | Customer IDs, **in visiting order** |
| `totalDistance` | `Double` | Distance of the cycle depot → stops → depot |
| `totalLoad` | `Integer` | Sum of the stops' demands |

The depot does **not** appear in `stops`; it is implicit at the start and the end.

> **The same `vehicleId` may appear in several routes.** That is multi-trip: the vehicle makes a
> second journey. Greedy and random do it all the time; the genetic engine only when it has no
> alternative, because it actively seeks one vehicle per route. If you consume `routes` counting
> vehicles, count distinct identifiers, not routes.
>
> **No engine returns a vehicle that is not in the request.** The fleet is a problem datum: if a
> depot declares 4 vehicles, its routes are distributed among those 4 even if 5 are needed. The only
> exception is a request that declares no `vehicles`, which is how an unlimited fleet is requested;
> there the genetic engine names the routes with synthetic identifiers `V-GA-<depot>-<n>`.

## Benchmark instance format

The files in `instances-MD-CVRP-JSON/` are generated by `parse_mdcvrp.py` from Cordeau's original
format. `StandardInstanceParser` reads them and `StandardInstanceMapper` converts them into a
`RoutingRequest`.

```json
{
  "filename": "p22",
  "problem_type": "MDVRP",
  "problem_type_code": 2,
  "vehicles_per_depot": 5,
  "num_customers": 360,
  "num_depots": 9,
  "depots":    [ { "depot_id": 1, "max_duration": 200, "vehicle_capacity": 60,
                   "id": 361, "x": 0.0, "y": 0.0, "service_duration": 0, "demand": 0 } ],
  "customers": [ { "id": 1, "x": -10.0, "y": -10.0, "service_duration": 0, "demand": 12,
                   "visit_frequency": 1, "num_combinations": 9, "visit_combinations": [1, 2, 4] } ]
}
```

Mapping applied by `StandardInstanceMapper`:

| Instance field | Destination |
|---|---|
| `depots[i].x`, `.y` | `DepotDto.lng`, `DepotDto.lat` - **note: `x` is longitude, `y` is latitude** |
| `depots[i].max_duration` | `DepotDto.maxDuration` |
| `depots[i].vehicle_capacity` | `VehicleDto.capacity` of its vehicles |
| `vehicles_per_depot` | How many `VehicleDto` are generated per depot |
| `customers[i].demand` | `CustomerDto.demand` |
| `customers[i].service_duration` | `CustomerDto.serviceDuration` |
| - | `DepotDto.id` = `"1"`, `"2"`, … by position |
| - | `CustomerDto.id` = the instance's numeric `id`, as text |

The matrix is computed by `DistanceMatrixCalculator` with **Euclidean** distance over `(lng, lat)`.

### Fields that are parsed but not used

`StandardInstanceParser` reads these fields and keeps them in `NodeEntry`, but the mapper does not
propagate them and no engine knows about them:

- `visit_frequency`, `num_combinations`, `visit_combinations` - they belong to the *periodic*
  problem (PVRP), not to the MD-CVRP.
- `time_window_earliest`, `time_window_latest` - time windows. No engine supports them.

They are there so that no information is lost when parsing, with a view to supporting those
variants later.

## Constraints: who honours them

| Constraint | Where it comes from | random | greedy | genetic |
|---|---|---|---|---|
| Every customer exactly once | Problem definition | Yes | Yes | Yes |
| Vehicle capacity | `VehicleDto.capacity` | Yes | Yes | Yes |
| Maximum route duration | `DepotDto.maxDuration` | Yes | Yes | Yes |
| Number of vehicles per depot | Count of `vehicles` | No | No | Yes |
| Time windows | Not propagated | No | No | No |

This table matters when comparing costs: **an engine that ignores constraints solves an easier
problem and gives lower costs that are not comparable**. See [benchmark.md](benchmark.md).
