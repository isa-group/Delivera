"""
Viabilidad cuántica de una instancia: el filtro de /analyze y los qubits por región.

/analyze del engine cuántico (cqvrp) solo aplica tres umbrales heurísticos
(granularidad, tensión de capacidad y compresibilidad espacial); no construye el
QUBO ni cuenta qubits. Este script hace las dos cosas:

  1. Llama a POST /api/v1/quantum-routing/analyze por HTTP.
  2. Calcula, con el código de cqvrp y sin modificarlo, los qubits que necesitaría
     cada región para cada candidato que prueba cqvrp (K_min a K_min+3
     Súper-Clientes por región), sin ejecutar QAOA.

La instancia va por la vía cuántica solo si pasa el filtro y algún candidato cabe
entero: cqvrp descarta un candidato en cuanto una de sus regiones supera el límite.

El límite de qubits es el del solver QISKIT (simulador local). DWAVE no tiene ese
límite: envía cada región a D-Wave Leap, así que para él solo cuenta el filtro.

Cada ejecución deja dos ficheros en results/ que comparten nombre, como los de
compare_solvers.py:

  viabilidad-cuantica-<fecha>.json  Los datos en bruto: configuración y, por
                                    instancia, el filtro y los qubits de cada región.
  viabilidad-cuantica-<fecha>.md    El informe legible, en español.

Se ejecuta con el Python del entorno virtual de cqvrp, que tiene Qiskit y PyVRP, y
con el engine levantado (Docker en 8095 o uvicorn en 8000):

    ..\\..\\cqvrp\\quantum-solver-ms\\.venv\\Scripts\\python.exe quantum_viability.py            # p01-p23
    ..\\..\\cqvrp\\quantum-solver-ms\\.venv\\Scripts\\python.exe quantum_viability.py p01 p02 --include-mtz
    ..\\..\\cqvrp\\quantum-solver-ms\\.venv\\Scripts\\python.exe quantum_viability.py mi_instancia.json --max-qubits 30

Las instancias por nombre son las de cqvrp (tests/data); un .json usa el formato de
/solve de cqvrp. Sin instancias, se analizan todas las pNN de cqvrp.
"""

import argparse
import contextlib
import io
import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request
import warnings
from collections import Counter
from datetime import datetime
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
DEFAULT_CQVRP = BASE_DIR.parent.parent / "cqvrp" / "quantum-solver-ms"
DEFAULT_OUT_DIR = BASE_DIR / "results"
CANDIDATE_OFFSETS = 4  # cqvrp prueba K_min..K_min+3 (max_k_offset=3, fijo en su API)


def import_cqvrp(path):
    """Hace importable el código de cqvrp sin copiarlo ni tocarlo."""
    if not (path / "services" / "evaluation_service.py").exists():
        sys.exit(f"No encuentro cqvrp en {path}. Indica la ruta con --cqvrp o CQVRP_PATH.")
    # Sin .pyc: importar no debe dejar ficheros nuevos dentro de cqvrp.
    sys.dont_write_bytecode = True
    sys.path.insert(0, str(path))
    warnings.filterwarnings("ignore")


def load_instance(name, cqvrp_path):
    from domain.models import RoutingRequest
    from utils.parser import CordeauParser

    file = Path(name)
    if file.suffix == ".json":
        return RoutingRequest.model_validate(json.loads(file.read_text(encoding="utf-8")))
    return CordeauParser.parse_instance(str(cqvrp_path / "tests" / "data" / name), name)


