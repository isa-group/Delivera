# genetic-engine

**Port 8093** · `com.delivera.fms.engine.genetic` · main class
[`GeneticRouteSolver`](../../engines/genetic-engine/src/main/java/com/delivera/fms/engine/genetic/service/GeneticRouteSolver.java)

It is the production engine and the only one that honours **all** the instance's constraints:
capacity, maximum route duration and fleet size. It is also the only one that actively seeks **one
vehicle per route**; the other two distribute routes among vehicles without looking at how many
trips each accumulates.

Genetic algorithm: population + crossover + mutation, with local search applied
periodically to the best individuals. It uses jMetal 6.6 only for the `PermutationSolution`
representation and the random generator; the algorithm is hand-written.

## Class map

```
service/
  GeneticRouteSolver        evolutionary loop, parameters, decoding
    └ MDCVRPProblem         objective function (inner class): sums the core's per-depot cost
  ProblemMapper             contract DTOs → core model

scheduler/
  PermutationCodec          translation permutation ↔ per-depot order
  RouteScheduler            building the response's RouteDto

operator/crossover/
  BestCostRouteCrossover    BCRC crossover

operator/mutation/
  IntraDepotMutation        reorders within a depot
  InterDepotMutation        reassigns a customer to another depot (diversification)

operator/search/
  LocalSearch               adapter of RouteOptimizer (core) to the chromosome
  InterDepotLocalSearch     adapter of DepotRebalancer (core) to the chromosome

routing-core (shared module, see architecture.md)
  RouteSplitter             optimal split of a sequence into routes  ← the core
  RouteOptimizer            intra-route 2-opt + relocate between routes of the same depot
  DepotRebalancer           reassignment between depots: repair + improvement
```

`RouteSplitter`, route-level local search and inter-depot rebalancing used to live inside this
engine and were extracted to `routing-core` to share them with the simulated annealing engine. The
algorithm did not change:
[`GeneticRegressionTest`](../../engines/genetic-engine/src/test/java/com/delivera/fms/engine/genetic/benchmark/GeneticRegressionTest.java)
pins the seeded cost of `p01`, `p07` and `p22` and gave the same to the tenth decimal before and
after. That test is what allows refactoring while knowing whether the search has changed; if a
change is deliberate, its expected costs are updated in the same commit.

## Solution representation

An individual has **two parts**, because a permutation alone is not enough: it defines an order but
not who serves whom.

**1. The permutation** (`solution.variables()`) - a *giant tour*: the customer indices in some
order, with no marks of where each route starts and ends.

**2. The depot map** (`solution.attributes().get("depotMap")`) - a `Map<Integer, Depot>` saying
which depot each customer belongs to.

```
permutation   [ 7, 2, 9, 4, 1, 5, 8, 3, 6 ]
depotMap      7→D1  2→D1  9→D1  4→D2  1→D2  5→D2  8→D1  3→D2  6→D1

sequence D1   [ 7, 2, 9, 8, 6 ]
sequence D2   [ 4, 1, 5, 3 ]
```

Only the **relative order** of the customers of the same depot matters. Whether they are
interleaved or grouped in the permutation is irrelevant to the cost.

The actual routes **are not stored**: they are derived by splitting each sequence with
`RouteSplitter`.

`PermutationCodec` centralises this translation and offers two ways of rewriting the permutation:

- `writeBack` - keeps the positions each depot occupies. Valid when only the order within depots
  changes.
- `writeBackContiguous` - concatenates the depots. **It is the one to use when some customer has
  changed depot**, because `writeBack` assumes the set of customers of each depot has not changed.

## `RouteSplitter`: the core

It is the only place where a chromosome turns into routes. Being unique is deliberate: **the
objective function and the response returned to the client are exactly the same computation**, and
cannot diverge.

Given a depot's sequence, it finds the **minimum-cost** cut into routes through Prins' dynamic
programming. It is not the same as cutting whenever the vehicle fills up: the greedy cut is easy to
beat.

### The DP

```
cost[0] = 0
for each start position i:
    walk customers forward accumulating load, distance and service time
    stop when one more does not fit by capacity or duration
    for each valid cut i..j:
        cost[j+1] = min(cost[j+1], cost[i] + cost of route i..j)
```

`predecessor[]` allows reconstructing the routes, and `routeCount[]` counts how many were used.

### The fleet limit goes inside the DP

If the minimum-cost split needs more routes than the depot has vehicles, a **second
two-dimensional DP**, `cost[routes][position]`, is relaunched, looking for the best split with at
most `fleet` routes.

