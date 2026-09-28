# Decisions and fixes in the genetic engine

Record of the problems found while reviewing the genetic engine and of why they were solved the way
they were. It serves to avoid reintroducing the same faults and to understand why the code has the
shape it has.

**Starting point:** p22 gave 6737.95 against a BKS of 5702.16 (17.9 % gap) and the solution **was
infeasible**: route `V4-2` carried a load of 72 with vehicles of capacity 60.

**Current state:** ~5960 on p22 (4.5 % gap) and all 33 instances feasible.

---

## 1. The split was greedy, not optimal

**What happened.** The objective function walked the giant tour and cut as soon as the vehicle was
full. That cut is easy to beat: for the same customer order there is a cheaper split.

**Why it matters.** Measured on p22: same order, greedy cut 6688 versus optimal cut 6565.

**How it was solved.** `RouteSplitter` with Prins' dynamic programming. It is now the **only** place
where a chromosome turns into routes, used by both the objective function and the response decoder,
so they cannot diverge.

## 2. Local search optimised a tour, not routes

**What happened.** 2-opt was applied to each depot's giant tour, as if it were a TSP, and the
capacity split came afterwards.

**Why it matters.** Shortening the tour does **not** amount to cheapening the routes: a 2-opt can
shorten the tour and raise the real cost once cut. Measured: moving to route-level local search went
from 6565 to 6378.

**How it was solved.** `LocalSearch` splits into real routes first and then applies intra-route
2-opt and *relocate* between routes of the same depot, evaluating each move against the real cost.

## 3. Insertion costs blind to route and depot boundaries

**What happened.** The operators computed `d(prev,c) + d(c,next) − d(prev,next)` using the
neighbours in the **global permutation**, without checking that they belonged to the same depot.

**Why it matters.** Edges between customers of different depots, which exist in no route, were
being valued. The greedy placed customers in positions that *looked* cheap and were not.

**How it was solved.** `RouteSplitter.insertionCost` / `removalGain` / `bestPosition`, which
measure within the depot's sequence with the depot as the endpoint. All operators use them.

## 4. 2-opt with stale edges

**What happened.** The cost of the initial edge was computed outside the inner loop. When an
inversion was accepted, the segment changed but the cached variable was not recomputed, so from the
first improvement on **the deltas were wrong and worsening moves were accepted**.

**How it was solved.** After accepting an inversion the sweep restarts, recomputing the edges.
Routes are short (~10 customers), so the extra cost is irrelevant.

## 5. The best-so-far degraded by itself

**What happened.** `bestSolution` held a **reference** to an individual of the population. Local
search later mutated that same object *in place*. If it made it worse, `bestFitness` kept the old
good value while the referenced object was worse, and at the end the degraded version was decoded.

**How it was solved.** `bestSolution` is always an independent copy.

## 6. Elitism injected clones

**What happened.** The 5 worst offspring were replaced by 5 copies **of the same** individual.

**Why it matters.** With a tournament of size 3, injecting 5 clones of the same genotype every
generation causes premature convergence. It showed: the run stalled around generation 120.

**How it was solved.** The `k` best **distinct** ones are copied.

## 7. The "heuristic" 20 % of the population was 30 identical individuals

**What happened.** The heuristic seeding was a deterministic nearest neighbour, starting from the
same customer-depot assignment for everyone. It received a random generator and **did not use
it**. The 30 individuals were identical, and on every restart 29 more identical ones were generated.

**How it was solved.** Randomised nearest neighbour: at each step one of the 3 closest customers is
chosen at random (`SEED_CANDIDATE_LIST`).

## 8. The crossover extracted a whole depot, not a route

**What happened.** BCRC extracted **all** the customers of a depot (~40) instead of one route
(~10), and reinserted them greedily.

**Why it matters.** That way the crossover inherits no structure from either parent: it degenerates
into a greedy reconstruction. And it cost some 14,400 insertion evaluations per child.

**How it was solved.** The donor depot's sequence is split and **one** of its routes is extracted.

