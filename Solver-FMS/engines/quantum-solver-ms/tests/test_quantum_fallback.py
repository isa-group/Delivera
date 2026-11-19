"""Fallback PyVRP2 real y contrato HTTP, sin ejecutar backends cuánticos."""

import asyncio
from collections import Counter
import json
import unittest
from unittest.mock import patch

from pyvrp.stop import MaxIterations

from api.adapter.adapter import Adapter
from api.adapter.fallback_adapter import PyvrpFallbackAdapter, fallback_parameters
from api.main import app
from domain.models import CustomerDto, DepotDto, RouteDto, RoutingRequest, RoutingResponse, VehicleDto
from services.evaluation_service import QuantumPreparationError, solve_with_classical_fallback
from solvers.pyvrp_v2_solver import solve_hgs_adc


def make_request():
    return RoutingRequest(
        instance_name="fallback-original", depots=[DepotDto(id="D", x=0, y=0)],
        customers=[CustomerDto(id="C1", x=1, y=0, demand=6),
                   CustomerDto(id="C2", x=2, y=0, demand=6)],
        vehicles=[VehicleDto(id="V", capacity=10)],
    )


class TestQuantumFallback(unittest.TestCase):
    def setUp(self):
        # PyVRP es real. Las iteraciones fijas evitan depender de la velocidad del equipo.
        timer = patch("solvers.pyvrp_v2_solver.MaxRuntime", return_value=MaxIterations(500))
        timer.start()
        self.addCleanup(timer.stop)

    def post(self, request, backend="qiskit"):
        body = json.dumps(request.model_dump(mode="json")).encode()
        messages = []

        async def receive():
            return {"type": "http.request", "body": body, "more_body": False}

        async def send(message):
            messages.append(message)

        scope = {
            "type": "http", "method": "POST", "path": "/api/v1/quantum-routing/solve",
            "query_string": b"", "headers": [(b"content-type", b"application/json"),
                (b"x-target-quantum-backend", backend.encode()), (b"x-classical-time-limit", b"1")],
            "http_version": "1.1", "scheme": "http",
            "server": ("test", 80), "client": ("test", 1234),
        }
        asyncio.run(app(scope, receive, send))
        payload = b"".join(m.get("body", b"") for m in messages)
        result = json.loads(payload)
        json.dumps(result, allow_nan=False)
        return messages[0]["status"], result

    def test_adapter_preserves_depot_blocks_and_rounded_native_distances(self):
        request = make_request()
        request.depots.append(DepotDto(id="D2", x=100, y=0))
        request.vehicles.append(VehicleDto(id="V2", capacity=4))
        request.customers[0].x = 0.6
        original = request.model_dump()
        adapted = PyvrpFallbackAdapter.to_request(request)
        self.assertEqual(adapted.problem_id, request.instance_name)
        self.assertEqual([(v.id, v.start_depot_id, v.capacity) for v in adapted.vehicles],
                         [("V", "D", 10), ("V2", "D2", 4)])
        self.assertEqual([n.matrix_index for n in adapted.depots + adapted.customers], [0, 1, 2, 3])
        self.assertEqual(adapted.distance_matrix[0][2], 1)
        self.assertEqual(adapted.distance_matrix[1][2], 99)
        self.assertEqual(request.model_dump(), original)

    def test_adapter_rejects_ambiguous_fleet_and_precompressed_customers(self):
        request = make_request()
        request.depots.append(DepotDto(id="D2", x=100, y=0))
        with self.assertRaisesRegex(ValueError, "uniformemente"):
            PyvrpFallbackAdapter.to_request(request)
        request = make_request()
        request.customers[0].internal_nodes = [request.customers[1]]
        with self.assertRaisesRegex(ValueError, "originales"):
            PyvrpFallbackAdapter.to_request(request)

    def test_default_single_trip_failure_is_null_and_does_not_retry_classical(self):
        with patch("services.evaluation_service.solve_with_pyvrp") as old_solver:
            status, payload = self.post(make_request())
        old_solver.assert_not_called()
        self.assertEqual(status, 200)
        response = payload["routing_response"]
        self.assertFalse(response["is_feasible"])
        self.assertIsNone(response["total_distance"])
        self.assertEqual(response["routes"], [])
        self.assertEqual(response["solver_details"]["maxTripsPerVehicle"], 1)
        self.assertTrue(response["solver_details"]["failureReason"])
        self.assertIsNone(payload["metrics"]["total_cost"])
        self.assertIsNone(payload["metrics"]["classical_reference_distance"])
        self.assertIsNone(payload["metrics"]["relative_degradation"])

    def test_authorized_reassignment_works_for_both_quantum_backends(self):
        for backend in ["qiskit", "dwave"]:
            with self.subTest(backend=backend):
                request = make_request()
                request.parameters = {"fallback": {"maxVehicleRepetition": 2,
                    "vehicleStrategy": "distance", "vehicleStrategyConfig": "balanced", "seed": 7}}
                with patch("api.main.solve_best_quantum_candidate") as quantum:
                    status, payload = self.post(request, backend)
                quantum.assert_not_called()  # La demanda supera el filtro inicial.
                self.assertEqual(status, 200)
                response = payload["routing_response"]
                self.assertTrue(response["is_feasible"])
                self.assertEqual(response["solver_used"], "HGS-ADC classical fallback")
                self.assertEqual(response["instance_name"], request.instance_name)
                self.assertEqual(response["seed"], 7)
                self.assertEqual(response["solver_details"]["additionalTrips"], 1)
                self.assertFalse(response["solver_details"]["scheduleValidated"])
                self.assertEqual([r["trip_index"] for r in response["routes"]], [1, 2])
                self.assertTrue(all(r["vehicle_id"] == "V" for r in response["routes"]))
                self.assertTrue(all(r["stops"][0] == r["stops"][-1] == "D" for r in response["routes"]))
                self.assertEqual(Counter(c for r in response["routes"] for c in r["stops"][1:-1]),
                                 Counter(c.id for c in request.customers))
                self.assertEqual(response["total_distance"], 6)
                self.assertTrue(response["fallback_reason"])
                self.assertEqual(payload["metrics"]["backend_requested"], backend)
                self.assertEqual(payload["metrics"]["relative_degradation"], 0)

    def test_heterogeneous_fleet_and_fallback_after_quantum_failure(self):
        request = make_request()
        request.customers.append(CustomerDto(id="C3", x=3, y=0, demand=2))
        request.vehicles.append(VehicleDto(id="SMALL", capacity=4))
        request.parameters = {"fallback": {"maxVehicleRepetition": 2, "classicalTimeLimit": 0.25}}
        with patch("api.main.evaluate_initial_quantum_viability", return_value={"is_viable": True}), patch(
            "api.main.solve_best_quantum_candidate", side_effect=QuantumPreparationError("quantum failed")), patch(
            "services.evaluation_service.solve_hgs_adc", wraps=solve_hgs_adc,
        ) as solver:
            status, payload = self.post(request)
        self.assertEqual(status, 200)
        self.assertTrue(payload["routing_response"]["is_feasible"])
        self.assertEqual(payload["routing_response"]["fallback_reason"], "quantum failed")
        self.assertEqual(solver.call_args.kwargs["max_runtime_sec"], 0.25)
        self.assertEqual(solver.call_args.kwargs["max_vehicle_repetition"], 2)

    def test_invalid_options_fail_before_running_any_solver(self):
        invalid = [
            {"fallback": {"maxVehicleRepetition": value}} for value in [0, 11, 1.5, True]
        ] + [{"fallback": {"vehicleStrategy": "unknown"}}, {"fallback": {"typo": 2}},
             {"fallback": {"classicalTimeLimit": 0}}, {"fallback": {"seed": -1}},
             {"fallback": None}, {"fallback": []}, {"fallbak": {}}]
        with patch("api.main.evaluate_initial_quantum_viability") as analyze, patch(
            "api.main.solve_with_classical_fallback",
        ) as solver:
            for parameters in invalid:
                with self.subTest(parameters=parameters):
                    request = make_request()
                    request.parameters = parameters
                    status, _ = self.post(request)
                    self.assertEqual(status, 422)
        analyze.assert_not_called()
        solver.assert_not_called()

    def test_quantum_success_does_not_apply_fallback_reassignment_to_reference(self):
        request = make_request()
        for customer in request.customers:
            customer.demand = 1
        request.parameters = {"fallback": {"maxVehicleRepetition": 3, "seed": 7}}
        response = RoutingResponse(instance_name=request.instance_name, solver_used="quantum-test",
            execution_time_ms=1, total_distance=4, is_feasible=True,
            routes=[RouteDto(vehicle_id="V", stops=["D", "C1", "C2", "D"], distance=4, load=2)])
        with patch("api.main.solve_best_quantum_candidate", return_value=(response, {})) as quantum, patch(
            "api.main.solve_with_classical_fallback",
        ) as fallback, patch("services.evaluation_service.solve_with_pyvrp", return_value=response) as reference:
            status, payload = self.post(request)
        self.assertEqual(status, 200)
        fallback.assert_not_called()
        reference.assert_called_once_with(request, max_runtime_sec=1)
        self.assertNotIn("max_vehicle_repetition", quantum.call_args.kwargs)
        self.assertIsNone(payload["routing_response"]["solver_details"])
        self.assertIsNone(payload["routing_response"]["routes"][0]["trip_index"])

    def test_parameters_inherit_classical_budget_and_direct_calls_use_request_options(self):
        request = make_request()
        self.assertEqual(fallback_parameters(request, 12).classical_time_limit, 12)
        request.parameters = {"fallback": {"maxVehicleRepetition": 2}}
        response = solve_with_classical_fallback(request, "test", max_runtime_sec=0.1)
        self.assertTrue(response.is_feasible)
        self.assertEqual(response.solver_details["maxTripsPerVehicle"], 2)

    def test_delivera_adapter_preserves_options_until_fallback(self):
        standard = PyvrpFallbackAdapter.to_request(make_request())
        standard.parameters = {"fallback": {"maxVehicleRepetition": 2,
                                            "classicalTimeLimit": 0.1, "seed": 7}}
        request = Adapter.adapt_request(standard)
        response = solve_with_classical_fallback(request, "test")
        self.assertTrue(response.is_feasible)
        self.assertEqual(response.seed, 7)
        self.assertEqual(response.solver_details["maxTripsPerVehicle"], 2)
        self.assertEqual(response.solver_details["additionalTrips"], 1)

    def test_empty_instance_needs_no_search_and_ambiguous_fleet_returns_422(self):
        request = make_request()
        request.customers = []
        with patch("solvers.pyvrp_v2_solver.Model.solve") as search:
            response = solve_with_classical_fallback(request, "empty", max_runtime_sec=0.1)
        search.assert_not_called()
        self.assertTrue(response.is_feasible)
        self.assertEqual(response.total_distance, 0)
        self.assertEqual(response.routes, [])
        request = make_request()
        request.depots.append(DepotDto(id="OTHER", x=10, y=0))
        status, _ = self.post(request)
        self.assertEqual(status, 422)


if __name__ == "__main__":
    unittest.main()
