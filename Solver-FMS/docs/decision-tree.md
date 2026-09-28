# Decision tree: which solver suits

Trains a decision tree on the data produced by [`compare_solvers.py`](benchmark.md) and obtains a
rule that chooses a solver from the instance's properties: number of customers, depots, capacity,
density, total demand and eight more.

It is given an experiment's `datos-*.csv` and **looks for its `instancias-*.csv` next to it**,
which is where those properties come from. The tree's level of observation is the instance: one
row, one decision. Runs are aggregated up to there — each solver keeps its best cost and its mean
time — and the problem's properties are attached to them.

The solvers **are read from the data**, they are not written into the code. The script classifies
over whichever it finds in the CSV, be it two or six.

```bash
python decision_tree.py                          # the most recent experiment in results/
python decision_tree.py results/datos-*.csv      # several experiments together
python decision_tree.py --exclude RANDOM,GREEDY  # production candidates only
```

From `Solver-FMS/`. Unlike the `comparison` package, this one **needs dependencies**:

```bash
pip install pandas scikit-learn matplotlib
```

Each run leaves two files under `--out`: the report `tree-<date>.md` and the image
`tree-<date>.png`.

| Parameter | Default | Meaning |
|---|---|---|
| `csv` | the most recent in `results/` | One or several `datos-*.csv`. Their sibling `instancias-*.csv` is read automatically. When joined, each instance keeps its best cost |
| `--exclude` | - | Solvers to leave out, comma-separated |
| `--tolerance` | `1.0` | Margin in % within which two solvers are considered of equal quality |
| `--max-depth` | `10` | Maximum tree depth |
| `--min-leaf` | `5` | Minimum instances per leaf |
| `--folds` | `10` | Cross-validation folds |
| `--permutations` | `2000` | Permutations of the significance test |
| `--seed` | `20260819` | Seed, so the tree is reproducible |
| `--out` | `results/` | Output directory |
| `--skip-selftest` | - | Do not verify the algorithm on the synthetic problem |

## What "suits" means

It is what the tree learns to predict, so it defines the whole problem.

**The solver with the lowest feasible cost suits.** If others are within `--tolerance` percent of
the best, they all count as equal quality and **among them the fastest wins**.

It is the real decision in production: if two engines give practically the same cost, the one that
takes ten times less is the better choice. And it prevents the tree from separating differences that
come from the seed and not the algorithm: between two stochastic runs, the distance between 5.1 %
and 5.2 % gap is noise.

The `Decidida por` (decided by) column of the report marks each instance as `coste` (cost) or
`tiempo` (time).

The tolerance changes the problem, and it is worth seeing it measured. Over the 33 Cordeau
instances, excluding the random engine:

| `--tolerance` | Split |
|---:|---|
| 0 % | `GENETIC` 33 |
| 10 % | `GENETIC` 33 |
| 20 % | `GENETIC` 32 · `GREEDY` 1 |
| 30 % | `GENETIC` 29 · `GREEDY` 4 |
| 40 % | `GENETIC` 15 · `GREEDY` 18 |
| 60 % | `GENETIC` 4 · `GREEDY` 29 |
| 80 % | `GREEDY` 33 |

The genetic engine wins on cost in all 33 instances, with margins over the runner-up from 19.4 % to
73.6 %. Below that 19.4 % no instance changes hands.

Two details of the computation:

- The cost is the **best** of the N repetitions; the time is the **mean**. That slightly favours
  the stochastic solver, which paid N times that time to get its best run.
- If two solvers tie **exactly** on time, the order of appearance in the runs decides.

## How to read the report

### First of all: is the tree worth anything?

| Measure | What it says |
|---|---|
| Cross-validation accuracy | How often it is right on instances it has not seen |
| Accuracy always choosing the majority solver | What you get **without a tree** |
| Permutation test p-value | How often chance matches that accuracy |

**A tree that does not beat the majority rule is useless, however many branches the drawing has.**
It is the row to look at before the tree: 80 % accuracy sounds good until you see that always
choosing the same solver gives 78 %.

The permutation test retrains the tree with shuffled labels 2000 times and counts how many match
the real accuracy. It consumes 90 % of the run time — some 40 s, against the 0.5 ms training the
tree costs — and it is what tells a pattern from a coincidence with 33 instances.

### When a single solver wins every instance

The report shows the tree, which is a single node, and **publishes no metrics**. With a single class
the accuracy is 100 % by construction: always being right is trivial when there is only one possible
answer, and that 100 % next to that of a tree with branches would invite comparing them.

