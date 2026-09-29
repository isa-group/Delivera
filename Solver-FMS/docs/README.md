# Solver-FMS documentation

Solver-FMS is Delivera's route-solving subsystem. It solves the **MD-CVRP** (*Multi-Depot
Capacitated Vehicle Routing Problem*): given a set of customers with demand, several depots and a
fleet of capacitated vehicles, find the set of routes of minimum total cost that serves every
customer exactly once.

It consists of a gateway and four independent solving engines, each with a different strategy, so
that algorithms can be compared on the same instances.

## Index

| Document | Contents |
|---|---|
| [architecture.md](architecture.md) | Modules, ports, request flow, endpoints and deployment |
| [solver-metadata.md](solver-metadata.md) | Each solver's descriptor: description, parameters with their defaults, and how to register a new engine |
| [data-model.md](data-model.md) | DTOs, distance matrix, instance format and problem constraints |
| [engines/random-engine.md](engines/random-engine.md) | Random engine: the reference baseline |
| [engines/greedy-engine.md](engines/greedy-engine.md) | Greedy engine: nearest neighbour |
| [engines/genetic-engine.md](engines/genetic-engine.md) | Genetic engine: representation, operators, split and parameters |
| [engines/annealing-engine.md](engines/annealing-engine.md) | Simulated annealing engine: neighbourhoods, temperature calibration, feasibility and anytime curve |
| [benchmark.md](benchmark.md) | How to measure against the Cordeau instances, compare the solvers, and current results |
| [decision-tree.md](decision-tree.md) | Decision tree over those measurements: which solver suits which instance |
| [decisions-and-fixes.md](decisions-and-fixes.md) | What was fixed in the genetic engine and why |

## At a glance

```
                      ┌──────────────────────┐
  HTTP client   ────► │  fms-gateway  :8090  │
                      │ validates, dispatches│
                      └──────────┬───────────┘
                                 │  POST /api/v1/engine/solve
          ┌──────────────┬───────┴────────┬──────────────────┐
          ▼              ▼                ▼                  ▼
  ┌──────────────┐ ┌──────────────┐ ┌───────────────┐ ┌─────────────────┐
  │ greedy :8091 │ │ random :8092 │ │ genetic :8093 │ │ annealing :8094 │
  └──────────────┘ └──────────────┘ └───────┬───────┘ └────────┬────────┘
                                            └───── routing-core ┘
```

The client chooses the engine with the `solverType` field (`RANDOM`, `GREEDY`, `GENETIC`,
`ANNEALING`). The gateway validates the request, forwards it to the corresponding engine and
returns its response untouched. The two metaheuristic engines share `routing-core`, the objective
function, so that their costs are comparable.

Each solver publishes its own metadata at `GET /api/v1/fms/solvers`: description, algorithmic
family and the parameters it accepts with their default values, which the gateway applies when the
request does not carry them. See [solver-metadata.md](solver-metadata.md).

Which constraints each engine honours is not in that descriptor but in the table below and in each
engine's sheet: it is the reference to keep at hand when comparing costs.

## The four engines compared

| | random-engine | greedy-engine | genetic-engine | annealing-engine |
|---|---|---|---|---|
| Strategy | Random order | Nearest neighbour | Genetic algorithm with local search | Simulated annealing with local search |
| Deterministic | No | Yes | No | No (yes with `seed` and `maxLevels`) |
| Stopping criterion | - | - | Stagnation | **Time budget** |
| Honours capacity | Yes | Yes | Yes | Yes |
| Honours maximum duration | Yes | Yes | Yes | Yes |
| Honours fleet size | Yes | Yes | Yes | Yes |
| One vehicle per route | No (unbounded multi-trip) | No (unbounded multi-trip) | **Yes, with an exception** | **Yes, with an exception** |
| Time on p22 (360 customers) | milliseconds | milliseconds | ~3-6 s | whatever it is given (5 s by default) |
| Quality on p22 (BKS 5702) | - | - | ~6030 (+5.8 %) | ~5890 (+3.3 %), 1.8-4.2 % depending on the seed |
| What it is for | Reference upper bound | Reasonable fast solution | Production solution | Production solution with a bounded time and an anytime curve |

All four honour vehicle capacity and maximum route duration, so their costs are comparable with
each other. The difference is quality: the random engine is a reference upper bound, the greedy one
a reasonable fast solution, and the two metaheuristics are the production ones, with different
profiles: the genetic engine wins when the fleet is tight (`p07`) and the annealing one when there
is slack and budget (`p01`, `p03`, `p22`). Neither dominates, which is what makes the choice of
solver non-trivial. See each engine's sheet for details and limitations.

## Getting started

```bash
docker compose up --build
```

Swagger UI is at `http://localhost:8090/swagger-ui/index.html`.
