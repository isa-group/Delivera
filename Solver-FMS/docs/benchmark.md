# Benchmark

## The instances

`instances-MD-CVRP-JSON/` holds the 33 **Cordeau** MDVRP instances, the standard test bed for this
problem: `p01`–`p23` and `pr01`–`pr10`. They range from 50 to 360 customers and from 2 to 9 depots.

For each one the **best known solution** (BKS) is published, which allows measuring the *gap*: how
much worse our solution is than the best known one.

### Instances that are the same with a different constraint

This is easy to overlook and leads to wrong conclusions. Several groups share customers, depots,
capacity and fleet, and **differ only in `max_duration`**:

| Group | `max_duration` | BKS |
|---|---|---|
| p12 / p13 / p14 | 0 / 200 / 180 | 1318.95 / 1318.95 / 1360.12 |
| p15 / p16 / p17 | 0 / 200 / 180 | 2505.42 / 2572.23 / 2709.09 |
| p18 / p19 / p20 | 0 / 200 / 180 | 3702.85 / 3827.06 / 4058.07 |
| p21 / p22 / p23 | 0 / 200 / 180 | 5474.84 / 5702.16 / 6095.46 |

The tighter the duration, the higher the BKS: the constraint makes the optimal solution more
expensive. **22 of the 33 instances have a duration limit**, and the 10 `prXX` also have non-zero
service times, which consume duration but not cost.

It is the best test of whether an engine honours the constraint: if it ignores it, it solves the
three cases the same way and returns the same cost. If it honours it, the cost rises as the duration
tightens, just as the BKS does. On p21/p22/p23 the greedy engine gives 8977 / 9517 / 10665.

## The golden rule

> **A cost below the BKS is not a record: it is the sign that some instance constraint is being
> violated.**

That is how it was detected that `max_duration` was not reaching the engine: a Postman query
returned 5935 on p23, whose BKS is 6095.46. See [decisions-and-fixes.md](decisions-and-fixes.md).

That is why the benchmark test **validates feasibility as well as cost**: a cost improvement cannot
come from skipping a constraint without the test catching it.

## How to run it

`SolverBenchmarkTest`, in `/fms-gateway`, exercises **all** registered solvers. It is an
integration test: it needs the system up.

```bash
docker compose up -d
```

```bash
mvn test -Dtest=SolverBenchmarkTest -Dbenchmark=true -Dinstances=p01,p22 -Druns=3
```

From `/fms-gateway`. In the terminal each argument must be quoted: `"-Dbenchmark=true"`.

| Parameter | Default | Meaning |
|---|---|---|
| `-Dbenchmark=true` | - | **Mandatory.** Without it the test is skipped, so as not to slow down the build |
| `-Dinstances=` | `p01` | Comma-separated instances, or `all` for the 33 |
| `-Dsolvers=` | all | Subset, e.g. `GREEDY,GENETIC` |
| `-Druns=` | `1` | Repetitions **per non-deterministic solver**. Deterministic ones run once |
| `-Dgateway=` | `http://localhost:8090` | Gateway to measure against |
| `-Dtimeout=` | `600` | Seconds per request |

Output:

```
Solvers registrados: [RANDOM, GREEDY, GENETIC, ANNEALING]

p22  9 depositos, 360 clientes, duracion maxima 200  |  BKS 5702,16
  solver     n      mejor      media     gap  rutas   tiempo  factible     semilla
  RANDOM     2   20326,68   20918,49 +256,5%    131    189ms  si         127997977
  GREEDY     1    9517,33    9517,33  +66,9%     63    214ms  si                 -
  GENETIC    2    5946,53    5953,76   +4,3%     36     2,3s  si        1012033380
```

### The `semilla` (seed) column

It is the seed of the **best** of the repetitions, the one to send back in `parameters` to obtain
exactly that solution again. It shows `-` for deterministic solvers, which do not depend on chance.
It is what makes raising `-Druns` worthwhile: a good run is no longer lost, it can be repeated and
used as a starting point for tuning parameters.

### Without Docker: each engine's regression tests

