import asyncio
from collections import Counter
import json
import math
import unittest
from unittest.mock import patch

from fastapi import HTTPException
from pyvrp.stop import MaxIterations

from api.adapter.models import RoutingRequestStd
from api.main import app, solve
from services.vehicle_strategy_service import HybridPresets, VehicleStrategies
from solvers.pyvrp_v2_solver import solve_hgs_adc
from pyvrp import Model


def make_request(demands=(6, 6, 4, 4), capacities=(10, 10), coordinates=None):
    coordinates = coordinates or [(100, 0), (101, 0), (-100, 0), (-101, 0)][:len(demands)]
    points = [(0, 0)] + coordinates
    return RoutingRequestStd.model_validate({
        "problemId": "reassignment-test",
        "depots": [{"id": "D", "lng": 0, "lat": 0, "matrixIndex": 0}],
        "customers": [
            {"id": f"C{i}", "lng": x, "lat": y, "matrixIndex": i + 1, "demand": demand}
            for i, ((x, y), demand) in enumerate(zip(coordinates, demands))
        ],
        "vehicles": [
            {"id": f"V{i}", "capacity": capacity, "startDepotId": "D"}
            for i, capacity in enumerate(capacities)
        ],
        "distanceMatrix": [
            [math.hypot(a[0] - b[0], a[1] - b[1]) for b in points] for a in points
        ],
        "solverType": "PYVRP", "parameters": {},
    })


