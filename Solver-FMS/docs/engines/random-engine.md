# random-engine

**Port 8092** · `com.delivera.fms.engine.random` · main class
[`RandomRouteSolver`](../../engines/random-engine/src/main/java/com/delivera/fms/engine/random/service/RandomRouteSolver.java)

## What it is for

It is the **baseline**. It produces a solution that is valid with respect to capacity, with no
optimisation criterion whatsoever, so that any other engine must beat it clearly. If a new algorithm
does not noticeably improve on the random one, something is wrong with it.

It is not meant for production.

## Algorithm

```
1. Shuffle the customer list                       Collections.shuffle
2. Group each customer with its nearest depot
3. For each vehicle:
      walk its depot's customers in the shuffled order
      accumulate into the route until one more does not fit by capacity
      close the route, return to the depot and open another
4. Customers left unserved go to a fallback route
```

The visiting order within a route is the shuffled order: no attempt is made to bring nearby
customers together. Hence the high cost.

## Implementation details

**Grouping by nearest depot** (`groupByNearestDepot`): each customer goes to the depot with the
lowest `distanceMatrix[depot][customer]`. It is the same heuristic used by greedy and by the genetic
engine's initial population.

**Multi-trip** (`buildMultiTripRoutes`): the same vehicle may appear in several routes. When it
fills up, the route is closed and another is opened with the same `vehicleId`. That is why **the
engine does not honour the number of vehicles per depot**: it does not need to, because it assumes a
vehicle can make as many trips as necessary.

**Fallback route**: if a customer is left unserved because its depot had no assigned vehicles, a
route with `vehicleId = "V-FALLBACK-<depot>"` and **unlimited capacity** is created. That route may
violate capacity. It is a mechanism for not losing customers, not a valid solution.

**No declared vehicles**: if `vehicles` is empty or null, one route per depot is created with
`vehicleId = "V-<depot>"` and unlimited capacity.

## Parameters

| Parameter | Default | Range | Meaning |
|---|---|---|---|
| `seed` | - | 0 - 2^(48) - 1 | Shuffle seed. Without it the engine draws one and returns it |

The shuffle is the engine's only source of randomness, so the seed fully determines the solution:
same instance and same seed always give the same result. If not sent, the engine draws one within
that range and returns it in the response's `seed` field, so any run can be repeated afterwards by
sending it back.

The upper bound comes from `java.util.Random` keeping **48 bits** of the seed: `[0, 2⁴⁸)` covers
every possible stream exactly once, and above it two different seeds would give the same sequence.

## Limitations

- Ignores the number of vehicles: uses unbounded multi-trip.
- The fallback route may exceed capacity.
- As a baseline it is better to take the mean of several runs and not a single one: without a fixed
  seed, two calls with the same input give different results by design.

## Computational cost

Linear in the number of customers, plus the shuffle. Answers in milliseconds even on the large
instances.