## 9. The crossover corrupted half the children

**What happened.** The child was built by copying parent 2, thereby inheriting its customer-depot
assignment, which is the one consistent with the resulting permutation. Right after, the main loop
**replaced it with parent 1's**.

**Why it matters.** The assignment no longer matched the freshly built order. Half the children of
each generation were born corrupt.

**How it was solved.** That replacement was removed.

## 10. Intra-depot mutation disabled itself

**What happened.** It required the depot to have at least 2 **contiguous blocks** in the
permutation. A depot whose customers were grouped together — which is the desirable state, and the
one crossover and local search tend towards — **never mutated**.

**How it was solved.** It operates on the depot's sequence, with the condition on the number of
customers, not blocks.

## 11. `phase2Improve` produced infeasible routes

**What happened.** The last step before returning the response moved the last customer of a route
to the start of the next one if it lowered the distance, **without checking capacity**.

**Why it matters.** It was the direct origin of route `V4-2` with a load of 72 over a capacity of
60. Moreover, by running only in the decoding, the fitness guiding the search and the reported cost
were different functions.

**How it was solved.** It was removed. The decoder now uses the same optimal split as the objective
function.

## 12. Nearest-depot assignment is infeasible on some instances

**What happened.** It is not a bug of the previous code but a property of the instances, which
surfaced when feasibility started being validated. On `p07` the nearest-depot assignment leaves 412
load units in a depot with 4 vehicles of capacity 100; on `p11`, 3041 with 6 of 500.

**Why it is hard.** There is **no single move** that improves the penalised cost: taking a customer
out raises the distance and, if it does not manage to remove a route, it does not pay off. An
"accept only if it improves" criterion stays trapped indefinitely.

**How it was solved.** `InterDepotLocalSearch` separates **repairing** from **improving**. Repair
takes load out of the overloaded depot **with no cost condition**, choosing the cheapest move among
those that leave the destination within its fleet.

## 13. The number of routes depends on where the cut falls, not only on the load

**What happened.** Repair kept failing even when trying every candidate. The reason: only **one**
insertion position in the destination was tried, the cheapest by distance, and that position may be
precisely the one that splits the sequence so that the destination needs one more route.

A depot with load below its total capacity may need extra routes if the sequence cannot be cut
well: the cuts have to be contiguous.

**How it was solved.** When repairing, **all** insertion positions of the destination are swept,
measuring the real split of each.

## 14. The fleet limit had to be inside the split

**What happened.** With duration now active, `p08` needed more routes and one depot went over: 15
routes with 14 vehicles, with 27 of 28 vehicles used in total. Repair worked, but **any later
re-split undid it**.

**Why.** With the penalty applied *after* the cut, the split cannot trade a bit of distance for one
route less: it minimises distance, full stop. Every evaluation and every local search pass split the
sequence again and produced 15 routes again.

**How it was solved.** If the minimum-cost split exceeds the fleet, a **two-dimensional DP**
`cost[routes][position]` bounded by the number of vehicles is relaunched. It is the most general
lesson of the whole review: **a constraint the penalty cannot enforce has to be inside the model
that decides**, not outside it.

The route segments are precomputed once and shared by both DPs, because the bounded one walks them
once per vehicle count. That brought `pr06` down from 11.8 s to ~7 s and `pr10` from 12.8 s to
~6 s.

## 15. `max_duration` was not reaching the engine

**How it was detected.** A Postman query returned **5935** on `p23`, whose BKS is **6095.46**. Being
below the BKS is impossible without violating something.

**What happened.** The parser read `max_duration` and stored it in `DepotConfig`, but the mapper
did not propagate it and no DTO had the field. Since p21, p22 and p23 are the same instance with
duration 0, 200 and 180, the engine solved p21 in all three cases. Same with p12–p14, p15–p17 and
p18–p20: **22 of the 33 instances** were affected.

**How it was solved.** `DepotDto.maxDuration`, optional, propagated from the gateway and applied
in the split DP. The greedy and random engines were not touched: Spring ignores unknown properties
by default.

