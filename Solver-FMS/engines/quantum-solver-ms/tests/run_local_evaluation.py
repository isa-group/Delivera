"""Evaluacion local del flujo del microservicio cuantico con instancias Cordeau.

Ejemplos desde la raiz del proyecto::

	python tests/run_local_evaluation.py --instance p01
	python tests/run_local_evaluation.py --all --backend classical

El parser solo se usa aqui. El resto del flujo reproduce la preparacion,
seleccion de backend, fallback y calculo de metricas de produccion.
"""

from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path
from typing import Any, Dict, Iterable, List

PROJECT_ROOT = Path(__file__).resolve().parents[1]
DATA_DIR = Path(__file__).resolve().parent / "data"
# Permite ejecutar el archivo directamente desde la carpeta del proyecto sin
# instalar el microservicio como paquete Python.
sys.path.insert(0, str(PROJECT_ROOT))

from domain.models import RoutingRequest, RoutingResponse
from services.evaluation_service import (
	QuantumPreparationError,
	evaluate_quantum_metrics,
	evaluate_initial_quantum_viability,
	solve_best_quantum_candidate,
	solve_with_classical,
	solve_with_classical_fallback,
)
from utils.parser import CordeauParser


def _paths_for(instance: str | None, evaluate_all: bool) -> List[Path]:
	# El runner admite una instancia concreta o todas las pNN del benchmark.
	if evaluate_all:
		paths = sorted(DATA_DIR.glob("p[0-9][0-9]"))
		if not paths:
			raise FileNotFoundError(f"No hay instancias pNN en {DATA_DIR}")
		return paths

	path = DATA_DIR / (instance or "p01")
	if not path.is_file():
		raise FileNotFoundError(f"No existe la instancia Cordeau: {path}")
	return [path]


def _jsonable(value: Any) -> Any:
	# Convierte valores NumPy/Pydantic simples a tipos que json.dumps entiende.
	if isinstance(value, dict):
		return {str(key): _jsonable(item) for key, item in value.items()}
	if isinstance(value, (list, tuple)):
		return [_jsonable(item) for item in value]
	if hasattr(value, "item"):
		return value.item()
	return value


def evaluate_instance(
	filepath: Path,
	backend: str = "qiskit",
	max_qubits: int = 24,
	include_mtz: bool = False,
	classical_time_limit: int = 60,
	quantum_time_limit: float = 30.0,
) -> Dict[str, Any]:
	"""Ejecuta una instancia como si fuera una llamada al microservicio.

	Primero aplica el filtro barato, despues explora los candidatos K y, si
	ninguno funciona, usa el fallback clasico. El resultado conserva tanto la
	respuesta de routing como las metricas para poder inspeccionarlo en JSON.
	"""
	started = time.perf_counter()
	request = CordeauParser.parse_instance(filepath, filepath.name)
	preparation: Dict[str, Any]
	preparation_error = None

	if backend == "classical":
		# El modo clásico reproduce la ruta directa de la API. Por ello no se
		# calcula viabilidad cuántica ni se ejecuta GAP o clustering.
		preparation = {
			"execution_mode": "classical_direct",
			"initial_viability": None,
		}
		response = solve_with_classical(
			request,
			max_runtime_sec=classical_time_limit,
		)
		# La propia solución de PyVRP es la referencia con la que se compara.
		classical_cost = response.total_distance
	else:
		# El filtro pre-GAP solo usa features globales. No intenta adivinar K
		# regional ni el número final de qubits.
		viability = evaluate_initial_quantum_viability(request)
		classical_cost = None
		if not viability["is_viable"]:
			preparation_error = "La instancia no supera el filtro inicial de GraphAnalyzer."
			preparation = {"fallback": "initial_viability", "reason": preparation_error}
		else:
			# El servicio prueba K_min..K_min+3. Cada candidato se descarta si
			# falla capacidad, subtours o el límite de qubits.
			try:
				response, preparation = solve_best_quantum_candidate(
					request,
					backend=backend,
					max_qubits=max_qubits,
					max_k_offset=3,
					include_mtz=include_mtz,
					quantum_timeout_sec=quantum_time_limit,
				)
			except QuantumPreparationError as error:
				preparation_error = str(error)
				preparation = {"fallback": "preparation", "reason": preparation_error}
		preparation["initial_viability"] = viability

		# El fallback devuelve rutas clásicas completas, no solo una distancia.
		if preparation_error:
			response = solve_with_classical_fallback(
				request,
				preparation_error,
				max_runtime_sec=classical_time_limit,
			)
			# La solución de fallback ya es la referencia clásica. Así se evita
			# una segunda ejecución PyVRP al calcular las métricas.
			classical_cost = response.total_distance

	# Usamos exactamente la misma funcion de metricas que el endpoint. Esto
	# evita que el runner local se quede desactualizado si cambia evaluation_service.
	metrics = evaluate_quantum_metrics(
		request,
		response,
		classical_cost=classical_cost,
		internal_cost=preparation.get("internal_cost", 0.0),
		classical_time_limit=classical_time_limit,
	)
	metrics["preparation"] = preparation
	metrics["backend_requested"] = backend

	# Se agrupan entrada, respuesta, tiempo y metricas en un documento estable.
	return _jsonable({
		"instance": filepath.name,
		"customers": len(request.customers),
		"depots": len(request.depots),
		"vehicles": len(request.vehicles),
		"elapsed_ms": (time.perf_counter() - started) * 1000,
		"routing_response": response.model_dump(),
		"metrics": metrics,
	})


