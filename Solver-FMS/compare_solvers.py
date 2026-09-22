#!/usr/bin/env python3
"""
Compara solvers sobre las instancias Cordeau, a traves de la API del gateway.

Por que a traves de la API y no llamando a cada motor: es el unico camino que
ejerce el mapeo real y trata a los tres solvers exactamente igual, que es la
condicion para que la comparacion signifique algo.

Que hace distinto a mirar el coste que devuelve cada motor:

1. RECALCULA el coste desde las paradas, en vez de fiarse del que informa el
   motor. Ambos van al CSV, asi que una discrepancia se ve.
2. REPITE. Ningun motor salvo el voraz es reproducible, asi que una ejecucion
   suelta no dice nada: hacen falta mejor, media y dispersion.

Uso:
    python compare_solvers.py --instances p01,p22
    python compare_solvers.py --all --runs 5 --csv resultados.csv
"""

import argparse
import csv
import json
import math
import statistics
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
INSTANCES_DIR = BASE_DIR / "instances-MD-CVRP-JSON"

# Los BKS viven en un fichero de datos que comparten este script y el test de
# benchmark. Tenerlos dos veces garantiza que algun dia dejen de coincidir.
BKS_SOURCE = BASE_DIR / "best-known.json"


def load_best_known():
    if not BKS_SOURCE.exists():
        return {}
    return json.loads(BKS_SOURCE.read_text(encoding="utf-8"))


def load_instance(name):
    """Replica el mapeo del gateway: id de deposito = posicion + 1, distancia euclidea."""
    data = json.loads((INSTANCES_DIR / f"{name}.json").read_text(encoding="utf-8"))

    # 0 significa sin limite, igual que en el motor. Solo se usa para describir la
    # instancia en la cabecera.
    depots = {str(i + 1): {"x": d["x"], "y": d["y"], "max_duration": d["max_duration"] or math.inf}
              for i, d in enumerate(data["depots"])}

    customers = {str(c["id"]): {"x": c["x"], "y": c["y"]} for c in data["customers"]}

    return {"name": name, "depots": depots, "customers": customers,
            "max_duration": max(d["max_duration"] for d in depots.values())}


def distance(a, b):
    return math.hypot(a["x"] - b["x"], a["y"] - b["y"])


def route_cost(instance, response):
    """
    Recalcula el coste desde las paradas, en vez de fiarse del que informa el motor.

    Cada ruta es el ciclo deposito -> paradas -> deposito. Los tiempos de servicio
    no cuentan para el coste.
    """
    depots, customers = instance["depots"], instance["customers"]
    cost = 0.0

    for route in response.get("routes") or []:
        depot = depots.get(str(route["depotId"]))
        if depot is None:
            continue

        node = depot
        for stop in route["stops"]:
            customer = customers.get(str(stop))
            if customer is None:
                continue
            cost += distance(node, customer)
            node = customer
        cost += distance(node, depot)

    return cost


def solve(base_url, instance_name, solver, timeout):
    url = f"{base_url}/api/v1/fms/instances/send?fileName={instance_name}&solverType={solver}"
    request = urllib.request.Request(url, method="POST",
                                     headers={"Content-Type": "application/json"})
    started = time.perf_counter()
    with urllib.request.urlopen(request, timeout=timeout) as response:
        body = json.load(response)
    return body, (time.perf_counter() - started) * 1000


def catalog(base_url):
    """El descriptor dice que solvers hay y cuales son deterministas."""
    with urllib.request.urlopen(f"{base_url}/api/v1/fms/solvers", timeout=10) as response:
        return {s["type"]: s for s in json.load(response)["solvers"]}