class TestPyvrpReassignment(unittest.TestCase):
    def run_solver(self, request, limit=1):
        # Iteraciones fijas para que las regresiones no dependan de la velocidad del equipo.
        with patch("solvers.pyvrp_v2_solver.MaxRuntime", return_value=MaxIterations(500)):
            return solve_hgs_adc(
                request, HybridPresets.BALANCED, VehicleStrategies.DISTANCE,
                max_runtime_sec=0.1, max_vehicle_repetition=limit,
            )

    def assert_valid_routes(self, request, result, limit):
        self.assertTrue(result.is_feasible)
        vehicles = {vehicle.id: vehicle for vehicle in request.vehicles}
        customers = {customer.id: customer for customer in request.customers}
        counts = Counter()
        visited = []
        for route in result.routes:
            vehicle = vehicles[route.vehicle_id]
            counts[vehicle.id] += 1
            self.assertEqual(route.depot_id, vehicle.start_depot_id)
            self.assertEqual(route.trip_index, counts[vehicle.id])
            self.assertLessEqual(counts[vehicle.id], limit)
            self.assertEqual(route.total_load, sum(customers[c].demand for c in route.stops))
            self.assertLessEqual(route.total_load, vehicle.capacity)
            visited.extend(route.stops)
        self.assertEqual(Counter(visited), Counter(customers.keys()))
        self.assertAlmostEqual(result.total_cost, sum(route.total_distance for route in result.routes))
        self.assertEqual(result.solver_details.additional_trips, sum(n - 1 for n in counts.values()))
        json.dumps(result.model_dump(by_alias=True), allow_nan=False)

    def test_distance_can_improve_by_adding_a_trip(self):
        request = make_request()
        single = self.run_solver(request)
        multiple = self.run_solver(request, limit=2)
        self.assert_valid_routes(request, single, 1)
        self.assert_valid_routes(request, multiple, 2)
        self.assertEqual(single.total_cost, 804)
        self.assertEqual(multiple.total_cost, 604)
        self.assertEqual(multiple.solver_details.additional_trips, 1)
        self.assertEqual(multiple.solver_details.objective, "DISTANCE")
        self.assertEqual(multiple.solver_details.distance_weight, 1)
        self.assertEqual(multiple.solver_details.time_cost, 0)
        self.assertEqual(multiple.solver_details.departure_cost, 0)
        self.assertFalse(multiple.solver_details.schedule_validated)

    def test_heterogeneous_fleet_reuses_only_a_vehicle_with_enough_capacity(self):
        request = make_request(demands=(6, 6, 2), capacities=(10, 4))
        result = self.run_solver(request, limit=2)
        self.assert_valid_routes(request, result, 2)
        large_trips = [route for route in result.routes if route.vehicle_id == "V0"]
        self.assertEqual(len(large_trips), 2)
        self.assertEqual(result.solver_details.additional_trips, 1)

    def test_infeasible_result_is_json_serializable_and_has_no_partial_routes(self):
        request = make_request(demands=(6, 6, 6))
        result = self.run_solver(request)
        self.assertFalse(result.is_feasible)
        self.assertIsNone(result.total_cost)
        self.assertEqual(result.routes, [])
        self.assertIn("No se encontró", result.solver_details.failure_reason)
        json.dumps(result.model_dump(by_alias=True), allow_nan=False)

    def test_decimal_asymmetric_matrix_and_permuted_indices(self):
        request = make_request(demands=(1,), coordinates=[(1, 0)])
        request.depots[0].matrix_index = 1
        request.customers[0].matrix_index = 0
        request.distance_matrix = [[0, 0.7234], [0.6123, 0]]
        result = self.run_solver(request)
        self.assert_valid_routes(request, result, 1)
        self.assertAlmostEqual(result.total_cost, 1.3357)

    def test_decimal_distances_reach_the_optimizer(self):
        request = make_request(demands=(1,))
        request.distance_matrix = [[0, 0.6], [0.7, 0]]
        observed = {}
        original_solve = Model.solve

        def capture_model(model, **kwargs):
            observed["distances"] = model.data().distance_matrix(profile=0).copy()
            return original_solve(model, **kwargs)

        with patch.object(Model, "solve", new=capture_model):
            result = self.run_solver(request)
        distances = observed["distances"]
        # No basta con recalcular bien la salida: la búsqueda debe distinguir los costes.
        self.assertGreater(distances[0, 1], 0)
        self.assertLess(distances[0, 1], distances[1, 0])
        self.assertAlmostEqual(result.total_cost, 1.3)

    def test_depot_without_vehicles_does_not_shift_route_depot_ids(self):
        request = make_request(demands=(1,), capacities=(10,))
        empty = request.depots[0].model_copy(update={"id": "EMPTY", "matrix_index": 2})
        request.depots.insert(0, empty)
        request.distance_matrix = [[0, 1, 2], [1, 0, 2], [2, 2, 0]]
        result = self.run_solver(request)
        self.assert_valid_routes(request, result, 1)
        self.assertEqual(result.routes[0].depot_id, "D")

    def test_missing_matrix_uses_cartesian_coordinates(self):
        request = make_request(demands=(1,), coordinates=[(0.3, 0.4)])
        request.distance_matrix = None
        self.assertAlmostEqual(self.run_solver(request).total_cost, 1)

    def test_depots_and_customers_can_have_the_same_id(self):
        request = make_request(demands=(1,))
        request.customers[0].id = "D"
        result = self.run_solver(request)
        self.assert_valid_routes(request, result, 1)
        self.assertEqual(result.routes[0].stops, ["D"])

    def post_request(self, payload):
        body = json.dumps(payload).encode()
        messages = []

        async def receive():
            return {"type": "http.request", "body": body, "more_body": False}

        async def send(message):
            messages.append(message)

        scope = {
            "type": "http", "method": "POST", "path": "/api/v1/HGS-ADC/solve",
            "query_string": b"", "headers": [(b"content-type", b"application/json")],
            "http_version": "1.1", "scheme": "http",
            "server": ("test", 80), "client": ("test", 1234),
        }
        with patch("solvers.pyvrp_v2_solver.MaxRuntime", return_value=MaxIterations(500)):
            asyncio.run(app(scope, receive, send))
        return messages[0]["status"], json.loads(b"".join(m.get("body", b"") for m in messages))

    def test_http_response_preserves_trip_metadata_and_aliases(self):
        request = make_request(demands=(1,), coordinates=[(0.3, 0.4)])
        status, payload = self.post_request(request.model_dump(mode="json", by_alias=True))
        self.assertEqual(status, 200)
        self.assertTrue(payload["isFeasible"])
        self.assertEqual(payload["routes"][0]["tripIndex"], 1)
        self.assertEqual(payload["solverDetails"]["tripCount"], 1)
        self.assertEqual(payload["solverDetails"]["timeCost"], 0)
        self.assertAlmostEqual(payload["totalCost"], 1)

    def test_http_parameters_control_all_solver_options_without_headers(self):
        request = make_request(demands=(6, 6, 2), capacities=(10, 4))
        request.parameters = {
            "classicalTimeLimit": 0.25,
            "seed": 7,
            "maxVehicleRepetition": 2,
            "vehicleStrategy": "load",
            "vehicleStrategyConfig": "balanced",
        }
        with patch("api.main.solve_hgs_adc", wraps=solve_hgs_adc) as engine:
            status, payload = self.post_request(request.model_dump(mode="json", by_alias=True))
        self.assertEqual(status, 200)
        self.assertTrue(payload["isFeasible"])
        self.assertEqual(payload["seed"], 7)
        self.assertEqual(payload["solverDetails"]["maxTripsPerVehicle"], 2)
        self.assertEqual(payload["solverDetails"]["additionalTrips"], 1)
        self.assertEqual(engine.call_args.kwargs["max_runtime_sec"], 0.25)
        self.assertEqual(engine.call_args.kwargs["vehicle_strategy"], VehicleStrategies.LOAD)
        self.assertEqual(engine.call_args.kwargs["vehicle_strategy_config"], HybridPresets.BALANCED)

    def test_http_missing_parameters_use_defaults(self):
        payload = make_request(demands=(1,)).model_dump(mode="json", by_alias=True)
        payload.pop("parameters")
        status, response = self.post_request(payload)
        self.assertEqual(status, 200)
        self.assertEqual(response["seed"], 42)
        self.assertEqual(response["solverDetails"]["maxTripsPerVehicle"], 1)

    def test_http_invalid_parameters_are_rejected_before_solving(self):
        invalid_options = [
            {"maxVehicleRepetition": 0}, {"maxVehicleRepetition": 11},
            {"maxVehicleRepetition": 1.5}, {"maxVehicleRepetition": True},
            {"classicalTimeLimit": 0.01}, {"seed": -1}, {"seed": "42"},
            {"vehicleStrategy": "unknown"}, {"vehicleStrategyConfig": "unknown"},
            {"maxVehicleRepetiton": 2},
        ]
        with patch("api.main.solve_hgs_adc") as engine:
            for options in invalid_options:
                with self.subTest(options=options):
                    request = make_request(demands=(1,))
                    request.parameters = options
                    status, response = self.post_request(request.model_dump(mode="json", by_alias=True))
                    self.assertEqual(status, 422)
                    self.assertEqual(response["detail"][0]["loc"][:2], ["body", "parameters"])
        engine.assert_not_called()

    def test_malformed_matrix_is_rejected_without_fallback(self):
        for matrix in [[], [[0]], [[0, -1], [1, 0]], [[0, math.inf], [1, 0]]]:
            with self.subTest(matrix=matrix):
                request = make_request(demands=(1,))
                request.distance_matrix = matrix
                with self.assertRaises(ValueError):
                    self.run_solver(request)

    def test_unknown_depot_and_duplicate_indices_are_rejected(self):
        request = make_request(demands=(1,))
        request.vehicles[0].start_depot_id = "UNKNOWN"
        with self.assertRaises(ValueError):
            self.run_solver(request)
        request = make_request(demands=(1,))
        request.customers[0].matrix_index = 0
        with self.assertRaises(ValueError):
            self.run_solver(request)

    def test_temporal_constraints_are_rejected_instead_of_ignored(self):
        request = make_request(demands=(1,))
        request.depots[0].max_duration = 10
        with self.assertRaisesRegex(ValueError, "todavía no admite"):
            self.run_solver(request)
        request.depots[0].max_duration = 0
        request.customers[0].service_duration = 1
        with self.assertRaises(ValueError):
            self.run_solver(request)

    def test_endpoint_returns_422_for_unsupported_constraints(self):
        request = make_request(demands=(1,))
        request.customers[0].service_duration = 1
        with self.assertRaises(HTTPException) as error:
            asyncio.run(solve(request))
        self.assertEqual(error.exception.status_code, 422)


if __name__ == "__main__":
    unittest.main()