def _print_result(result: Dict[str, Any]) -> None:
	# La salida de consola muestra lo esencial; --json conserva el detalle.
	response = result["routing_response"]
	metrics = result["metrics"]
	degradation = metrics["relative_degradation"]
	degradation_text = "n/a" if degradation is None else f"{degradation:.2%}"
	print(
		f"{result['instance']}: solver={response['solver_used']} | "
		f"coste_total={metrics['total_cost']:.2f} | "
		f"distancia_global={response['total_distance']:.2f} | "
		f"coste_interno={metrics['internal_cost']:.2f} | "
		f"referencia={metrics['classical_reference_distance']:.2f} | "
		f"degradacion={degradation_text} | factible={metrics['is_feasible']} | "
		f"qubits={response.get('qubits') or 'n/a'}"
	)
	if response.get("fallback_reason"):
		print(f"  fallback: {response['fallback_reason']}")


def build_parser() -> argparse.ArgumentParser:
	# Los argumentos permiten reproducir localmente distintas condiciones del
	# microservicio sin modificar el codigo.
	parser = argparse.ArgumentParser(description=__doc__)
	group = parser.add_mutually_exclusive_group()
	group.add_argument("--instance", help="Archivo Cordeau, por ejemplo p01")
	group.add_argument("--all", action="store_true", help="Evalua todas las instancias pNN")
	parser.add_argument("--backend", choices=("qiskit", "dwave", "classical"), default="qiskit")
	parser.add_argument("--max-qubits", type=int, default=24)
	parser.add_argument("--classical-time-limit", type=int, default=60)
	parser.add_argument(
		"--quantum-time-limit",
		type=float,
		default=30.0,
		help="Límite en segundos para una ejecución Qiskit antes del fallback",
	)
	parser.add_argument(
		"--include-mtz",
		action="store_true",
		help="Incluye restricciones MTZ para eliminar subtours en el QUBO",
	)
	parser.add_argument("--json", action="store_true", dest="as_json")
	return parser


def main(argv: Iterable[str] | None = None) -> int:
	args = build_parser().parse_args(argv)
	try:
		results = []
		# Con --all, un caso defectuoso se registra y no impide evaluar los demas.
		for path in _paths_for(args.instance, args.all):
			try:
				result = evaluate_instance(
					path,
					backend=args.backend,
					max_qubits=args.max_qubits,
					include_mtz=args.include_mtz,
					classical_time_limit=args.classical_time_limit,
					quantum_time_limit=args.quantum_time_limit,
				)
				results.append(result)
				if not args.as_json:
					_print_result(result)
			except Exception as error:
				print(f"{path.name}: ERROR: {error}", file=sys.stderr)
				if not args.all:
					return 1
		if args.as_json:
			print(json.dumps(results, indent=2, ensure_ascii=True))
		return 0 if results else 1
	except (FileNotFoundError, ValueError) as error:
		print(f"ERROR: {error}", file=sys.stderr)
		return 1


if __name__ == "__main__":
	raise SystemExit(main())
