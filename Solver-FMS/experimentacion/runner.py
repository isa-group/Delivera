"""
Lanzar, medir, validar y convertir cada ejecucion en una fila.

Es la capa donde se produce el dato. Lo que sale de aqui son diccionarios con las
columnas de dataset.CSV_COLUMNS, ya listos para volcar o para agregar.
"""

import http.client
import subprocess
import sys
import time
from datetime import datetime

from . import BASE_DIR
from .dataset import compact, digest, format_row, mean_time, summarize_violations
from .gateway import declared_defaults, solve
from .instance import Instance, routes_of


class Config:

    def __init__(self, args):
        self.url = args.url.rstrip("/")
        self.runs = args.runs
        self.timeout = args.timeout
        self.seed = args.seed
        self.label = args.label or ""
        self.started = datetime.now()
        self.started_at = time.time()
        self.run_id = self.started.strftime("%Y-%m-%d-%H%M")
        self.commit = git_commit()
        self.command = " ".join(["python compare_solvers.py", *sys.argv[1:]])


def git_commit():
    """Que commit produjo estos numeros. Sin esto el informe no se puede volver a situar."""
    try:
        return subprocess.run(["git", "rev-parse", "--short", "HEAD"], cwd=BASE_DIR,
                              capture_output=True, text=True, timeout=5,
                              check=True).stdout.strip()
    except (OSError, subprocess.SubprocessError):
        return "desconocido"


def run_instance(config, instance_name, solvers, bks, writer, handle, prefix=""):
    """Ejecuta todos los solvers sobre una instancia y devuelve una fila por ejecucion."""
    instance = Instance.load(instance_name)
    features = instance.features()

    header = f"\n{prefix}{instance_name}  {instance.describe()}"
    if bks:
        header += f"  |  BKS {bks:.2f}"
    print(header)
    print(f"  {'solver':<9}{'n':>3}{'mejor':>11}{'media':>11}{'desv':>8}"
          f"{'gap':>8}{'rutas':>7}{'tiempo':>9}{'factible':>10}{'semilla':>16}")

    records = []
    for solver, info in solvers.items():
        # El voraz es determinista: repetirlo solo gasta tiempo. Lo dice su descriptor.
        attempts = 1 if info.get("deterministic") else config.runs
        rows = []

        for repetition in range(1, attempts + 1):
            row = execute(config, instance, features, solver, info, repetition, bks)
            rows.append(row)
            records.append(row)
            writer.writerow(format_row(row))
            # Un barrido de las 33 instancias son minutos: si se corta a mitad, lo ya
            # medido tiene que estar en disco.
            handle.flush()
            if row["status"] == "error":
                print(f"  {solver:<9}fallo: {row['error']}")
                break

        print_solver_line(solver, rows)

    return records


def execute(config, instance, features, solver, info, repetition, bks):
    """Una ejecucion: la lanza, la mide, la valida y la convierte en fila."""
    parameters = {}
    # Con --seed las repeticiones son reproducibles y distintas entre si: la k-esima
    # usa seed + k - 1. Sin --seed el motor sortea una y la devuelve, que es lo que
    # mide la variabilidad real del algoritmo.
    if config.seed is not None and not info.get("deterministic"):
        parameters["seed"] = config.seed + repetition - 1

    row = {
        "filename": instance.name,
        "solver": solver,
        "repetition": repetition,
        "run_id": config.run_id,
        "label": config.label,
        "timestamp": datetime.now().isoformat(timespec="seconds"),
        "solver_version": info.get("version", ""),
        "strategy": info.get("strategy", ""),
        "deterministic": str(bool(info.get("deterministic"))).lower(),
        "bks": bks,
    }
    row.update(features)

    effective = dict(declared_defaults(info))
    effective.update(parameters)

    try:
        response, elapsed = solve(config.url, instance.name, solver, config.timeout, parameters)
    except (OSError, http.client.HTTPException) as error:
        row.update({"status": "error", "error": str(error), "seed": "",
                    "params": compact(effective)})
        return row

    cost = instance.cost(response)
    problems = instance.violations(response)
    seed = response.get("seed")
    if seed is not None:
        effective["seed"] = seed

    used = len({r.get("vehicleId") for r in routes_of(response)})

    row.update({
        "status": "ok",
        "seed": seed if seed is not None else "",
        "params": compact(effective),
        "cost": cost,
        "reported_cost": response.get("totalCost"),
        "gap_pct": (cost / bks - 1) * 100 if bks else None,
        "routes": len(routes_of(response)),
        "vehicles_used": used,
        "extra_trips": len(routes_of(response)) - used,
        "feasible": "true" if not problems else "false",
        "violations": len(problems),
        "violations_detail": summarize_violations(problems),
        "elapsed_ms": elapsed,
        "engine_ms": response.get("computationTimeMs"),
        "error": "",
    })
    return row


def print_solver_line(solver, rows):
    """La linea que se ve en la terminal mientras el barrido avanza."""
    summary = digest(rows)
    if summary is None:
        return

    stdev = f"{summary['stdev']:.1f}" if summary["stdev"] is not None else "-"
    gap = f"{summary['gap']:+.1f}%" if summary["gap"] is not None else "-"
    seed = summary["best"]["seed"] or "-"
    feasible = f"{summary['feasible']}/{summary['runs']}"

    print(f"  {solver:<9}{summary['runs']:>3}{summary['best_cost']:>11.2f}"
          f"{summary['mean_cost']:>11.2f}{stdev:>8}{gap:>8}{summary['routes']:>7}"
          f"{mean_time(summary['mean_time']):>9}{feasible:>10}{str(seed):>16}")