The two metaheuristic engines also have their own regression test that does not need the gateway:
[`GeneticRegressionTest`](../engines/genetic-engine/src/test/java/com/delivera/fms/engine/genetic/benchmark/GeneticRegressionTest.java)
and [`AnnealingRegressionTest`](../engines/annealing-engine/src/test/java/com/delivera/fms/engine/annealing/benchmark/AnnealingRegressionTest.java).
They load `p01`, `p07` and `p22` with `CordeauInstanceLoader`, which replicates the gateway's
mapping (lat = y, lng = x, Euclidean matrix, `vehicles_per_depot` vehicles per depot), run the engine
with a fixed seed and check feasibility and cost **to the tenth decimal**. They do not judge quality:
they judge that the search has not changed, which is what is needed to refactor with confidence. If
a change in the algorithm is deliberate, the expected costs are updated in the same commit.

To explore the annealing parameters without starting anything there is `AnnealingCalibrationTest`,
described in [engines/annealing-engine.md](engines/annealing-engine.md#how-to-calibrate).

### The solvers are not written into the test

They are read from `GET /api/v1/fms/solvers`, which is derived from the engine configuration.
Registering a new engine is enough for it to enter the benchmark, even if implemented in another
technology: the only thing required of it is the HTTP contract. The descriptor also provides the
`deterministic` field, which is what decides whether to repeat a solver several times or once.

## What it validates

`CordeauInstance.violations` checks, on every run and for every solver:

1. Every customer appears in **exactly one** route, and no route visits non-existent customers.
2. Each route's `totalLoad` matches the sum of its stops' demands.
3. No route exceeds the vehicle **capacity**.
4. No route exceeds the depot's **maximum duration**, counting distance + service times.
5. No depot uses **more distinct vehicles** than it has.

It returns **all** violations, not just the first: that way you see at a glance whether an engine
misses one specific constraint or whether the solution is broken from top to bottom.

The cost is **recomputed from the stops**, not taken from the `totalCost` the engine reports. An
engine that adds up wrongly cannot get away with it for having computed it itself.

> **Check 5 counts distinct vehicles, not routes.** A vehicle may make more than one trip: what it
> cannot do is not exist. It is a deliberately lax check, and the price is that it passes easily — a
> vehicle making 26 trips passes it —, so **it says nothing about how many trips the fleet makes**.
> That is seen by comparing `routes` with `vehicles_used` in the `compare_solvers.py` file: on `p22`
> the random engine makes 139 routes with 9 vehicles and the genetic one 36 with 36.

Cost does **not** fail the test. Comparing quality is `compare_solvers.py`'s job; what is checked
here is that what each engine returns is a valid solution of the problem.

## Current results of the genetic engine

One run per instance. All feasible.

| Instance | Cost | BKS | Gap | Time |
|---|---:|---:|---:|---:|
| p01 | 609.24 | 576.87 | 5.61 % | 0.6 s |
| p02 | 496.55 | 473.53 | 4.86 % | 0.8 s |
| p03 | 676.10 | 641.19 | 5.44 % | 0.9 s |
| p04 | 1075.46 | 1001.59 | 7.37 % | 1.6 s |
| p05 | 778.70 | 750.03 | 3.82 % | 1.4 s |
| p06 | 942.79 | 876.50 | 7.56 % | 1.2 s |
| p07 | 947.17 | 885.80 | 6.93 % | 1.2 s |
| p08 | 4691.85 | 4437.68 | 5.73 % | 3.8 s |
| p09 | 4108.53 | 3900.22 | 5.34 % | 2.7 s |
| p10 | 3952.55 | 3663.02 | 7.90 % | 2.9 s |
| p11 | 3818.61 | 3554.18 | 7.44 % | 4.3 s |
| p12 | 1341.84 | 1318.95 | 1.74 % | 1.0 s |
| p13 | 1341.84 | 1318.95 | 1.74 % | 1.1 s |
| p14 | 1365.69 | 1360.12 | **0.41 %** | 1.2 s |
| p15 | 2664.85 | 2505.42 | 6.36 % | 1.5 s |
| p16 | 2664.85 | 2572.23 | 3.60 % | 1.6 s |
| p17 | 2731.37 | 2709.09 | **0.82 %** | 1.6 s |
| p18 | 3957.85 | 3702.85 | 6.89 % | 2.2 s |
| p19 | 3988.79 | 3827.06 | 4.23 % | 2.3 s |
| p20 | 4097.06 | 4058.07 | **0.96 %** | 2.1 s |
| p21 | 6010.11 | 5474.84 | 9.78 % | 3.3 s |
| p22 | 5961.68 | 5702.16 | 4.55 % | 3.2 s |
| p23 | 6145.58 | 6095.46 | **0.82 %** | 3.1 s |
| pr01 | 934.50 | 861.32 | 8.50 % | 0.7 s |
| pr02 | 1362.70 | 1307.34 | 4.23 % | 1.3 s |
| pr03 | 1900.19 | 1803.80 | 5.34 % | 1.7 s |
| pr04 | 2216.75 | 2058.31 | 7.70 % | 3.1 s |
| pr05 | 2660.08 | 2331.20 | 14.11 % | 6.4 s |
| pr06 | 3100.59 | 2676.30 | 15.85 % | 10.2 s |
| pr07 | 1183.73 | 1089.56 | 8.64 % | 1.0 s |
| pr08 | 1816.64 | 1664.85 | 9.12 % | 1.9 s |
| pr09 | 2240.81 | 2153.10 | 4.07 % | 2.4 s |
| pr10 | 3288.99 | 2921.85 | 12.57 % | 11.8 s |

Median gap ~6 %. The instances with the tightest duration (p14, p17, p20, p23) have the best gap,
because the constraint shrinks the solution space. The worst are `pr05`, `pr06` and `pr10`, the
largest ones with service times.

As a reference for how far things have come: before the fixes described in
[decisions-and-fixes.md](decisions-and-fixes.md), p22 gave 6737.95 **and was infeasible** (routes
with load above the vehicle capacity).

> **Verify the BKS table.** The values are in `Solver-FMS/best-known.json`, read by both the test
> and `compare_solvers.py`. They are the ones usually published for the Cordeau set, but only p01,
> p22 and p23 have been explicitly cross-checked. If any were wrong, the gap the test prints would be
> wrong too. Cost and feasibility validation do not depend on this table.

## Comparing all solvers

The test above measures **only the genetic engine**. To pit every registered solver against each
other on the same instances there is a script that hits the API, validates what each returns and
writes the experiment down:

```bash
python compare_solvers.py
```

From `Solver-FMS/`, with the system up. No dependencies: standard library only. With no arguments it
launches **the 33 instances** with every solver in the catalogue.

| Parameter | Default | Meaning |
|---|---|---|
| `--url` | `http://localhost:8090` | Gateway to measure against |
| `--instances` | `all` | `all`, or comma-separated instances (`p01,p22`) |
| `--solvers` | all | Subset, e.g. `GREEDY,GENETIC` |
| `--runs` | 3 | Repetitions **per non-deterministic solver**. Below 5 the deviation is not reliable |
| `--seed` | - | Base seed. Repetition *k* uses `seed+k-1`, and the whole experiment repeats as is |
| `--out` | `results/` | Directory where the report and data are written |
| `--label` | - | Experiment label. Goes into the three file names and into a column of the runs |
| `--timeout` | `600` | Seconds per request |

### Where everything lives

`compare_solvers.py` is only the command line and the wiring. The work is in the `experimentation/`
package, split in two by purpose:

| Package | Purpose | Dependencies |
|---|---|---|
| `comparison/` | **Measure**: launch the solvers and write the experiment down. Used by `compare_solvers.py` | Standard library only |
| `tree/` | **Analyse**: learn from those measurements which solver suits. Used by `decision_tree.py` | scikit-learn, matplotlib |

The split is not cosmetic: mixing them would force installing scikit-learn in order to measure, or
giving it up in order to analyse.

| `comparison/` | What it knows |
|---|---|
| `instance.py` | What a Cordeau instance is: its features, what a solution costs and which constraints it violates |
| `gateway.py` | Talking to the gateway: catalogue and solving |
| `runner.py` | Launch, measure and turn each run into a row |
| `dataset.py` | The CSV schema and the aggregation of repetitions |
| `report.py` | The Markdown report |

| `tree/` | What it knows |
|---|---|
| `dataset.py` | What "suits" means: cost and, at equal cost, time |
| `model.py` | Train, validate against the majority rule and verify the algorithm |
| `plot.py` | The tree as an image |
| `report.py` | The Markdown report |

How to run and interpret the tree is in [decision-tree.md](decision-tree.md).

The separation has a concrete audience: **later analysis of the results needs `instance.py` and
nothing else**. Reading the features of the 33 instances should not require a running gateway or
drag in the report generator.

```python
from experimentation.comparison.instance import Instance, all_names

rows = [Instance.load(name).features() for name in all_names()]
```

### The three outputs

Each run leaves three files under `--out`, with the date and the label in the name:

```
instancias-2026-08-13-1316.csv
datos-2026-08-13-1316.csv
informe-2026-08-13-1316.md
instancias-2026-08-13-1332-semilla-fija.csv
datos-2026-08-13-1332-semilla-fija.csv
informe-2026-08-13-1332-semilla-fija.md
```

- **`instancias-<date>.csv`** - one row per **instance**: the problem's properties. It is the table
  that describes *what* was solved.
- **`datos-<date>.csv`** - one row per **run**: one solver, one repetition, one instance. It is the
  table that describes *what came out*. It joins the previous one on `filename` and repeats none of
  its columns. That the schema does not change between experiments is what allows concatenating the
  CSVs of several sessions and analysing them together: they are told apart by `run_id` and
  `label`, not by having different columns.
- **`informe-<date>.md`** - the readable report: run date, commit, configuration, solver
  descriptors with their parameters, global summary, per-instance comparison, **one table per
  solver** with the 33 rows, incidents and methodological notes.

### Why two data files and not one

Because there are **two levels of observation**, and putting them in the same table forces lying
in one of the two.

A Cordeau instance has 50 customers, 4 depots and a BKS of 576.87: that holds for the instance, and
keeps holding whether it is solved once or eleven times. Cost, time, seed and feasibility belong to
each specific run. In a single file, the fourteen problem columns were repeated identically across
the eleven rows of each instance. The problems of doing it that way are not stylistic:

- **Any aggregate comes out biased.** The mean of `load_ratio` over the CSV rows is not the mean
  over instances: it is the mean weighted by number of runs, and an instance with eleven runs weighs
  eleven times more than one where a solver failed on the first try. Whoever opens the file and
  takes the mean gets a number that seems to describe the test bed and does not.
- **The table admits impossible states.** With the properties repeated, nothing prevents two `p01`
  rows from saying it has 50 and 60 customers. The file stops guaranteeing what it claims.
- **It reads badly.** A file with one row per run invites counting runs; if it also carries the
  problem's properties, it invites counting instances over the same rows. They are two different
  questions and now each has its table.

Joining them is one line, and the name prefix keeps them paired:

```python
import pandas as pd

runs = pd.read_csv("results/datos-2026-08-13-1316.csv")
instances = pd.read_csv("results/instancias-2026-08-13-1316.csv")
everything = runs.merge(instances, on="filename")
```

`decision_tree.py` does that join itself: it is given the `datos-*.csv` and looks for its
`instancias-*.csv` next to it.

The date goes to the minute, not the second, because the name is read and cited. Two experiments
within the same minute — two quick tests on one instance — disambiguate with a suffix
(`...-1316-2.csv`) instead of overwriting each other, and that same suffix goes in the `run_id`
column, so the file name and its rows' identifier always match.

The split is deliberate: the CSV is for the machine and must not change shape; the report is to be
read and cited. The CSV is written and flushed to disk as the run progresses, and a Ctrl-C
interruption still generates the report with what was measured up to then: a full sweep takes
several minutes and what was already measured is not thrown away.

### The columns of `instancias-<date>.csv`

One row per instance. Everything comes from the instance file and nothing depends on the solver.

| Column | What it is |
|---|---|
| `filename` | The instance id. It is the key joining it to the runs |
| `num_customers`, `num_depots`, `vehicles_per_depot`, `vehicle_capacity` | The declared problem size |
| `has_duration_limit` | `true`/`false`. Whether routes have a maximum duration |
| `max_duration` | The maximum duration **when there is one**; empty when not |
| `total_demand` | Sum of customer demands |
| `load_ratio` | Total demand over total fleet capacity. Measures how tight the instance is |
| `avg_service_duration` | Mean service time per customer. It is `0` in 23 of the 33: most Cordeau instances do not model it |
| `customers_per_depot`, `area`, `customer_density` | The geometry: how many customers per depot and in how much space |
| `mean_nn_distance` | Mean distance from a customer to its nearest customer. Measures clustering |
| `mean_nearest_depot_distance` | Mean distance from a customer to the **nearest** depot |
| `bks` | The best published solution. It is a property of the instance, not a result of this experiment |

Two model details that are not cosmetic:

- **`max_duration` is empty when there is no limit**, and the `has_duration_limit` flag says so
  separately. The instance file encodes "no limit" with a `0`, and that `0` is not a duration: it is
  the absence of the datum. Written as is, "no limit" ends up below the tightest instance in the
  bench in any numeric order, and any cut or filter on that column says the opposite of what
  happens. Empty is what it means: there is no value.
- **`mean_nearest_depot_distance` is the distance to the nearest depot**, not to the depots. It was
  called `mean_depot_distance`, which read as if it averaged over all of them. They are two
  different measures and the one that matters is this one: it is the one actually travelled.

### The columns of `datos-<date>.csv`

One row per run. Two blocks.

**Identification**: `filename` (the key to the instance table), `solver`, `repetition`, `run_id`,
`label`, `timestamp`, `solver_version`, `strategy`, `deterministic`, `seed` and `params` - the
effective parameters it ran with, which are the descriptor's defaults plus whatever was sent.

**Result**: `status`, `cost`, `reported_cost`, `gap_pct`, `routes`, `vehicles_used`,
`extra_trips`, `feasible`, `violations`, `violations_detail`, `elapsed_ms`, `engine_ms` and `error`.

`gap_pct` is computed with the BKS but belongs to this level: it changes from one repetition to
another because the cost changes. What is not here is the `bks` itself, which is the same for the
eleven rows of an instance and lives in the other table.

### Terminal output

```
[22/33] p22  9 depositos, 360 clientes, duracion maxima 200  |  BKS 5702.16
  solver     n      mejor      media    desv     gap  rutas   tiempo  factible         semilla
  RANDOM     3   20656.41   20826.14   221.0 +262.3%    130     81ms       0/3      1668748295
  GREEDY     1    9517.33    9517.33       -  +66.9%     63     77ms       0/1               -
  GENETIC    3    5952.10    5960.98     7.7   +4.4%     36     2.6s       3/3      1012033380
```

### The `desv` (deviation) column

Except for the greedy one, no engine repeats its result unless its seed is fixed, and by default the
script does not fix it: it measures the algorithm's real variability, which is what is interesting
to compare. A single run, therefore, says nothing. If two configurations are separated by less than
this deviation, the difference is noise and not improvement. It shows `-` when there was only one
run: without repetitions there is no dispersion to measure.

**With few repetitions it misleads.** The genetic engine often converges to the same solution, and
on small instances it is easy for two runs to give the same number. On `p01`, five runs give three
distinct values; two runs may show zero apparent dispersion.

The number of repetitions is decided by the solver's descriptor: if it declares
`deterministic: true`, it runs once because repeating it only wastes time.

With `--seed` the experiment becomes reproducible: repetition *k* uses `seed+k-1`, so repetitions
remain distinct from each other but the full sweep can be launched again and give the same. It is
what suits comparing two configurations of the genetic engine without chance entering the
comparison.

### The cost that is compared, and feasibility

The cost is the one **recomputed from each route's stops**, not the one the engine reports. The CSV
carries both, `cost` and `reported_cost`, so if they ever stopped matching it would show.

The script **also validates the instance's constraints** on every run, with the same checks as
`CordeauInstance` in the gateway test: customer served exactly once, reported load, capacity,
maximum duration and vehicles per depot. They are two implementations of the same rules — one in
Java and one in Python — that must be maintained together, in exchange for the script not depending
on Maven.

That is why the **best cost of each solver and the report's wins are computed only over feasible
runs**. Without that rule the comparison flips: the greedy engine ignores the maximum duration and
the number of vehicles, so in the 22 instances with a limit it may win on distance precisely by
skipping the constraint.

## Benchmark through the API

To measure against the deployed system instead of against the class directly:

```bash
curl -X POST "http://localhost:8090/api/v1/fms/instances/send?fileName=p22&solverType=GENETIC"
```

This path does exercise the gateway's real mapping and the communication between containers, but it
does **not validate feasibility**: that check has to be done separately. It is the path through
which the p23 anomaly was detected, precisely because the cost came out below the BKS.
