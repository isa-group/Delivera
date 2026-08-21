"""
De filas del CSV a una tabla por instancia, con la etiqueta de que solver conviene.

Aqui vive la decision de que significa "conviene", que es lo que el arbol aprende a
predecir. Cambiarla cambia el problema entero, asi que esta en un solo sitio.
"""

import csv
import statistics
import sys

# Las columnas del CSV que describen la INSTANCIA, no el resultado. Es el bloque de
# caracteristicas del esquema de compare_solvers, sin `bks`: el BKS es la referencia
# contra la que se mide el acierto, no un dato disponible al elegir solver.
FEATURES = [
    "num_customers", "num_depots", "vehicles_per_depot", "vehicle_capacity",
    "max_duration", "total_demand", "load_ratio", "avg_service_duration",
    "customers_per_depot", "area", "customer_density", "mean_nn_distance",
    "mean_depot_distance",
]


def load(paths, excluded):
    """
    Carga las filas de ejecucion de los CSV, y filtra las que no son factibles o que
    pertenecen a solvers excluidos. Si no queda ninguna, termina el programa.
    """
    rows = []
    for path in paths:
        with path.open(encoding="utf-8") as handle:
            rows.extend(csv.DictReader(handle))
    usable = [r for r in rows
              if r["status"] == "ok" and r["feasible"] == "true" and r["solver"] not in excluded]
    if not usable:
        sys.exit("No queda ninguna ejecucion factible despues de filtrar")
    return usable


def build_dataset(rows, tolerance):
    """
    De las filas de ejecucion, construye una tabla por instancia con la etiqueta de que
    solver conviene. La etiqueta es el solver mas barato, y si hay varios equivalentes
    (dentro de un margen de tolerancia), se elige el que mas rapido resolvio. Si hay varios equivalentes y el mas rapido es distinto del mas barato, se marca
    """
    by_instance = {}
    for row in rows:
        by_instance.setdefault(row["filename"], []).append(row)

    dataset = []
    for name, runs in sorted(by_instance.items()):
        cost, elapsed = {}, {}
        for run in runs:
            solver, value = run["solver"], float(run["cost"])
            if solver not in cost or value < cost[solver]:
                cost[solver] = value
            elapsed.setdefault(solver, []).append(float(run["elapsed_ms"]))
        elapsed = {s: statistics.fmean(v) for s, v in elapsed.items()}
        if not cost:
            continue

        ranked = sorted(cost.items(), key=lambda kv: kv[1])
        best_cost = ranked[0][1]
        margin = ((ranked[1][1] - best_cost) / best_cost * 100) if len(ranked) > 1 else float("inf")

        equivalent = [s for s, c in cost.items() if (c - best_cost) / best_cost * 100 <= tolerance]
        winner = min(equivalent, key=lambda s: elapsed[s])

        entry = {f: float(runs[0][f]) for f in FEATURES}
        entry.update({
            "filename": name,
            "cost": cost,
            "elapsed": elapsed,
            "winner": winner,
            "cheapest": ranked[0][0],
            "runner_up": ranked[1][0] if len(ranked) > 1 else None,
            "margin": margin,
            "decided_by_time": len(equivalent) > 1,
        })
        dataset.append(entry)
    return dataset