This is important and not a detail: if the fleet penalty were applied *after* the split, the cut
could not trade a bit of distance for one route less, and every new split — the local search's,
each evaluation's — would undo the repair the inter-depot search had made. See
[decisions-and-fixes.md](../decisions-and-fixes.md).

The **route segments are precomputed once** (`buildSegments`) and shared by both DPs, because the
bounded DP walks them once per vehicle count. This nearly halved the time on the large instances.

### When not even the bounded DP fits the fleet

There may be no split of that sequence with `fleet` routes or fewer: with service times and tight
duration, the customers assigned to that depot do not fit in its vehicles in a single round. It
happens on `pr04`, `pr05`, `pr06` and `pr10`, and not in every run.

Then the split falls back to the minimum-cost one and the fleet penalty makes sure that solution
does not beat one that does fit. If it still survives to the end, `RouteScheduler` distributes the
extra routes **among the vehicles that exist**, in turns: the fifth route of a depot with four
vehicles is the first one's second trip.

What it does **not** do is make up a vehicle. It used to — it generated `V-GA-<depot>-<n>`
identifiers above the declared fleet — and that was a mistake: the fleet is a problem datum, and a
solution that enlarges it is solving another problem. A second trip is debatable; enlarging the
fleet is not.

The order of preference is therefore: one vehicle per route → second trip → never a new vehicle.
The first three mechanisms (bounded DP, penalty, inter-depot repair) exist so that the first option
is almost always the one that comes out.

### Duration and service time

A route's duration is `distance + sum of service times`. The **cost** is distance only. They are
different things and the DP carries them separately.

A customer whose demand exceeds capacity, or whose round trip already exceeds the maximum duration,
forms its own route with a penalty, instead of leaving the sequence without a possible split.

### Insertion deltas

`RouteSplitter` also offers `insertionCost`, `removalGain` and `bestPosition`, which **all**
operators must use. They compute the delta *within a depot's sequence*, with the depot as the
endpoint:

```
insert c between a and b   →   d(a,c) + d(c,b) − d(a,b)
```

Measuring against the neighbours in the global permutation would be a mistake: it would value edges
between customers of different depots, which exist in no real route.

## Objective function

`MDCVRPProblem.evaluate`, per depot:

```
cost = cost of the optimal split
     + FLEET_PENALTY × (routes − vehicles)   if over the fleet
```

`FLEET_PENALTY` and `DURATION_PENALTY` are 1000, well above the cost of a typical route (~160 on
p22), so that an infeasible solution never beats a feasible one.

Penalties affect the **fitness**, not the response's `totalCost`, which is always real distance
travelled.

## Operators

### `BestCostRouteCrossover` (BCRC)

Probability **0.9**. Generates two children per crossover, swapping the parents' roles.

```
1. Pick a depot at random from the donor parent and split its sequence
2. Extract ONE of its routes
3. Copy the receiving parent (the child inherits ITS customer-depot assignment)
4. Remove the extracted customers from the child
5. Reinsert each in the cheapest position of the sequence
   of the depot the receiver assigns it to
```

It matters that **one route** (about 10 customers) is extracted and not all the depot's customers:
extracting the whole depot would turn the crossover into a greedy reconstruction that inherits no
structure from either parent.

The child inherits the **receiver's** `depotMap`, which is the one consistent with the permutation
it is built on.

### `IntraDepotMutation`

Probability **0.2**, decided per individual. If triggered, it applies to the sequence of **every**
depot one of three operators chosen at random:

- **swap** - exchanges two customers.
- **invert** - reverses a segment.
- **relocate** - moves a customer to another position.

It works on the depot's sequence, not on the permutation's contiguous blocks. A depot whose
customers are already grouped is precisely the case that must be mutable.

### `InterDepotMutation`

Probability **0.3**, applied only to individuals already selected by the main loop (10 at random,
every 5 generations). It is a **diversification** operator, not an improvement one:

```
1. Locate the "border" customers: those with another depot within
   BORDER_RATIO = 1.3 times the distance of their current depot
2. Pick one at random
3. Pick at random a destination depot among the nearby ones
4. Insert it in its cheapest position within that depot
```

The destination is chosen **at random** among the nearby ones, not always the second closest,
which would give a deterministic and almost always identical move. It does not check whether the
change improves: the next generation's selection takes care of that. What it does check is that
the destination depot has enough fleet to absorb the demand.

