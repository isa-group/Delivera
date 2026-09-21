# annealing-engine

**Port 8094** · `com.delivera.fms.engine.annealing` · main class
[`AnnealingRouteSolver`](../../engines/annealing-engine/src/main/java/com/delivera/fms/engine/annealing/service/AnnealingRouteSolver.java)
· algorithm
[`SimulatedAnnealing`](../../engines/annealing-engine/src/main/java/com/delivera/fms/engine/annealing/algorithm/SimulatedAnnealing.java)

Simulated annealing: a **single-solution** metaheuristic that starts from one solution and keeps
modifying it with random moves, always accepting those that improve and, with probability
`exp(-Δ/T)`, also those that worsen. The temperature `T` drops over time, so the search goes from
exploring to refining. It is the Metropolis criterion of **Kirkpatrick, Gelatt and Vecchi (1983)**;
the neighbourhoods are the multi-depot adaptation of **Osman's (1993)** *λ-interchange*.

It is built on jMetal 6.6's `AbstractLocalSearch` template: one step of the template is one
temperature level. jMetal provides the template and the random generator; the acceptance criterion,
the cooling and the neighbourhoods are hand-written.

It shares with the genetic engine the [`routing-core`](../architecture.md#the-shared-core): the
optimal split, the route-level local search and the inter-depot rebalancing. **That is not reuse,
it is experimental control**: both engines measure cost with exactly the same code, so a difference
between them is attributable to the search strategy and not to measuring different things.

## What sets it apart from the other engines

| | genetic-engine | annealing-engine |
|---|---|---|
| Population | 150 individuals | 1 solution |
| Stopping criterion | stagnation and restarts | **time budget** |
| Reproducible | yes, with `seed` | only with `seed` **and** `maxLevels` |
| Anytime curve | no | **yes**, in the `trace` field |

The time budget is the difference that matters. The genetic engine ends when it stagnates, however
long that takes; the annealing one is told how much time it has and uses all of it. That is what
allows asking it *"what cost do you give with 500 ms?"* and comparing engines by their
**cost/time frontier** rather than by final cost alone.

## Class map

```
service/
  AnnealingRouteSolver     entry point: parameters, initial solution, final polish, decoding
  AnnealingParameters      configuration of one run
  ProblemMapper            contract DTOs → core model

algorithm/
  SimulatedAnnealing       the annealing, on jMetal's AbstractLocalSearch<AnnealingSolution>

solution/
  AnnealingSolution        per-depot sequences + assignment + cached costs
  Neighborhood             generates the neighbour, keeps what is needed to undo it
```

Everything else — `RouteSplitter`, `RouteOptimizer`, `DepotRebalancer` — comes from `routing-core`.

## Representation

The same as the genetic engine's — order per depot plus customer-depot assignment — but **without
the global permutation**, which there exists only because of jMetal's representation. Here the
per-depot sequence is the canonical form and the assignment is derived from it.

```
sequence D1   [ 7, 2, 9, 8, 6 ]
sequence D2   [ 4, 1, 5, 3 ]
depotOf       7→0  2→0  9→0  8→0  6→0  4→1  1→1  5→1  3→1
```

Routes are not stored: they are derived by splitting each sequence with `RouteSplitter`. That both
engines walk **the same solution space** is deliberate: it isolates what is meant to be compared.

## The objective function is exact, and why that is affordable

Every move is evaluated with the real optimal split, not with a delta over the sequence. A delta
would be much cheaper but **does not see where the cut into routes will fall**, which is where the
cost comes from; optimising the sequence and splitting afterwards is not equivalent to optimising
the split's result.

That evaluating for real is affordable depends on a single idea: **a move touches one or two
depots**, so only those are re-split and the rest keep their cached cost.

```
AnnealingSolution
  depotCost[d]   penalised cost of each depot
  totalCost      the sum, maintained by differences
```

`Neighborhood` keeps a copy (`DepotBackup`) of each depot **right before** touching it. That copy
does two things: it says what to recompute, and it allows undoing if the move is rejected. Measured:
a complete move — propose, evaluate, undo — costs 0.9 µs on `p01` (13 customers per depot), 4 µs on
`p22` (40) and 9 µs on `pr06` (60-90). The split is 70 % of that.

An invariant test
([`NeighborhoodInvariantTest`](../../engines/annealing-engine/src/test/java/com/delivera/fms/engine/annealing/solution/NeighborhoodInvariantTest.java))
checks, after thousands of moves and undos, that every customer is in exactly one depot, that the
cached assignment matches the sequences, that the cost maintained by differences equals the one
recomputed from scratch, and that undoing returns exactly to the previous state.

## Neighbourhoods

A move picks a customer at random — which weights depots by their size — and applies:

**Intra-depot** (reorder a sequence; the split decides where the cuts fall):

- **relocate** — moves the customer to another position.
- **swap** — exchanges it with another one.
- **reverse** — reverses the segment between the two.

**Inter-depot** (the only ones that change the distribution, which is where quality is won in
MD-CVRP):

- **relocate** — takes it out of its depot and puts it in another, in its cheapest position.
- **swap** — exchanges the depot of two customers. It does not change how many customers each depot
  has, so it keeps working once the distribution is already fitted to the fleet.

Their proportion is set by `interDepotMoveProbability`. The destination depot is chosen among the
ones **near** the customer: those no farther than `depotCandidateRatio` times the distance of the
nearest one. Moving a customer to the other end of the map is a move no acceptance criterion will
admit, and proposing it only wastes iterations. The list is precomputed once because it depends on
the geometry, not on the assignment.

The neighbourhood is good on its own: a **pure descent** with these moves (accepting improvements
only) takes `p22` from the initial solution to 5974 in 0.85 s, already below the genetic engine.
What annealing adds is the ability to leave that valley.

## Temperature

### Initial: calibrated, not fixed

`WARMUP_SAMPLES = 1000` moves are sampled from the starting solution (all undone) and a **low
quantile** of the worsening ones is taken, `calibrationQuantile` (5 % by default): a *small*
worsening, the kind the search really needs to accept to leave a local optimum. The initial
temperature is the one that accepts such a worsening with `initialAcceptanceRate`:
`T0 = -Δ / ln(p0)`.

An absolute value would not be transferable: the cost of a move depends on the coordinate scale and
the instance size, so a temperature good for `p01` would be absurd for `p22`.

**A low quantile and not the median, and this changes the result more than any other parameter.**
From a local optimum almost every random move worsens a lot (relocating a customer to a random
position is almost always terrible), so the median is a huge scale. At that temperature the chain
settles at an equilibrium cost far above the starting point — on `p22`, ~8600 at `T = 68` against
an initial 6595 — and, since the final temperature is derived from the initial one, **it never gets
low enough to intensify**. The result was that the best never improved on the initial solution.
Measured on `p22`: 12 % gap with the median, 2.5 % with the 5 % quantile.

Only **distance** worsenings count (below half a fleet penalty). Those carrying a penalty are a
thousand times larger than a normal move and, on an instance whose initial solution does not fit the
fleet (`p22` starts with 9-13 routes per depot for a fleet of 5), they are also the majority:
counting them pushed the temperature to ~1000 and the annealing accepted everything.

### Cooling and reheating

Geometric: at the end of each level, `T ← coolingRate × T`. A level is
`movesPerTemperatureFactor × customers` moves, proportional to the size on purpose.

The minimum temperature is also derived: it is the one that accepts that same small worsening with
`finalAcceptanceRate`. With the defaults, `T_min = 0.13 × T0` and a cooling cycle lasts ~49 levels.
On reaching it the search **reheats** to half the initial temperature and restarts from the best
known solution.

**There is no reheating on stagnation.** It was tried (reheat after 25 levels without improving the
best) and it was counterproductive: it fired *before* a cycle reached the minimum temperature, so
the search never cooled down and did not intensify. Measured on `p01`: 36 % overall acceptance with
it, against the expected 6 %.

There is no cap on reheats: the only stopping criterion is time (or `maxLevels`).

## Feasibility

There are two mechanisms here and they should not be confused.

### The penalty guides, it does not decide

`RouteSplitter` **tolerates** two things by charging 1000 of penalty: a single-customer route that
does not fit the duration, and more routes than vehicles. That penalty is the gradient that pushes
the search towards feasibility, and annealing follows it well when the excess of routes is a matter
of **order**: on `p22` it goes from 9-13 routes per depot to the fleet's 5 within the first second,
because every reordering that packs better removes a route and is accepted at once.

But **it is not valid as a criterion for accepting a result**: with a penalty of 1000 on a total
cost of 6000, a cheap infeasible solution can end up below an expensive feasible one and win the
comparison. That is why the engine keeps separately the **best feasible solution seen** — verified
with `RouteSplitter.isFeasible`, which materialises the routes and checks capacity, duration and
fleet — and that is the one it returns. Only if none appeared during the whole run does it repair
the best it has before giving up.

The check is done once per level, not on every improvement: the best-so-far only improves, so
looking at it there captures the same state without splitting every depot thousands of times.

### Rebalancing repairs what annealing cannot

There are instances — `p07`, `p11`, `pr06` — where the excess of routes is a matter of
**assignment**: a depot has more customers than its fleet can serve however they are ordered, and
getting out of that requires a **chain** of relocations to other depots in which only the last one
removes a route. The Metropolis criterion values moves one at a time, so the probability of walking
the whole chain is the product of the individual ones: it stays trapped except by chance.

That is why `DepotRebalancer` — the same one the genetic engine uses — is called on the starting
solution and every `rebalanceFrequency` levels. With one difference: the genetic engine confirms
with a split **every** insertion position of the destination, which is exact but quadratic (on
`pr06` one pass cost 2 to 8 s, more than the whole budget); the annealing engine confirms only the
**three cheapest by distance** (`REPAIR_INSERTION_CANDIDATES`). It loses the guarantee of finding
the one that adds the fewest routes, but the fine ordering is refined afterwards by the annealing
itself, and `pr06` goes from being infeasible at 13 s to being feasible within the first second.

The auxiliary phases have a deadline: the initial repair cannot consume more than a quarter of the
budget, and the periodic rebalancing is cut off when it runs out, returning what was done so far.

## Main loop

```
initial solution: customer to nearest depot + randomised nearest neighbour
rebalance and polish, up to 5 passes or a quarter of the budget
calibrate the initial and minimum temperatures

while time (or levels) remain:

    level: for each of factor × customers moves
        propose a neighbour, keeping a copy of the depots it touches
        recompute only those depots
        accept if it improves, or with probability exp(-Δ/T); otherwise undo
        record the best

    T ← coolingRate × T
    if the best is feasible and better than the best feasible: record it and add a trace point
    every rebalanceFrequency levels:   rebalance between depots
    every localSearchFrequency levels: 2-opt and relocate between routes
    if T < T_min: reheat to T0/2 and return to the best known solution

final polish of the result (rebalancing + local search); if there never was a feasible one, repair
return the best FEASIBLE solution seen
```

## Parameters

Configurable per request, in the `parameters` map. The defaults are those of
[`AnnealingParameters.DEFAULTS`](../../engines/annealing-engine/src/main/java/com/delivera/fms/engine/annealing/service/AnnealingParameters.java)
and are also declared in the solver's metadata. **If you change one, change it in both places**:
the descriptor would be announcing a run that is not the one that happens.

| Parameter | Default | Range | Meaning |
|---|---|---|---|
| `timeLimitMs` | 5000 | 100–300000 | Time budget. Main stopping criterion |
| `maxLevels` | 0 | 0–10⁶ | Level cap; 0 = time alone rules. What makes a run reproducible |
| `calibrationQuantile` | 0.05 | 0.01–0.5 | Which quantile of the sampled worsenings is the "small worsening" |
| `initialAcceptanceRate` | 0.4 | 0.01–0.99 | With what probability that worsening is accepted at the start |
| `finalAcceptanceRate` | 0.001 | 10⁻⁶–0.5 | Below that probability the system is cold and reheats |
| `coolingRate` | 0.96 | 0.5–0.9999 | Cooling factor per level |
| `movesPerTemperatureFactor` | 12 | 1–1000 | Moves per level **and per customer** |
| `interDepotMoveProbability` | 0.25 | 0–1 | Fraction of moves that change a customer's depot |
| `depotCandidateRatio` | 1.3 | 1–100 | How much farther than the nearest a destination depot may be |
| `localSearchFrequency` | 8 | 0–10000 | Every how many levels 2-opt and relocate are applied; 0 = only at the end |
| `rebalanceFrequency` | 20 | 0–10000 | Every how many levels rebalancing between depots runs; 0 = only at start and end |
| `seed` | - | 0 – 2⁴⁸−1 | Seed. Without it the engine draws one and returns it |

Constants without a measured experimental range, in `SimulatedAnnealing` and
`AnnealingRouteSolver`:

| Constant | Value | Meaning |
|---|---|---|
| `WARMUP_SAMPLES` | 1000 | Sampling moves for temperature calibration |
| `REHEAT_FACTOR` | 0.5 | Fraction of the initial temperature to reheat to (0.5, 1 and 2 give the same within noise) |
| `CLOCK_CHECK_INTERVAL` | 512 | Every how many moves the clock is checked within a level |
| `INITIAL_REPAIR_PASSES` / `_SHARE` | 5 / 4 | Initial repair passes and fraction (1/4) of the budget they may spend |
| `SEED_CANDIDATE_LIST` | 3 | Candidates of the randomised nearest neighbour |
| `REPAIR_INSERTION_CANDIDATES` | 3 | Insertion positions the rebalancer confirms |
| `FINAL_REPAIR_PASSES` | 5 | Last-resort repair passes |

### `localSearchFrequency = 0` and `rebalanceFrequency = 0` are the ablation

With both at zero the engine is **pure annealing** (the two phases only act at start and end). It
is what separates what the Metropolis criterion contributes from what local search contributes,
which is the comparison to make before crediting the result to either. A first measurement with a
single seed: on `p22` pure annealing gives 1.9 % and the hybrid 3.3 %; on `p01` both give 2.4 %.
With the temperature properly calibrated, periodic local search contributes little or nothing on
those two instances. **It must be repeated with several seeds before concluding it.**

## The anytime curve

The response includes a `trace` field: the instants at which the best feasible solution improved.

```json
"trace": [
  { "elapsedMs": 103,  "cost": 6595.14 },
  { "elapsedMs": 652,  "cost": 6364.21 },
  { "elapsedMs": 1817, "cost": 5795.25 },
  { "elapsedMs": 1818, "cost": 5786.42 }
]
```

It allows answering *what cost the engine would have given with a smaller budget* **without
running it again**, which is what is needed to compare engines by their cost/time frontier. The
gateway lets it through untouched; engines that do not search incrementally return it as `null`.

## Results

With the default 5 s budget and seed `20260914`, against the BKS. In the last column, the genetic
engine with that same seed (from its regression test) where measured:

| Instance | Cost | BKS | *Gap* | Feasible | GENETIC |
|---|---:|---:|---:|---|---:|
| `p01` | 590.45 | 576.87 | 2.4 % | yes | 609.87 (5.7 %) |
| `p03` | 647.36 | 641.19 | **1.0 %** | yes | |
| `p07` | 904.42 | 885.80 | 2.1 % | yes | 941.56 (6.3 %) |
| `p11` | 3759.94 | 3554.18 | 5.8 % | yes | |
| `p15` | 2596.05 | 2505.42 | 3.6 % | yes | |
| `p22` | 5887.84 | 5702.16 | 3.3 % | yes | 6030.19 (5.8 %) |
| `pr06` | 3328.26 | 2676.30 | 24.4 % | yes | |

> These figures are from **one run per instance** with a fixed seed. To compare configurations,
> repeat with several seeds and look at means, or fix `maxLevels` and compare without clock noise.

**The spread between seeds is not negligible.** Three seeds on `p22`: 1.8 %, 4.2 % and 2.3 %; on
`p11`: 6.6 %, 4.7 % and 7.4 %. On `p01`, one seed reaches the exact BKS (576.87) and others stay at
585.00 or 590.45, three very stable local optima. A two-point difference between two configurations
may be chance; in fact, sweeping `movesPerTemperatureFactor` (4, 6, 12) and `coolingRate`
(0.92, 0.96) gave no difference outside the noise.

## Known limitations

- **It is not reproducible with time-based stopping.** Two runs with the same seed make a different
  number of moves depending on the machine load. With a fixed `maxLevels` it is, and that is what
  [`AnnealingRegressionTest`](../../engines/annealing-engine/src/test/java/com/delivera/fms/engine/annealing/benchmark/AnnealingRegressionTest.java)
  uses.
- **It may overrun the budget** by a few tens of milliseconds: the auxiliary phases have a deadline,
  but the final polish does not. If no feasible solution appeared during the whole run, the final
  repair may take longer; on the 33-instance bench it has not happened.
- **`pr06` and the instances with service times stay far off.** With a saturated fleet (6/6 in all
  four depots) almost any structural change adds a route. With 20 s the *gap* drops from 24 % to
  11.5 %, so what is missing there is budget, not algorithm.
- **It is sequential**: it does not use more than one core. Annealing admits independent parallel
  runs with different seeds keeping the best, which is the most direct route if it were needed.
- **It does not support time windows.**
- **Heterogeneous fleet**: it inherits from the core the capacity of the largest vehicle of each
  depot.

## How to calibrate

[`AnnealingCalibrationTest`](../../engines/annealing-engine/src/test/java/com/delivera/fms/engine/annealing/benchmark/AnnealingCalibrationTest.java)
runs the engine locally on Cordeau instances and prints cost, *gap*, feasibility and trace. It
asserts nothing; it only runs on demand. Any parameter can be passed as a property:

```bash
mvn test -Dtest=AnnealingCalibrationTest -Dcalibration=true -Dinstances=p01,p22 -Dseed=1 -DcalibrationQuantile=0.1
```

With `-Dannealing.log=TRACE` it prints per level the temperature, the current cost and the best:
that is what lets you see whether the chain settles below the best when cooling, which is the check
that uncovered the median problem.
