from fastapi import FastAPI, Header, HTTPException
from fastapi.exceptions import RequestValidationError
from pydantic import ValidationError
from typing import Optional, Dict, Any

from domain.models import RoutingRequest
from api.adapter.models import PyvrpParameters, RoutingRequestStd, RoutingResponseStd
from api.adapter.specification import ParamSpecification, ParamFactory, ParamTypes
from api.adapter.specification import OpenSpecificationFactory, OpenMetada
from api.adapter.adapter import Adapter
from solvers.pyvrp_v2_solver import solve_hgs_adc, hgs_adc_manifest
from solvers.pyvrp_v2_solver import PARAM_SPECIFICATIONS as HGS_ADC_PARAMS

from services.evaluation_service import (
    QuantumPreparationError,
    evaluate_quantum_metrics,
    evaluate_initial_quantum_viability,
    solve_best_quantum_candidate,
    solve_with_classical,
    solve_with_classical_fallback,
)

# Lista única de backends admitidos por la cabecera HTTP y por los endpoints
# informativos. Evita que la documentación y la validación diverjan.
SUPPORTED_BACKENDS = frozenset({"qiskit", "dwave", "classical"})

app = FastAPI(
    title="Delivera Quantum Solver Microservice",
    description="Microservicio cuántico-clásico para la resolución y evaluación de problemas MD-CVRP.",
    version="1.0.0"
)


def _get_backend(value: Optional[str]) -> str:
    """Normaliza y valida el backend solicitado en la cabecera HTTP."""
    backend = (value or "qiskit").strip().lower()
    if backend not in SUPPORTED_BACKENDS:
        valid_values = ", ".join(sorted(SUPPORTED_BACKENDS))
        raise HTTPException(
            status_code=422,
            detail=(
                "Valor no válido para X-Target-Quantum-Backend. "
                f"Valores admitidos: {valid_values}."
            ),
        )
    return backend


@app.get("/health", response_model=Dict[str, Any])
async def health() -> Dict[str, Any]:
    """Expone un estado mínimo para monitores y orquestadores."""
    return {
        "status": "ok",
        "service": "quantum-solver-ms",
        "version": app.version,
        "supported_backends": sorted(SUPPORTED_BACKENDS),
    }


@app.post("/api/v1/quantum-routing/analyze", response_model=Dict[str, Any])
async def analyze_quantum_route(request: RoutingRequest) -> Dict[str, Any]:
    """Analiza la viabilidad inicial sin ejecutar un solver de rutas."""
    viability = evaluate_initial_quantum_viability(request)
    return {
        "instance_name": request.instance_name,
        "analysis": viability,
        "supported_backends": sorted(SUPPORTED_BACKENDS),
    }


@app.post("/api/v1/quantum-routing/analyze/test", response_model=Dict[str, Any])
async def analyze_quantum_route_test(_request: RoutingRequestStd) -> Dict[str, Any]:
    """Analiza la viabilidad inicial sin ejecutar un solver de rutas."""
    request = Adapter.adapt_request(_request)
    viability = evaluate_initial_quantum_viability(request)
    return {
        "instance_name": request.instance_name,
        "analysis": viability,
        "supported_backends": sorted(SUPPORTED_BACKENDS),
    }



# TODO: DELETE
@app.post("/api/v1/adapter/test", response_model=RoutingRequest)
async def test_adapter(request: RoutingRequestStd) -> Dict[str, Any]:
    """Analiza la viabilidad inicial sin ejecutar un solver de rutas."""
    return Adapter.adapt_request(request)

# TODO: DELETE
@app.post("/api/v1/manifest/test", response_model=Dict[str, Any])
async def test_manifest() -> Dict[str, Any]:
    """Muestra el manifiesto del servicio."""

    return hgs_adc_manifest()