### `LocalSearch`

**Route-level** local search. It splits each depot's sequence into real routes *before*
optimising, so each move is evaluated against the cost the solution will really have.

- **Intra-route 2-opt** - reverses a segment if it shortens the route. After accepting an inversion
  it restarts the sweep, because the cached edges stop being valid as soon as the segment is
  reversed.
- **Relocate between routes** of the same depot - moves a customer to the best position of another
  route, checking capacity.

Up to `MAX_PASSES = 8` passes or until there is no improvement.

> Optimising the giant tour as if it were a TSP and splitting it afterwards is **not** equivalent:
> it can shorten the tour and raise the final cost after the cut.

Adding a *swap* of customers between routes was tried and discarded: it did not measurably improve
the result and added code.

### `InterDepotLocalSearch`

It does two things that are deliberately kept separate:

**1. Repair** (`repairFleet`). If a depot needs more routes than it has vehicles, load is taken out
of it **even at a distance cost**. Acceptance is not conditioned on improving the cost: there may
be no single move that pays off, and an "only if it improves" criterion would stay trapped. For
each candidate **all** insertion positions of the destination are swept, because the number of
routes depends on where the sequence is cut and the cheapest position by distance may be precisely
the one that adds a route.

**2. Improve** (`applyBestMove`). Relocates border customers when it reduces the real cost of the
two depots involved. The estimated delta only serves to rank candidates; before accepting, the real
split of both depots is recomputed and the move is undone if it does not improve. Up to
`MAX_CANDIDATES = 25` candidates are verified instead of giving up when the first fails.

## Main loop

```
initialise population (20 % randomised heuristic, 80 % random)
evaluate

while evaluations or minimum generations remain:

    elites ← copies of the ELITISM_COUNT best DISTINCT

    repeat until the offspring is full:
        select 2 parents by tournament of 3 (avoiding the same one)
        crossover  → 2 children
        intra-depot mutation on each child

    evaluate offspring

    every INTER_DEPOT_FREQUENCY generations:
        inter-depot mutation on INTER_DEPOT_INDIVIDUALS random children

    replace the worst with the elites
    population ← offspring

    every LOCAL_SEARCH_FREQUENCY generations:
        local search on the TOP_K_LOCAL_SEARCH best

    every INTER_DEPOT_OPT_FREQUENCY generations:
        inter-depot local search on a copy of the best
        if it improves, inject it replacing the worst

    update the best-so-far (always as an independent COPY)

    if RESTART_STAGNANT generations have passed without improvement:
        restart the population, or stop if the restarts are used up

final polish of the best: inter-depot + local search
decode into routes
```

Two details that matter:

- The **best-so-far is always stored as a copy**. If it were a reference to an individual of the
  population, local search would mutate it underneath and could degrade it without the algorithm
  noticing.
- **Elitism copies the `k` best distinct ones**, not `k` times the best. Injecting `k` clones of
  the same genotype every generation causes premature convergence.

## Parameters

Configurable per request, in the `parameters` map. The defaults are those of
[`GeneticParameters.DEFAULTS`](../../engines/genetic-engine/src/main/java/com/delivera/fms/engine/genetic/service/GeneticParameters.java)
and are also declared in the solver's metadata, which is what the gateway applies when the client
does not send them (see [solver-metadata.md](../solver-metadata.md)). **If you change one, change it
in both places**: the descriptor would be announcing a run that is not the one that happens.

| Parameter | Default | Range | Meaning |
|---|---|---|---|
| `populationSize` | 150 | 10–2000 | Individuals per generation |
| `maxEvaluations` | 75000 | 1000–5·10⁶ | Evaluation budget (~500 generations) |
| `minGenerations` | 100 | 1–100000 | Floor before allowing a restart |
| `crossoverProbability` | 0.9 | 0–1 | BCRC crossover probability |
| `intraDepotMutationProbability` | 0.2 | 0–1 | Probability of mutation within the depot |
| `interDepotMutationProbability` | 0.3 | 0–1 | Probability of reassignment between depots |
| `elitismCount` | 5 | 0–100 | Best distinct ones kept each generation and after a restart |
| `tournamentSize` | 3 | 2–20 | Individuals per selection tournament |
| `localSearchFrequency` | 10 | 1–1000 | Every how many generations local search runs |
| `interDepotFrequency` | 5 | 1–1000 | Every how many generations inter-depot mutation runs |
| `restartStagnantGenerations` | 20 | 1–10000 | Generations without improvement before restarting |
| `maxRestarts` | 3 | 0–100 | Restarts allowed; at the next stagnation it stops |
| `heuristicSeedRatio` | 0.2 | 0–1 | Fraction of the initial population built with the heuristic |
| `seed` | - | 0 - 2^(48) - 1 | Generator seed. Without it the engine draws one and returns it |

