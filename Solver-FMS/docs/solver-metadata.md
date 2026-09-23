# Solver metadata

Every solver registered in the gateway carries **mandatory metadata** describing it: what it does
and which parameters it is invoked with. It is what lets the system work as an umbrella over solvers
of different technologies and paradigms, and what makes their results comparable.

## Why

Pitting two algorithms against each other requires knowing that both solve the same problem with
the same configuration. If the parameters live inside each engine's code, as constants, a benchmark
result cannot be reproduced or cited: you have to recompile to change a population size, and nobody
knows with which settings the number in the table was obtained.

The metadata solves that by publishing each solver's parameters with their default values, which
the gateway **actually applies** before invoking the engine. An experiment is described by the
parameter map that was sent plus the solver's `version`.

## Descriptor structure

`GET /api/v1/fms/solvers/{type}` returns the full descriptor. It has two parts.

### 1. Identity and description

| Field | Meaning |
|---|---|
| `type` | Identifier. The same value sent in `solverType` when solving |
| `name` | Human-readable name |
| `description` | **Mandatory.** What the algorithm does, in one sentence |
| `strategy` | Algorithmic family: baseline, constructive heuristic, metaheuristic, exact… |
| `technology` | What it is implemented with. Informative: the gateway depends only on the HTTP contract |
| `version` | Solver version. Pinning it is what makes a benchmark result citable |
| `deterministic` | Whether two runs on the same input give the same solution |
| `status` | Engine health, only when requested with `includeStatus=true` |

### 2. Parameters

`parameters` declares each invocation parameter with its **default value**, which is the one the
gateway applies when the client does not send it:

```json
{
  "name": "populationSize",
  "description": "Individuos por generacion. Mas poblacion explora mas soluciones distintas a costa de mas tiempo por generacion",
  "type": "INTEGER",
  "defaultValue": 150,
  "min": 10.0,
  "max": 2000.0,
  "required": false
}
```

`type` is `INTEGER` or `DECIMAL`, and says whether the parameter admits decimals: that is what
`min`/`max` cannot express, because `elitismCount` (0–100) and `crossoverProbability` (0–1) look
the same in the descriptor and are not. A parameter without `defaultValue` and with
`required: true` forces the client to send it; without `defaultValue` and without `required`, the
solver decides internally.

The client sends the ones it wants to change in the request's `parameters` map; the rest are filled
in automatically before dispatching to the engine, so that a run always starts from a complete and
known configuration.

What each engine accepts today: `GREEDY` none, `RANDOM` only `seed`, `GENETIC` fourteen,
documented one by one in [engines/genetic-engine.md](engines/genetic-engine.md#parameters), and
`ANNEALING` twelve, in [engines/annealing-engine.md](engines/annealing-engine.md#parameters).

## Parameter resolution

It happens in `EngineDispatcher`, before sending the problem to the engine:

1. Parameters the request does not carry are filled with their `defaultValue`.
2. One not declared by the solver is discarded with a log warning.
3. One that is not a number, has decimals while being `INTEGER`, or falls outside `min`/`max`,
   returns **400**. A `12.7` on `populationSize` is rejected instead of truncated to 12: the engine
   must not run a configuration nobody asked for.
4. A `required` one with neither default nor sent value returns **400**.

The engine therefore always receives the complete configuration. It lives in the dispatcher and not
in the controller because it is an invariant: any entry path (direct request or instance load)
reaches the engine with the same contract.

## Registering a solver

The metadata lives in the gateway's configuration, not inside the engine. That is the decision that
makes the system an umbrella: a solver written in Python, a commercial one or an exact one integrate
the same way, without implementing any Java contract, only by exposing two HTTP endpoints
(`POST /api/v1/engine/solve` and `GET /actuator/health`).

1. Add the constant to the `TypeSolver` enum.
2. Add its block under `fms.engines` in `application.yml`, with its description and parameters.

```yaml
fms:
  engines:
    ortools:
      url: http://ortools-engine:8094
      display-name: OR-Tools CP-SAT
      description: Exact solver with a time limit on CP-SAT
      strategy: Exact
      technology: Python 3.12 / OR-Tools
      version: 1.0.0
      deterministic: true
      order: 40
      parameters:
        - name: timeLimitSeconds
          description: Solver time budget
          type: INTEGER
          default-value: 60
          min: 1
          max: 3600
```

No Java class changes: the HTTP client, the dispatch, the catalogue and the parameter resolution are
derived from that block. `enabled: false` removes an engine from the catalogue without deleting its
configuration.

An engine may ignore the `parameters` map it receives entirely (that is what the greedy one does). If
it wants to use it, the only rule is that **its defaults match the declared ones**: otherwise the
descriptor would be describing a run that is not the one that happens.

## Use in experimentation and benchmarking

What the parameters give you is the ability to launch the same instance with different
configurations of the same algorithm without recompiling anything:

```bash
curl -X POST "http://localhost:8090/api/v1/fms/instances/send?fileName=p01&solverType=GENETIC" \
  -H "Content-Type: application/json" \
  -d '{"populationSize": 300, "maxEvaluations": 150000}'
```

With one important caveat: **the genetic and random engines are not reproducible**. Two runs with
the same parameters on the same instance give different results, because their random generator
starts from a seed that can be neither fixed nor queried. When comparing two configurations, a small
cost difference may be chance and not improvement: repeat each several times and compare means, not
single runs.

What the descriptor does **not** say is which problem constraints each engine honours: that the
greedy one ignores the maximum route duration and the number of vehicles, or that the genetic one
does take them into account. That information exists, but in prose: it is in the comparison table of
the [README](README.md) and in each engine's sheet. Keep it in mind when comparing results, because
an engine that ignores a constraint may win on distance precisely because of that.

## Decisions and known limits

- **The metadata is declared by the integrator, not the engine.** An engine could expose it itself
  and the gateway discover it at start-up. Configuration was preferred because it imposes nothing on
  the integrated solver, which is exactly what allows absorbing foreign technologies. The cost is
  that the descriptor and the real behaviour can drift apart: they are two artifacts that must be
  maintained together.
- **The descriptor only covers identity and parameters.** It does not declare objective functions or
  supported problem features. A `constraints` block with that vocabulary was tried and removed:
  nothing consumed it, it duplicated what each engine's documentation already says, and it forced
  maintaining a catalogue of hypothetical features. If some day a program must decide whether a
  solver fits an instance, that is the moment to reintroduce it.