That image is not drawn by `plot_tree` but by custom code in `plot.py`. sklearn omits the `class =`
line when the tree has a single class, and produces a blank box with `samples = 33` and
`value = 1.0` that does not mention which solver suits. The custom node includes the solver's name
and the margin over the runner-up.

### Verifying the algorithm

A one-node tree does not prove the code works: a broken tree and a correctly trivial one look the
same. Each run also trains on a synthetic problem with a known answer — 120 samples where the true
rule is `signal > 150`, plus two pure-noise features it must ignore — and checks three things: that
it splits on the right column, that it finds the threshold (±15) and that it scores above 95 % on
leave-one-out.

```
Autoverificacion del algoritmo: PASA (umbral 150.2 sobre 150 real, acierto 99.2 %)
```

`PASA` (pass) means the result on the real data is a result. `FALLA` (fail) means there is a bug. It
is disabled with `--skip-selftest`.

### The node values

In the image, `value = [16.5, 16.5]` **are not instances**: they are counts weighted by
`class_weight="balanced"`, which compensates for one class having more examples than another. With
17 and 16 instances, `17 × 0.9706 = 16.5` and `16 × 1.0312 = 16.5`. The real count is in the
`samples` line.

## Example: greedy versus genetic

The genetic engine wins on cost in all 33 instances, so on pure cost the tree is a single node. The
question *"accepting up to 40 % more cost in exchange for speed, when is the greedy engine
enough?"* does split the instances into two classes:

```bash
python decision_tree.py --exclude RANDOM --tolerance 40 --max-depth 3
```

```
Instancias: 33  |  solvers: GENETIC, GREEDY
Conviene: GREEDY 18, GENETIC 15
Autoverificacion del algoritmo: PASA (umbral 150.2 sobre 150 real, acierto 99.2 %)

Acierto en validacion cruzada: 81.7 % (regla mayoritaria: 55.0 %, p = 0.0035)
```

The resulting tree:

```
vehicle_capacity <= 70          -> GENETIC   (12 instances, pure)
vehicle_capacity > 70
    customers_per_depot <= 55   -> GREEDY    (16 instances, pure)
    customers_per_depot > 55    -> GENETIC   (5 instances, mixed)
```

With small vehicles or heavily loaded depots it pays to run the genetic engine; in the middle zone
the greedy one gets close enough and is about thirty times faster. It beats always choosing the same
solver by 26.7 points, and chance matches that accuracy in 0.35 % of the shuffles.

That 40 % is a business threshold, not a finding of the analysis: it is set by whoever decides how
much cost they are willing to trade for speed.

## Tuning the tree

- **Raising `--max-depth` almost always raises training accuracy and lowers validation accuracy.**
  If raising it improves cross-validation, the tree was too shallow; if it worsens, it is
  memorising. With 33 instances, an unrestrained tree memorises them and gives a 100 % that means
  nothing.
- **A feature with importance 0.000 that still appears in a split** marks a split that does not
  change the decision: there is too much depth.
- **`--exclude RANDOM,GREEDY`** when the question is which of the production engines to use.
  Including the baselines inflates accuracy with instances nobody doubted.
- **`--folds` affects the stability of the estimate.** With 33 instances and 10 folds, each fold has
  3 instances and missing one is 33 points: the average is solid, the deviation huge. With
  `--folds 5` it comes out more stable.
- **`--permutations` is linear in time.** With 200 the run drops to about 6 s, in exchange for
  resolution: below p ≈ 0.005 it no longer distinguishes.

## Where everything lives

`decision_tree.py` is the command line and the wiring. The work is in `experimentation/tree/`:

| Module | What it knows |
|---|---|
| `dataset.py` | What "suits" means: cost and, at equal cost, time |
| `model.py` | Train, validate against the majority rule and verify the algorithm |
| `plot.py` | The tree as an image |
| `report.py` | The Markdown report |

## Limitations

- **33 instances.** Any model over that amount is at the limit. That is why the defaults are
  conservative and the reported accuracy is always cross-validated, never on the training data.
- **Trees are unstable with little data:** changing one instance can change the first split.
  Before taking a rule into code, check whether it survives repeating the experiment with another
  seed.
- **The Cordeau instances are not real delivery data.** A rule learned here is valid for choosing
  an engine on the test bed; extrapolating it to production is a hypothesis, not a result.
- **The tree does not say which algorithm is better, it says which suits under the configured
  criterion.** Changing `--tolerance` changes the answers, and it is right that it does.