`seed` is the only one without a default, on purpose: fixing one would make the engine always
deterministic, which is just the opposite of what `deterministic: false` declares. Nothing to do with
`heuristicSeedRatio`, which is about seeding the initial population.

The upper bound is not arbitrary. `JavaRandomGenerator` wraps a `java.util.Random`, which keeps
**48 bits** of the seed (`(seed ^ 0x5DEECE66D) & (2⁴⁸−1)`). Since the XOR with a constant is a
bijection over those bits, the range `[0, 2⁴⁸)` covers every possible stream, each exactly once:
none is missing and no two seeds give the same sequence. Above it they would start overlapping, and
negative ones fall on that same space without adding anything new.

These remain constants of
[`GeneticRouteSolver`](../../engines/genetic-engine/src/main/java/com/delivera/fms/engine/genetic/service/GeneticRouteSolver.java),
for lack of a measured experimental range:

| Constant | Value | Meaning |
|---|---|---|
| `INTER_DEPOT_INDIVIDUALS` | 10 | How many children receive the inter-depot mutation (indices with repetition) |
| `INTER_DEPOT_OPT_FREQUENCY` | 30 | Every how many generations the inter-depot search runs |
| `TOP_K_LOCAL_SEARCH` | 3 | How many individuals get local search |
| `SEED_CANDIDATE_LIST` | 3 | Candidates of the randomised nearest neighbour |

Constants in other classes:

| Constant | Class | Value | Meaning |
|---|---|---|---|
| `FLEET_PENALTY` | `RouteSplitter` | 1000 | Surcharge per route exceeding the fleet |
| `DURATION_PENALTY` | `RouteSplitter` | 1000 | Surcharge per route that does not fit the duration |
| `MAX_PASSES` | `RouteOptimizer` | 8 | Maximum passes of 2-opt + relocate |
| `BORDER_RATIO` | `InterDepotMutation`, `DepotRebalancer` | 1.3 | Threshold to consider a customer a "border" one |
| `MAX_ITERATIONS` | `DepotRebalancer` | 10 | Improvement moves per call |
| `MAX_REPAIR_MOVES` | `DepotRebalancer` | 20 | Fleet repair moves |
| `MAX_CANDIDATES` | `DepotRebalancer` | 25 | Candidates verified before giving up |

### What really ends the run

`MAX_EVALUATIONS` is **almost never reached**. What cuts it is the stagnation criterion: the first
restart cannot happen before generation 100, and then 20 stagnant generations are needed for each
of the 3 restarts. In practice runs end between generation 160 and 210, with 25,000–33,000
evaluations. `MIN_GENERATIONS` never gets to act either.

If you want to experiment looking for better solutions, the parameters with room are
`restartStagnantGenerations` and `maxRestarts`, not `maxEvaluations`. It is measured: raising the
budget or the local search frequency does not move the cost.

The `evaluations` counter is **approximate**: local search adds 3 when it actually performs
thousands of distance computations, and the greedy crossover adds nothing.

## Initial population

- **20 % heuristic** - **randomised** nearest neighbour: at each step it picks at random among the 3
  closest customers. Without that randomisation the 30 individuals would come out identical.
- **80 % random** - shuffled permutations.

In both cases the `depotMap` assigns each customer to its nearest depot. That initial assignment
**may need more routes than vehicles** on some instances, and it is the inter-depot search that
repairs it.

## Performance

Over the 33 Cordeau instances: between 0.6 s and 12 s, most under 3 s. The slowest are `pr06` and
`pr10`, where the duration constraint forces launching the bounded DP often.

The engine is **sequential**: it does not use more than one core.

## Known limitations

- **It is not reproducible**: `JMetalRandom` starts from a clock-derived seed that cannot be fixed
  from the request. Two identical runs give different costs, so a small difference between two
  configurations may be chance; to compare, repeat several times and look at means.
- **It does not support time windows.**
- **Heterogeneous fleet**: it takes the largest capacity of each depot, so it does not honour
  different capacities customer by customer.
- The cost reaches a plateau it does not leave by raising the budget. To reduce the gap further,
  diversity management (*path relinking*, granular neighbourhoods) would be needed.
