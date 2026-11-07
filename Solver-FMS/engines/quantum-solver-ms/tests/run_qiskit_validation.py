"""Prueba manual breve. No usa fallback ni modifica la configuración del servicio.

python -B tests/run_qiskit_validation.py --case two-clients --seed 7
python -B tests/run_qiskit_validation.py --case quantum_small.txt --timeout 120
"""

import argparse
from collections import Counter
import json
from pathlib import Path
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from domain.models import CustomerDto, DepotDto, RoutingRequest, VehicleDto
from services.evaluation_service import prepare_quantum_subrequests
from services.route_expansion_service import expand_quantum_routes
from solvers.quantum_qiskit import solve_with_qiskit_with_timeout
from utils.parser import CordeauParser


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--case", default="two-clients", choices=[
        "two-clients", "three-clients", "clustered-pair", "quantum_small.txt", "quantum_demo.txt",
    ])
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--timeout", type=float, default=None)
    parser.add_argument("--maxiter", type=int, default=20)
    parser.add_argument("--reps", type=int, default=1)
    parser.add_argument("--mtz", action="store_true")
    parser.add_argument("--output", type=Path, help="JSON opcional. Por defecto solo imprime el resultado")
    args = parser.parse_args()
    if args.case.endswith(".txt"):
        request = CordeauParser.parse_instance(str(ROOT / "tests/data" / args.case), args.case)
    else:
        count = 3 if args.case == "three-clients" else 2
        points = [(10, 0), (11, 0), (10, 3)]
        request = RoutingRequest(
            instance_name=args.case, depots=[DepotDto(id="D", x=0, y=0)],
            customers=[CustomerDto(id=f"C{i+1}", x=points[i][0], y=points[i][1], demand=1)
                       for i in range(count)],
            vehicles=[VehicleDto(id="V", capacity=count + 1)],
        )
    reduced = (prepare_quantum_subrequests(request)[0]
               if args.case in ["clustered-pair", "quantum_small.txt", "quantum_demo.txt"] else [request])
    result = {"case": args.case, "seed": args.seed, "maxiter": args.maxiter,
              "reps": args.reps, "mtz": args.mtz, "timeout_sec": args.timeout,
              "original_customers": len(request.customers),
              "reduced_customers": sum(len(r.customers) for r in reduced)}
    started = time.perf_counter()
    try:
        raw = solve_with_qiskit_with_timeout(
            reduced, include_mtz=args.mtz, timeout_sec=args.timeout,
            maxiter=args.maxiter, reps=args.reps, seed=args.seed,
        )
        response, _ = expand_quantum_routes(request, reduced, raw)
        assert Counter(c for r in response.routes for c in r.stops[1:-1]) == Counter(c.id for c in request.customers)
        result.update(status="valid", qubits=response.qubits, cost=response.total_distance,
                      routes=[r.stops for r in response.routes])
    except Exception as error:
        result.update(status="not_solved", reason=str(error))
    result["elapsed_sec"] = round(time.perf_counter() - started, 3)
    content = json.dumps(result, indent=2, ensure_ascii=False) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(content, encoding="utf-8")
    print(content)


if __name__ == "__main__":
    main()
