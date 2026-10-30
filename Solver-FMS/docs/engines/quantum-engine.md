# quantum-engine

**Port 8095** · Python 3.14 / FastAPI + Qiskit + D-Wave Ocean · built from the quantum line's repository · solvers `QISKIT` and `DWAVE`

## What it is for

The hybrid quantum-classical engine of the quantum line. It splits the instance by depot and tries
to solve each region on a quantum backend: QAOA on a local Qiskit simulator (solver `QISKIT`) or
D-Wave's Leap hybrid solver in the cloud (solver `DWAVE`). When the quantum path is not viable it
falls back to PyVRP. It is the first real path from Delivera to quantum hardware and simulators;
with 24 simulated qubits only very small instances reach the Qiskit part.

## Where the code lives

The solver is not copied into Delivera. Its code lives in the quantum line's repository `cqvrp`
(folder `quantum-solver-ms`, by Manuel García Gómez), and Delivera only keeps what is needed to
build and run it:

- [`engines/quantum-engine/Dockerfile`](../../engines/quantum-engine/Dockerfile): base image,
  dependencies, port and health check. It copies the code from a build context named `cqvrp`.
- The `quantum-engine` service in [`docker-compose.yml`](../../docker-compose.yml), which defines
  that context and the engine's configuration.

The `cqvrp` context comes from the `CQVRP_CONTEXT` variable:

| Value | When to use it |
| --- | --- |
| Not set (default `../../cqvrp/quantum-solver-ms`) | Local development, with `cqvrp` cloned next to `Delivera` |
| `https://github.com/prototipo/cqvrp.git#<commit>:quantum-solver-ms` | Reproducible builds pinned to a commit (the repository is private, so the build needs access to it) |

With the local default the image is built from whatever is checked out in the clone, including
uncommitted changes.

## Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/health` | Service details and supported backends; used by the Docker health check |
| GET | `/actuator/health` | Gateway health probe. Not available yet in `cqvrp` (see below) |
| POST | `/api/v1/quantum-routing/analyze` | Initial viability filter, without solving |
| POST | `/api/v1/quantum-routing/solve` | Solves the instance and returns routes and metrics |

The engine still uses the `cqvrp` contract (x/y coordinates, fleet per depot, options in `X-*`
headers), not `/api/v1/engine/solve`. Adapting it to the Delivera contract is a separate task.

## In the gateway

`cqvrp` has two quantum backends, and the gateway registers each one as its own solver. Both point
to this same engine, which will choose the backend from the request's `solverType` once the
contract adapter exists (today `cqvrp` chooses it with the `X-Target-Quantum-Backend` header):

| Solver | Backend | Where it runs | Qubit limit |
| --- | --- | --- | --- |
| `QISKIT` | QAOA on each region's QUBO | Local Qiskit simulator, inside the engine | Q_max (24 today) |
| `DWAVE` | Constrained quadratic model (CQM) on each region, with MTZ against subtours | D-Wave Leap hybrid solver, in the cloud | None from the simulator; Leap's own limits |