def call_analyze(base_url, request):
    body = json.dumps(request.model_dump(mode="json")).encode("utf-8")
    http_request = urllib.request.Request(
        f"{base_url}/api/v1/quantum-routing/analyze",
        data=body,
        method="POST",
        headers={"Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(http_request, timeout=60) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        sys.exit(f"/analyze respondió {error.code}: {error.read().decode('utf-8')[:300]}")
    except urllib.error.URLError as error:
        sys.exit(f"No puedo conectar con {base_url}: {error.reason}. ¿Está levantado el engine?")


def qubits_needed(subrequest, include_mtz):
    """Qubits del QUBO de una región, sin ejecutar QAOA.

    cqvrp no expone el cálculo por separado: construye el QUBO y lo compara con el
    límite antes de simular. Con límite 0 siempre lo supera y el error trae el
    número. Si cqvrp cambia ese mensaje, este cálculo deja de funcionar.
    """
    from solvers.quantum_qiskit import solve_with_qiskit

    try:
        solve_with_qiskit([subrequest], max_qubits=0, include_mtz=include_mtz)
    except ValueError as error:
        match = re.search(r"necesita (\d+) qubits", str(error))
        if match:
            return int(match.group(1))
        raise
    return 0


def qubit_requirements(request, max_qubits, include_mtz):
    """Recorre los mismos candidatos que solve_best_quantum_candidate de cqvrp."""
    from services.evaluation_service import prepare_quantum_subrequests

    candidates = []
    seen = set()
    for offset in range(CANDIDATE_OFFSETS):
        try:
            # PuLP y GraphAnalyzer escriben en stdout; no interesa en el informe.
            with contextlib.redirect_stdout(io.StringIO()):
                subrequests, preparation = prepare_quantum_subrequests(request, k_offset=offset)
        except Exception as error:
            candidates.append({"k_offset": offset, "fits": False, "error": str(error)})
            continue
        # Un offset que repite el mismo QUBO no se cuenta dos veces, igual que al resolver.
        shape = tuple(len(subrequest.customers) for subrequest in subrequests)
        if shape in seen:
            continue
        seen.add(shape)

        customers = {region["depot_id"]: region["original_customers"] for region in preparation["regions"]}
        regions = []
        for subrequest in subrequests:
            depot_id = subrequest.depots[0].id
            qubits = qubits_needed(subrequest, include_mtz)
            regions.append({
                "depot_id": depot_id,
                "customers": customers.get(depot_id),
                "superclients": len(subrequest.customers),
                "qubits": qubits,
                "fits": qubits <= max_qubits,
            })
        candidates.append({
            "k_offset": offset,
            "fits": all(region["fits"] for region in regions),
            "regions": regions,
        })
    return candidates


def evaluate(name, args):
    request = load_instance(name, args.cqvrp)
    analyze = call_analyze(args.url, request)["analysis"]
    candidates = qubit_requirements(request, args.max_qubits, args.include_mtz)
    fitting = [candidate["k_offset"] for candidate in candidates if candidate["fits"]]
    return {
        "instance": request.instance_name,
        "analyze": analyze,
        "max_qubits": args.max_qubits,
        "include_mtz": args.include_mtz,
        "candidates": candidates,
        "fits_in_qubits": bool(fitting),
        "quantum_viable": analyze["is_viable"] and bool(fitting),
    }


def print_report(result):
    features = result["analyze"]["features"]
    depots = features["num_depots"]
    print(f"\n{result['instance']}: {features['num_customers']} clientes, "
          f"{depots} depósito{'s' if depots != 1 else ''}")
    print(
        f"  /analyze: {'apta' if result['analyze']['is_viable'] else 'no apta'} "
        f"(G={features['demand_granularity']} <0.6, Tc={features['capacity_tightness']} <=0.9, "
        f"S={features['spatial_compressibility_index']} <=1.2)"
    )
    print(f"  Qubits por región (máximo {result['max_qubits']}{', con MTZ' if result['include_mtz'] else ''}):")
    for candidate in result["candidates"]:
        label = f"K_min+{candidate['k_offset']}"
        if "error" in candidate:
            print(f"    {label}: no se puede preparar ({candidate['error']})")
            continue
        regions = ", ".join(
            f"{region['depot_id']}: {region['qubits']}{'' if region['fits'] else ' ✗'}"
            for region in candidate["regions"]
        )
        print(f"    {label}: {'cabe' if candidate['fits'] else 'no cabe'} [{regions}]")
    if result["quantum_viable"]:
        verdict = "SÍ, va por QAOA (si QAOA encuentra una solución factible)"
    elif not result["analyze"]["is_viable"]:
        verdict = "NO, la descarta el filtro de /analyze"
    elif not prepared(result):
        verdict = "NO, no se puede preparar ningún candidato"
    else:
        verdict = f"NO, ningún candidato cabe en {result['max_qubits']} qubits"
    print(f"  Vía cuántica: {verdict}")


def needed_qubits(result):
    """Qubits que haría falta tener para que la instancia entera vaya por la vía cuántica.

    Es el mínimo, entre los candidatos que se pueden preparar, de su región más grande.
    None si no se puede preparar ninguno.
    """
    largest = [max(region["qubits"] for region in candidate["regions"])
               for candidate in result["candidates"] if "regions" in candidate]
    return min(largest) if largest else None


def prepared(result):
    return sum("regions" in candidate for candidate in result["candidates"])


def best_fitting_regions(result):
    """Regiones que caben en el candidato que más tiene dentro del límite, como «1 de 4»."""
    best = None
    for candidate in result["candidates"]:
        if "regions" in candidate:
            fitting = sum(region["fits"] for region in candidate["regions"])
            if best is None or fitting > best[0]:
                best = (fitting, len(candidate["regions"]))
    return f"{best[0]} de {best[1]}" if best else "n/d"


def verdict(result):
    if not result["analyze"]["is_viable"]:
        return "No: filtro"
    if not prepared(result):
        return "No: preparación"
    if not result["fits_in_qubits"]:
        return "No: qubits"
    return "**Sí**"


def preparation_reason(error):
    """Agrupa los errores de preparación quitando lo que depende de la instancia."""
    if "supercliente" in error and "supera Q" in error:
        return "Un Súper-Cliente supera la capacidad del vehículo"
    return re.sub(r"\S+_Sub_\S+", "<región>", error)[:120]


def git_commit(path):
    """Commit del repositorio en `path` y si tiene cambios sin commitear."""
    try:
        commit = subprocess.run(["git", "rev-parse", "--short", "HEAD"], cwd=path, capture_output=True,
                                text=True, timeout=5, check=True).stdout.strip()
        dirty = subprocess.run(["git", "status", "--porcelain"], cwd=path, capture_output=True,
                               text=True, timeout=5, check=True).stdout.strip()
        return commit, bool(dirty)
    except (OSError, subprocess.SubprocessError):
        return "desconocido", False


def reserve_run(out_dir, run_id, label):
    """Nombres de las dos salidas; dos ejecuciones del mismo minuto desempatan con un sufijo."""
    for attempt in range(1, 100):
        current = run_id if attempt == 1 else f"{run_id}-{attempt}"
        stem = f"{current}-{label}" if label else current
        paths = (out_dir / f"viabilidad-cuantica-{stem}.json", out_dir / f"viabilidad-cuantica-{stem}.md")
        if not any(path.exists() for path in paths):
            return current, paths
    sys.exit(f"Demasiadas ejecuciones con el mismo nombre en {out_dir}")


def write_report(path, meta, results, data_name):
    def commit_text(commit, dirty):
        return f"`{commit}`" + (" (con cambios locales sin commitear)" if dirty else "")

    q_max = meta["max_qubits"]
    lines = [
        "# Viabilidad cuántica de instancias MD-CVRP",
        "",
        f"**Fecha de ejecución:** {meta['started_text']}  ",
        f"**Identificador de la ejecución:** `{meta['run_id']}`  ",
        f"**Duración total:** {meta['duration_s']} s  ",
        f"**Engine cuántico:** `{meta['url']}`  ",
        f"**Commit del repositorio:** {commit_text(meta['commit'], meta['dirty'])}  ",
        f"**Commit de cqvrp:** {commit_text(meta['cqvrp_commit'], meta['cqvrp_dirty'])}  ",
        f"**Datos:** [`{data_name}`](./{data_name}) - {len(results)} instancias",
        "",
        "| Configuración | Valor |",
        "|---|---|",
        f"| Qubits disponibles (Q_max) | {q_max} |",
        f"| Restricciones MTZ | {'sí' if meta['include_mtz'] else 'no'} |",
        "| Candidatos por región | K_min a K_min+3 Súper-Clientes |",
        "",
        "## Resumen",
        "",
        "| | Instancias |",
        "|---|---|",
        f"| Analizadas | {len(results)} |",
        f"| Pasan el filtro de `/analyze` | {sum(r['analyze']['is_viable'] for r in results)} |",
        f"| Se puede preparar algún candidato | {sum(prepared(r) > 0 for r in results)} |",
        f"| Caben en {q_max} qubits | {sum(r['fits_in_qubits'] for r in results)} |",
        f"| **Van por la vía cuántica (`QISKIT`)** | **{sum(r['quantum_viable'] for r in results)}** |",
        "",
        "## Por instancia",
        "",
        "*Qubits necesarios* es la región más grande del mejor candidato: los qubits que haría falta "
        "tener para que la instancia entera vaya por la vía cuántica. *Regiones que caben* cuenta las "
        f"que no superan {q_max} qubits en el candidato con más regiones dentro del límite.",
        "",
        "| Instancia | Clientes | Depósitos | `/analyze` | G | Tc | S | Candidatos preparados | "
        "Qubits necesarios | Regiones que caben | Vía cuántica |",
        "|---|---|---|---|---|---|---|---|---|---|---|",
    ]
    for result in results:
        features = result["analyze"]["features"]
        needed = needed_qubits(result)
        lines.append(
            f"| {result['instance']} | {features['num_customers']} | {features['num_depots']} | "
            f"{'pasa' if result['analyze']['is_viable'] else '**rechaza**'} | "
            f"{features['demand_granularity']} | {features['capacity_tightness']} | "
            f"{features['spatial_compressibility_index']} | "
            f"{prepared(result)} de {len(result['candidates'])} | "
            f"{needed if needed is not None else 'n/d'} | {best_fitting_regions(result)} | {verdict(result)} |"
        )

    reasons = Counter(preparation_reason(candidate["error"])
                      for result in results for candidate in result["candidates"] if "error" in candidate)
    total = sum(len(result["candidates"]) for result in results)
    lines += ["", "## Candidatos que no se pueden preparar", ""]
    if reasons:
        lines += [f"{sum(reasons.values())} de {total} candidatos fallan antes de construir el QUBO:", "",
                  "| Motivo | Candidatos |", "|---|---|"]
        lines += [f"| {reason} | {count} |" for reason, count in reasons.most_common()]
    else:
        lines.append(f"Los {total} candidatos se pueden preparar.")

    lines += ["", "## Qubits por región", ""]
    for result in results:
        lines += [f"**{result['instance']}**", ""]
        for candidate in result["candidates"]:
            label = f"K_min+{candidate['k_offset']}"
            if "error" in candidate:
                lines.append(f"- {label}: no se puede preparar ({candidate['error']})")
            else:
                regions = ", ".join(f"{region['depot_id']}: {region['qubits']}"
                                    + ("" if region["fits"] else " ✗") for region in candidate["regions"])
                lines.append(f"- {label}: {'cabe' if candidate['fits'] else 'no cabe'} [{regions}]")
        lines.append("")

    lines += [
        "## Cómo se decide",
        "",
        "Una instancia va por la vía cuántica de `QISKIT` solo si pasa los tres pasos de cqvrp:",
        "",
        "1. **Filtro de `/analyze`:** granularidad de la demanda G < 0.6, tensión de capacidad "
        "Tc <= 0.9 y compresibilidad espacial S <= 1.2. No construye ningún modelo.",
        "2. **Preparación:** reparto de los clientes por depósito y agrupación en Súper-Clientes. "
        "Un candidato se descarta si algún Súper-Cliente supera la capacidad del vehículo.",
        f"3. **Límite de qubits:** cada región es un QUBO, y el candidato se descarta en cuanto una "
        f"región supera {q_max} qubits.",
        "",
        "`DWAVE` no tiene el límite de qubits del paso 3, pero comparte los pasos 1 y 2: una "
        "instancia que no se puede preparar tampoco llega a D-Wave Leap.",
        "",
        "## Limitaciones",
        "",
        "- Los qubits se obtienen de forma indirecta: cqvrp no expone su cálculo, así que se llama "
        "a su solver con un límite de 0 qubits y se lee el número del error, que salta antes de "
        "simular. Si cqvrp cambia ese mensaje, el cálculo deja de funcionar.",
        "- No se ejecuta QAOA: que una instancia quepa no garantiza que QAOA encuentre una "
        "solución factible.",
        "",
    ]
    path.write_text("\n".join(lines), encoding="utf-8")


def cordeau_names(cqvrp_path):
    return sorted(path.name for path in (cqvrp_path / "tests" / "data").glob("p[0-9][0-9]"))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("instances", nargs="*",
                        help="Nombres de cqvrp/tests/data (p01...) o ficheros .json. Por defecto, todas las pNN")
    parser.add_argument("--url", default="http://localhost:8095", help="Engine cuántico (por defecto el de Docker)")
    parser.add_argument("--max-qubits", type=int, default=24, help="Capacidad cuántica disponible (Q_max)")
    parser.add_argument("--include-mtz", action="store_true", help="Contar qubits con las restricciones MTZ")
    parser.add_argument("--cqvrp", type=Path, default=Path(os.getenv("CQVRP_PATH", DEFAULT_CQVRP)),
                        help="Carpeta quantum-solver-ms de cqvrp")
    parser.add_argument("--out", default=str(DEFAULT_OUT_DIR),
                        help="Directorio donde dejar el informe y los datos (por defecto results/)")
    parser.add_argument("--label", default="", help="Etiqueta de la ejecución; va al nombre de los ficheros")
    parser.add_argument("--json", action="store_true", help="Escribir también los datos por pantalla en JSON")
    args = parser.parse_args()

    import_cqvrp(args.cqvrp)
    names = args.instances or cordeau_names(args.cqvrp)

    out_dir = Path(args.out)
    if not out_dir.is_absolute():
        out_dir = BASE_DIR / out_dir
    out_dir.mkdir(parents=True, exist_ok=True)
    started = datetime.now()
    run_id, (data_path, report_path) = reserve_run(out_dir, started.strftime("%Y-%m-%d-%H%M"), args.label)
    print(f"Ejecución {run_id}  |  {len(names)} instancias  |  Q_max {args.max_qubits}"
          f"{'  |  con MTZ' if args.include_mtz else ''}")

    results = []
    clock = time.perf_counter()
    try:
        for name in names:
            results.append(evaluate(name, args))
            if not args.json:
                print_report(results[-1])
    except KeyboardInterrupt:
        # Lo analizado hasta aquí se conserva y el informe se genera igual.
        print("\n\nInterrumpido. Se genera el informe con lo analizado hasta ahora.")

    if not results:
        print("\nNo hay ninguna instancia analizada.")
        return

    commit, dirty = git_commit(BASE_DIR)
    cqvrp_commit, cqvrp_dirty = git_commit(args.cqvrp)
    meta = {
        "run_id": run_id,
        "started": started.isoformat(timespec="seconds"),
        "started_text": started.strftime("%d/%m/%Y %H:%M:%S"),
        "duration_s": round(time.perf_counter() - clock),
        "url": args.url,
        "max_qubits": args.max_qubits,
        "include_mtz": args.include_mtz,
        "commit": commit,
        "dirty": dirty,
        "cqvrp_commit": cqvrp_commit,
        "cqvrp_dirty": cqvrp_dirty,
    }
    data = {key: value for key, value in meta.items() if key != "started_text"}
    data["results"] = results
    data_path.write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding="utf-8")
    write_report(report_path, meta, results, data_path.name)

    if args.json:
        print(json.dumps(results, indent=2, ensure_ascii=False))
    print(f"\nDatos:   {data_path}")
    print(f"Informe: {report_path}")


if __name__ == "__main__":
    main()