def compare(base_url, instance_name, solvers, runs, timeout, writer):
    instance = load_instance(instance_name)
    bks = load_best_known().get(instance_name)

    limit = instance["max_duration"]
    header = (f"\n{instance_name}  "
              f"{len(instance['depots'])} depositos, {len(instance['customers'])} clientes, "
              f"duracion maxima {'sin limite' if limit == math.inf else int(limit)}")
    if bks:
        header += f"  |  BKS {bks:.2f}"
    print(header)
    print(f"  {'solver':<9}{'n':>3}{'mejor':>11}{'media':>11}{'desv':>8}"
          f"{'gap':>8}{'rutas':>7}{'tiempo':>9}")

    for solver, info in solvers.items():
        # El voraz es determinista: repetirlo solo gasta tiempo. Lo dice su descriptor.
        attempts = 1 if info.get("deterministic") else runs

        costs, times, routes = [], [], []
        for _ in range(attempts):
            try:
                response, elapsed = solve(base_url, instance_name, solver, timeout)
            except (urllib.error.URLError, TimeoutError) as error:
                print(f"  {solver:<9}fallo: {error}")
                break

            cost = route_cost(instance, response)
            costs.append(cost)
            times.append(elapsed)
            routes.append(len(response.get("routes") or []))

            if writer:
                writer.writerow([instance_name, solver, f"{cost:.2f}",
                                 f"{response.get('totalCost', 0):.2f}", f"{elapsed:.0f}",
                                 routes[-1]])

        if not costs:
            continue

        best, mean = min(costs), statistics.fmean(costs)
        # Una sola ejecucion no tiene dispersion: imprimir 0,0 la haria parecer
        # estable cuando lo que pasa es que no se ha medido.
        stdev = f"{statistics.stdev(costs):.1f}" if len(costs) > 1 else "-"
        gap = f"{(best / bks - 1) * 100:+.1f}%" if bks else "-"

        print(f"  {solver:<9}{len(costs):>3}{best:>11.2f}{mean:>11.2f}{stdev:>8}"
              f"{gap:>8}{min(routes):>7}{mean_time(times):>9}")


def mean_time(times):
    average = statistics.fmean(times)
    return f"{average:.0f}ms" if average < 1000 else f"{average / 1000:.1f}s"


def main():
    parser = argparse.ArgumentParser(description="Compara solvers sobre instancias Cordeau")
    parser.add_argument("--url", default="http://localhost:8090", help="URL del gateway")
    parser.add_argument("--instances", default="p01",
                        help="Instancias separadas por coma (p01,p22). Ignorado con --all")
    parser.add_argument("--all", action="store_true", help="Las 33 instancias del banco")
    parser.add_argument("--solvers", default="", help="Por defecto, todos los del catalogo")
    parser.add_argument("--runs", type=int, default=3,
                        help="Repeticiones por solver no determinista (por defecto 3)")
    parser.add_argument("--timeout", type=int, default=600, help="Timeout por peticion, en segundos")
    parser.add_argument("--csv", help="Vuelca cada ejecucion a un CSV")
    args = parser.parse_args()

    try:
        available = catalog(args.url)
    except urllib.error.URLError as error:
        sys.exit(f"No se puede leer el catalogo en {args.url}: {error}")

    if args.solvers:
        wanted = [s.strip().upper() for s in args.solvers.split(",")]
        unknown = [s for s in wanted if s not in available]
        if unknown:
            sys.exit(f"Solver no registrado: {', '.join(unknown)}. Hay: {', '.join(available)}")
        available = {s: available[s] for s in wanted}

    if args.all:
        names = sorted(p.stem for p in INSTANCES_DIR.glob("*.json"))
    else:
        names = [n.strip() for n in args.instances.split(",")]

    handle = open(args.csv, "w", newline="", encoding="utf-8") if args.csv else None
    writer = csv.writer(handle) if handle else None
    if writer:
        writer.writerow(["instancia", "solver", "coste", "coste_informado", "ms", "rutas"])

    try:
        for name in names:
            if not (INSTANCES_DIR / f"{name}.json").exists():
                print(f"\n{name}: no existe en {INSTANCES_DIR.name}, se salta")
                continue
            compare(args.url, name, available, args.runs, args.timeout, writer)
    finally:
        if handle:
            handle.close()
            print(f"\nEjecuciones volcadas en {args.csv}")


if __name__ == "__main__":
    main()
