# greedy-engine

**Port 8091** · `com.delivera.fms.engine.greedy` · main class
[`GreedyRouteSolver`](../../engines/greedy-engine/src/main/java/com/delivera/fms/engine/greedy/service/GreedyRouteSolver.java)

## What it is for

A fast, **deterministic** solution of reasonable quality. It is the default option of
`/api/v1/fms/instances/send` and the one to use when answering in milliseconds matters more than
squeezing the cost.

## Algorithm

**Nearest neighbour** heuristic, applied per depot and per vehicle:

```
1. Group each customer with its nearest depot
2. Distribute the depot's customers among its vehicles
      baseStops = customers / vehicles
      each vehicle takes baseStops stops, the last one takes the rest
3. For each vehicle, build its routes:
      start from the depot
      repeatedly pick the unvisited customer closest to the current point
      if it does not fit by capacity, discard it and continue with the next
      close the route on reaching the stop limit, and open another if customers remain
4. Customers left unserved go to a fallback route
```

## Implementation details

**Stop distribution** (`baseStops`): a depot's customers are divided equally among its vehicles,
and the **last vehicle receives `maxStops = Integer.MAX_VALUE`**, that is, everything left over. The
distribution is by *number of stops*, not by load, so one vehicle may end up with all the
high-demand stops and another with the low-demand ones.

**Discarding by capacity**: when the closest customer does not fit, it is removed from the candidate
list *of that trip* and the next closest is tried. A new route is not opened immediately; the current
one is filled with whatever fits.

**Multi-trip** (`buildGreedyMultiTripRoutes`): as in the random engine, a `vehicleId` may appear in
several routes. That is why this engine does not honour the number of vehicles either.

**Fallback route**: customers left unserved are grouped into a `V-FALLBACK-<depot>` route with
**unlimited capacity**, which may violate capacity.

## Limitations

- Ignores the number of vehicles per depot (unbounded multi-trip).
- The fallback route may exceed capacity.
- Distribution by number of stops ignores demand, and may leave vehicles very unbalanced in load.
- Nearest neighbour has the classic flaw: it leaves isolated customers "forgotten" and picks them up
  at the end with very long legs.
- It only optimises the visiting order. It neither reassigns customers between depots nor improves
  the routes once built.

## Computational cost

O(n²) per depot for the nearest-neighbour search. Answers in milliseconds on every instance of the
test bed.

## Comparison with the genetic engine

The genetic engine uses this same heuristic — nearest neighbour from the nearest depot — to seed part
of its initial population, but **randomised**: instead of the closest customer it picks at random
among the 3 closest, so as not to generate 30 identical individuals. See
[genetic-engine.md](genetic-engine.md).