Both appear in `GET /api/v1/fms/solvers` with their metadata and parameters (blocks
`fms.engines.qiskit` and `fms.engines.dwave` in the gateway's `application.yml`), and the gateway
validates those parameters like any other solver's. Two things do not work yet:

- **Status `DOWN`.** The gateway probes `/actuator/health`, which `cqvrp` does not have yet.
- **Solving returns 404.** The gateway sends the problem to `/api/v1/engine/solve`, and the engine
  only has the `cqvrp` endpoints. It will work once the contract adapter exists.

`FMS_ENGINES_QISKIT_ENABLED=false` or `FMS_ENGINES_DWAVE_ENABLED=false` in the gateway removes
either one from the catalogue.

## D-Wave credentials

The `DWAVE` backend calls D-Wave Leap, so it needs an API token, network access and quota. The
engine reads it from `DWAVE_API_TOKEN`, which `docker-compose.yml` passes through only when it is
set: export it in the shell or put it in a `.env` file next to `docker-compose.yml` (ignored by
Git). The token never goes into the repository. Without it, `cqvrp` cannot reach Leap, discards
the quantum candidates and solves the instance with PyVRP, giving the reason in the response.

## Parameters

They mirror the `X-*` headers of `cqvrp`, with the same defaults, so each descriptor matches the
run that happens.

`QISKIT`:

| Parameter | Type | Default | Range | `cqvrp` header | Meaning |
| --- | --- | --- | --- | --- | --- |
| `maxQubits` | INTEGER | 24 | 1–24 | `X-Max-Qubits` | Largest QUBO that may go to the simulator (Q_max). A region that needs more is solved with PyVRP |
| `quantumTimeoutSeconds` | DECIMAL | 30.0 | 1–300 | `X-Quantum-Timeout-Seconds` | Wall-clock limit of each QAOA run; up to four candidates are tried |
| `classicalTimeLimit` | INTEGER | 60 | 1–300 | `X-Classical-Time-Limit` | PyVRP budget for the fallback and for the classical reference. PyVRP always uses all of it |
| `includeMtz` | BOOLEAN | false | | `X-Include-Mtz` | Adds MTZ constraints against subtours, at the cost of many more qubits |

`DWAVE`:

| Parameter | Type | Default | Range | `cqvrp` header | Meaning |
| --- | --- | --- | --- | --- | --- |
| `classicalTimeLimit` | INTEGER | 60 | 1–300 | `X-Classical-Time-Limit` | Same as in `QISKIT` |

`maxQubits`, `quantumTimeoutSeconds` and `includeMtz` do not apply to D-Wave: `cqvrp` ignores
them on that path, always includes MTZ in the CQM and does not limit its run time (Leap's default
applies).

Not exposed for either: the number of extra candidates, fixed at 3 inside `cqvrp`, and a `seed`,
because neither QAOA nor Leap is seeded in `cqvrp`, so neither solver is deterministic.

With the defaults, a `QISKIT` request with a quantum solution takes at least 70 s, almost all of it
PyVRP (60 s of classical reference plus 10 s per super-customer of internal TSP), and the gateway
gives up on an engine after 300 s.

## Configuration

| Variable | Default | Meaning |
| --- | --- | --- |
| `CQVRP_CONTEXT` | `../../cqvrp/quantum-solver-ms` | Where the solver code is taken from |
| `QUANTUM_MAX_QUBITS` | 24 | Available quantum capacity (Q_max) |

## Pending changes in cqvrp

A patch has been proposed to the quantum line. Until it is merged, the Qiskit path never returns
a quantum solution, `/actuator/health` answers 404 (the gateway would report the engine as DOWN)
and `QUANTUM_MAX_QUBITS` has no effect.

Fixes to the Qiskit solver:

- QAOA circuits are transpiled to basic gates before simulation. Without it a 10-qubit QUBO takes
  more than 5 minutes instead of 0.6 s, so the 30 s timeout always expires and PyVRP takes over.
- Variable values are read from `variables_dict`; `get_variable_value` does not exist in
  `qiskit-optimization` 0.7 and every candidate failed after QAOA.
- A QAOA sample that violates the constraints, or leaves customers unserved, is rejected.
  Before, an empty solution with cost 0 was returned as feasible and won over real candidates.

Changes to fit Solver-FMS:

- `GET /actuator/health`, the path the gateway probes.
- `QUANTUM_MAX_QUBITS` as the default and upper bound of the `X-Max-Qubits` header, instead of a
  fixed 24.
- Synchronous solving endpoints, so a long run does not block the health endpoints.

## Running it

The service belongs to the `quantum` profile, because building it needs the `cqvrp` clone. Without
the profile the rest of Solver-FMS starts as before and the gateway lists `QISKIT` and `DWAVE` as `DOWN`.

```bash
docker compose --profile quantum up --build
```
