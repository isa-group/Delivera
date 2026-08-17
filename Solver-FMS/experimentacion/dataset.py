"""
El esquema del CSV y la agregacion de repeticiones.

Aqui vive el contrato de los datos: que columnas hay, en que orden y con cuantos
decimales. Que ese contrato no cambie entre experimentos es lo que permite concatenar
los CSV de varias sesiones y analizarlos juntos.
"""

import json
import statistics

# El esquema del CSV, en un solo sitio. Los experimentos se distinguen por run_id y
# label, no por tener columnas distintas.
CSV_COLUMNS = [
    # Identificacion de la ejecucion
    "filename", "solver", "repetition", "run_id", "label", "timestamp",
    "solver_version", "strategy", "deterministic", "seed", "params",
    # Caracteristicas de la instancia
    "num_customers", "num_depots", "vehicles_per_depot", "vehicle_capacity",
    "max_duration", "total_demand", "load_ratio", "avg_service_duration",
    "customers_per_depot", "area", "customer_density", "mean_nn_distance",
    "mean_depot_distance", "bks",
    # Resultado
    "status", "cost", "reported_cost", "gap_pct", "routes", "vehicles_used",
    "extra_trips", "feasible", "violations", "violations_detail", "elapsed_ms",
    "engine_ms", "error",
]

# Cuantos decimales lleva cada columna numerica al volcarse. Las que no aparecen se
# escriben tal cual.
DECIMALS = {
    "cost": 2, "reported_cost": 2, "bks": 2, "gap_pct": 2, "elapsed_ms": 0,
    "max_duration": 0, "load_ratio": 4, "avg_service_duration": 2,
    "customers_per_depot": 2, "area": 2, "customer_density": 5,
    "mean_nn_distance": 4, "mean_depot_distance": 4,
}


def format_row(row):
    values = []
    for column in CSV_COLUMNS:
        value = row.get(column)
        if value is None or value == "":
            values.append("")
        elif column in DECIMALS and isinstance(value, (int, float)):
            values.append(f"{value:.{DECIMALS[column]}f}")
        else:
            values.append(value)
    return values


def compact(mapping):
    return json.dumps(mapping, sort_keys=True, separators=(",", ":"))


def summarize_violations(problems):
    if not problems:
        return ""
    head = "; ".join(problems[:3])
    return head if len(problems) <= 3 else f"{head}; (+{len(problems) - 3} mas)"


def digest(rows):
    """
    Resume las repeticiones de un solver sobre una instancia en una sola linea.
    Devuelve None si no hay ninguna ejecucion valida.
    """
    done = [r for r in rows if r["status"] == "ok"]
    if not done:
        return None

    feasible = [r for r in done if r["feasible"] == "true"]
    costs = [r["cost"] for r in done]
    best = min(feasible or done, key=lambda r: r["cost"])

    return {
        "runs": len(done),
        "best": best,
        "best_cost": best["cost"],
        "mean_cost": statistics.fmean(costs),
        # Una sola ejecucion no tiene dispersion: imprimir 0,0 la haria parecer estable
        # cuando lo que pasa es que no se ha medido.
        "stdev": statistics.stdev(costs) if len(costs) > 1 else None,
        "gap": best.get("gap_pct"),
        "routes": min(r["routes"] for r in done),
        "mean_time": statistics.fmean(r["elapsed_ms"] for r in done),
        "feasible": len(feasible),
        "any_feasible": bool(feasible),
        "trips": sum(r["routes"] for r in done),
        "vehicles": sum(r["vehicles_used"] for r in done),
    }


def mean_time(average):
    #Formato compartido por la terminal y el informe, para que no digan cosas distintas
    return f"{average:.0f}ms" if average < 1000 else f"{average / 1000:.1f}s"
