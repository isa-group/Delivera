import asyncio
import unittest
from unittest.mock import patch

from fastapi import HTTPException

from api.main import analyze_quantum_route, health, solve_quantum_route
from services.evaluation_service import QuantumPreparationError
from domain.models import (
    CustomerDto,
    DepotDto,
    RouteDto,
    RoutingRequest,
    RoutingResponse,
    VehicleDto,
)


class TestApiEndpoints(unittest.TestCase):
    """Pruebas unitarias de los endpoints sin levantar Uvicorn ni solvers."""

    def setUp(self):
        # Instancia mínima reutilizable para los endpoints que reciben JSON.
        self.request = RoutingRequest(
            instance_name="api-test",
            depots=[DepotDto(id="D1", x=0, y=0)],
            customers=[CustomerDto(id="C1", x=10, y=0, demand=10)],
            vehicles=[VehicleDto(id="V1", capacity=100)],
        )

    def test_health_exposes_service_status_and_backends(self):
        # Las funciones asíncronas de FastAPI se ejecutan directamente con
        # asyncio.run(), sin necesidad del cliente HTTP opcional de Starlette.
        result = asyncio.run(health())

        self.assertEqual(result["status"], "ok")
        self.assertEqual(result["service"], "quantum-solver-ms")
        self.assertEqual(
            result["supported_backends"], ["classical", "dwave", "qiskit"]
        )

    def test_analyze_returns_viability_without_invoking_a_solver(self):
        result = asyncio.run(analyze_quantum_route(self.request))

        self.assertEqual(result["instance_name"], "api-test")
        self.assertIn("is_viable", result["analysis"])
        self.assertIn("features", result["analysis"])

    def test_classical_backend_uses_pyvrp_directly(self):
        # Esta es la respuesta que devolvería PyVRP. Se usa para aislar la
        # lógica de la API del coste real de ejecutar una metaheurística.
        pyvrp_response = RoutingResponse(
            instance_name="api-test",
            solver_used="PyVRP",
            execution_time_ms=5.0,
            total_distance=20.0,
            is_feasible=True,
            routes=[
                RouteDto(
                    vehicle_id="V1",
                    stops=["D1", "C1", "D1"],
                    distance=20.0,
                    load=10,
                )
            ],
        )

        # patch sustituye temporalmente la función real por un Mock. Dentro
        # del bloque no se ejecuta PyVRP y "solver" permite comprobar la llamada.
        with patch("api.main.solve_with_classical", return_value=pyvrp_response) as solver:
            result = asyncio.run(
                solve_quantum_route(
                    self.request,
                    x_target_quantum_backend="classical",
                    x_classical_time_limit=12,
                )
            )

        solver.assert_called_once_with(self.request, max_runtime_sec=12)
        self.assertEqual(result["routing_response"]["solver_used"], "PyVRP")
        self.assertIsNone(result["routing_response"]["fallback_reason"])
        self.assertEqual(result["metrics"]["backend_requested"], "classical")
        self.assertEqual(
            result["metrics"]["preparation"]["execution_mode"],
            "classical_direct",
        )
        self.assertEqual(result["metrics"]["relative_degradation"], 0.0)

    def test_invalid_backend_is_rejected(self):
        # assertRaises captura la excepción esperada. "context" conserva la
        # excepción capturada para poder comprobar después su código HTTP.
        with self.assertRaises(HTTPException) as context:
            asyncio.run(
                solve_quantum_route(
                    self.request,
                    x_target_quantum_backend="unknown",
                )
            )

        self.assertEqual(context.exception.status_code, 422)

    def test_qiskit_preparation_failure_uses_classical_fallback(self):
        fallback_response = RoutingResponse(
            instance_name="api-test",
            solver_used="PyVRP classical fallback",
            execution_time_ms=5.0,
            total_distance=20.0,
            is_feasible=True,
            routes=[],
            fallback_reason="Qiskit superó el límite de 30 segundos.",
        )

        # Se simula el error que produce el timeout de Qiskit. Así se verifica
        # el camino de fallback sin crear un proceso ni ejecutar QAOA.
        with patch(
            "api.main.solve_best_quantum_candidate",
            side_effect=QuantumPreparationError(
                "Qiskit superó el límite de 30 segundos."
            ),
        ) as quantum_solver, patch(
            "api.main.solve_with_classical_fallback",
            return_value=fallback_response,
        ) as fallback_solver, patch(
            "api.main.evaluate_quantum_metrics",
            return_value={
                "quantum_total_distance": 20.0,
                "internal_cost": 0.0,
                "total_cost": 20.0,
                "classical_reference_distance": 20.0,
                "relative_degradation": 0.0,
                "route_count": 0,
                "route_loads": [],
                "capacities": {"V1": 100},
                "capacity_violations": [],
                "is_feasible": True,
                "qubits": None,
            },
        ):
            result = asyncio.run(
                solve_quantum_route(
                    self.request,
                    x_target_quantum_backend="qiskit",
                    x_quantum_timeout_seconds=10,
                    x_classical_time_limit=12,
                    x_max_qubits=20,
                    x_include_mtz=True,
                )
            )

        quantum_solver.assert_called_once_with(
            self.request,
            backend="qiskit",
            max_qubits=20,
            max_k_offset=3,
            include_mtz=True,
            quantum_timeout_sec=10,
        )
        fallback_solver.assert_called_once_with(
            self.request,
            "Qiskit superó el límite de 30 segundos.",
            max_runtime_sec=12,
        )
        self.assertEqual(
            result["routing_response"]["solver_used"],
            "PyVRP classical fallback",
        )
        self.assertEqual(result["metrics"]["backend_requested"], "qiskit")


if __name__ == "__main__":
    unittest.main()