## 16. Service times were missing too

**What happened.** While fixing the above it was seen that the 10 `prXX` instances have non-zero
`service_duration`. The duration limit includes them, so they were still being measured wrongly.

**The nuance that matters.** Service time consumes **route duration** but does **not** add to
**cost**. If it were accumulated into the cost, the objective would no longer be comparable with
the BKS.

**How it was solved.** `CustomerDto.serviceDuration`, optional, and in `RouteSplitter` distance and
duration are accumulated separately.

---

## 17. Randomness came from a process-wide singleton

**What happened.** All operators took their generator from `JMetalRandom.getInstance()`: the
crossover, the two mutations and the solver itself. Operators are built per request, so the
`random` field looked like a collaborator owned by each run, but `getInstance()` always returns the
same object. There was **a single stream of random numbers for the whole process**.

**Why it matters.** Without a seed it made no difference, which is why it went unnoticed. As soon as
you want to reproduce a run, it breaks: two concurrent requests take turns on the same generator's
numbers, and a `setSeed` from the second restarts the first one's stream mid-run. Neither
reproduces, and from outside all you see is a different cost, indistinguishable from the
algorithm's normal variability. `GeneticRouteSolver` being a `@Service` (singleton bean) also rules
out keeping the generator in a field: it has to travel as an argument.

**How it was solved.** One `PseudoRandomGenerator` per run, created in `solve()` from the seed and
passed by constructor to the three operators and as an argument to `initializePopulation`,
`tournamentSelect` and `restartPopulation`. `JavaRandomGenerator` is used, which is **the same
generator `JMetalRandom` shipped by default**: the change alters neither the statistical quality nor
invalidates previous benchmark results, it only stops sharing state. `JMetalRandom` adds nothing
else, because it merely delegates to the interface with the same signatures.

**What made it worthwhile.** The rest of the engine was already deterministic: stopping depends on
evaluations and generations, never on the clock; `PermutationCodec.depotOrder` walks the depots in
a `LinkedHashMap` seeded from the list, so distance sums always accumulate in the same order; sorts
are stable and there are no parallel streams. The singleton was the only thing preventing bit-for-bit
reproducibility.

**Discarded:** `JMetalRandom.getInstance().setSeed(seed)` at the start of `solve()`. It is one line
and works with a single thread, but it leaves the engine at one request at a time — with a high
`maxRestarts` a large instance takes minutes and the others eat the dispatcher's 300 s timeout — and
turns an invariant into something to remember.

---

## Minor changes

- Index comparison with a `HashSet` instead of `contains` on a list, which was O(n²) per child.
- The tournament is prevented from returning the same parent twice.
- `restartPopulation` really keeps the `keepCount` best; before, it received the parameter and kept
  only one.
- Dead code removed: unused route counter, unread variables, conditionals that did nothing, an
  unreachable fallback path that also did not check capacity.
- `buildDepotOrder` was duplicated in four classes; it now lives in `PermutationCodec`.

## Ideas tried and discarded

- **Swap of customers between routes of the same depot.** Implemented and measured: 5958 on average
  versus 5955 without it. It added nothing and was reverted so as not to bloat the code.
- **Raising `MAX_EVALUATIONS`, `LOCAL_SEARCH_FREQUENCY` and `MAX_RESTARTS`.** The cost reaches a
  plateau around 5950 on p22 and does not drop with more budget.

## Pending

- **Time windows**: the parser reads them but they are neither propagated nor supported.
- **Reproducibility**: `JMetalRandom` is a global singleton without a configurable seed.
- **Externalisable parameters** into `application.yml` with `@ConfigurationProperties`.
- **Parallelisation**: the engine is sequential. Offspring evaluation is parallelisable, but
  `ThreadLocalRandom` would have to be used: contention on jMetal's shared generator would penalise
  it.
- **Diversity management** (*path relinking*, granular neighbourhoods) to get below the plateau.