@app.post("/api/v1/HGS-ADC/solve", response_model=RoutingResponseStd)
async def solve(_request: RoutingRequestStd):
    """Lee las opciones de Delivera desde parameters, sin cabeceras de configuración."""
    try:
        options = PyvrpParameters.model_validate(_request.parameters)
    except ValidationError as exc:
        errors = [
            {**error, "loc": ("body", "parameters", *error["loc"])}
            for error in exc.errors(include_url=False)
        ]
        raise RequestValidationError(errors) from exc
    try:
        return solve_hgs_adc(
            request=_request,
            seed=options.seed,
            max_runtime_sec=options.classical_time_limit,
            max_vehicle_repetition=options.max_vehicle_repetition,
            vehicle_strategy_config=options.vehicle_strategy_config,
            vehicle_strategy=options.vehicle_strategy,
        )
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc



@app.post("/api/v1/quantum-routing/solve", response_model=Dict[str, Any])
async def solve_quantum_route(
    #_request: RoutingRequestStd, 
    request: RoutingRequest,
    x_target_quantum_backend: Optional[str] = Header(default="qiskit"),
    x_quantum_timeout_seconds: float = Header(default=30.0, gt=0, le=300),
    x_classical_time_limit: int = Header(default=60, ge=1, le=300),
    x_max_qubits: int = Header(default=24, ge=1, le=24),
    x_include_mtz: bool = Header(default=False),
):
    """
    Resuelve una instancia con el backend seleccionado y devuelve sus métricas.

    Qiskit y D-Wave aplican el flujo de preparación cuántica. PyVRP resuelve
    directamente la instancia global cuando se solicita ``classical``.
    """
    # TODO:  request =  Adapter.adapt_request(_request)
    backend = _get_backend(x_target_quantum_backend)

    if backend == "classical":
        # PyVRP es una ejecución explícita de la instancia global. No se
        # comprime el grafo ni se aplica el filtro pensado para la vía cuántica.
        raw_response = solve_with_classical(
            request,
            max_runtime_sec=x_classical_time_limit,
        )
        preparation_metrics = {
            "execution_mode": "classical_direct",
            "initial_viability": None,
        }
        # La solución solicitada es también la referencia clásica, de modo que
        # su degradación relativa debe ser exactamente cero.
        classical_cost = raw_response.total_distance
    else:
        # Qiskit y D-Wave comparten el preprocesamiento y pueden derivar a
        # PyVRP si la instancia no admite una ejecución cuántica viable.
        preparation_error = None
        viability = evaluate_initial_quantum_viability(request)
        if not viability["is_viable"]:
            preparation_error = (
                "La instancia no supera el filtro inicial de GraphAnalyzer."
            )
            preparation_metrics = {
                "fallback": "initial_viability",
                "reason": preparation_error,
            }
        else:
            try:
                raw_response, preparation_metrics = solve_best_quantum_candidate(
                    request,
                    backend=backend,
                    max_qubits=x_max_qubits,
                    max_k_offset=3,
                    include_mtz=x_include_mtz,
                    quantum_timeout_sec=x_quantum_timeout_seconds,
                )
            except QuantumPreparationError as error:
                preparation_error = str(error)
                preparation_metrics = {
                    "fallback": "preparation",
                    "reason": preparation_error,
                }
        preparation_metrics["initial_viability"] = viability

        classical_cost = None
        if preparation_error:
            raw_response = solve_with_classical_fallback(
                request,
                preparation_error,
                max_runtime_sec=x_classical_time_limit,
            )
            # El fallback ya ha resuelto la instancia global con PyVRP. Reusar
            # su coste evita ejecutar PyVRP una segunda vez al crear métricas.
            classical_cost = raw_response.total_distance

    # Todas las rutas, incluidas las clásicas y los fallbacks, se exponen con
    # el mismo esquema de métricas para simplificar su consumo por el orquestador.
    metrics = evaluate_quantum_metrics(
        request,
        raw_response,
        classical_cost=classical_cost,
        internal_cost=preparation_metrics.get("internal_cost", 0.0),
        classical_time_limit=x_classical_time_limit,
    )
    metrics["preparation"] = preparation_metrics
    metrics["backend_requested"] = backend

    # TODO Adapter.adapt_response(raw_response),
    return {
        "routing_response": raw_response.model_dump(),
        "metrics": metrics
    }
