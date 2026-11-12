from collections import Counter
import math
from types import ModuleType, SimpleNamespace
import unittest
from unittest.mock import patch

from pyvrp.stop import MaxIterations

from domain.models import CustomerDto, DepotDto, RouteDto, RoutingRequest, RoutingResponse, VehicleDto
from services.evaluation_service import (
    QuantumPreparationError, evaluate_quantum_metrics, solve_best_quantum_candidate,
)
from services.route_expansion_service import expand_quantum_routes
from solvers.pyvrp_path_solver import solve_open_path


def make_group(name, customers):
    return CustomerDto(
        id=name, x=sum(c.x for c in customers) / len(customers), y=0,
        demand=sum(c.demand for c in customers), internal_nodes=customers,
    )


def make_problem():
    request = RoutingRequest(
        instance_name="original-problem",
        depots=[DepotDto(id="D", x=0, y=0)],
        customers=[CustomerDto(id=f"C{i}", x=x, y=0, demand=1)
                   for i, x in enumerate([2, 1, 8, 9, 12], 1)],
        vehicles=[VehicleDto(id="V", capacity=10)],
    )
    subrequest = request.model_copy(update={"customers": [
        make_group("SC_A", request.customers[:2]),
        make_group("SC_B", request.customers[2:4]),
        request.customers[4],
    ]})
    response = RoutingResponse(
        instance_name="backend-label", solver_used="quantum-test",
        execution_time_ms=7, total_distance=999, is_feasible=True, qubits=12,
        routes=[RouteDto(vehicle_id="V", stops=["D", "SC_A", "SC_B", "C5", "D"],
                         distance=999, load=5)],
    )
    return request, subrequest, response


class TestOpenPath(unittest.TestCase):
    def setUp(self):
        # Se ejecuta PyVRP real con iteraciones fijas, sin depender del reloj del equipo.
        self.stop = patch("solvers.pyvrp_path_solver.MaxRuntime", return_value=MaxIterations(400))
        self.stop.start()
        self.addCleanup(self.stop.stop)

    def test_twenty_customers_between_distinct_external_endpoints(self):
        origin = DepotDto(id="A", x=0, y=0)
        destination = DepotDto(id="B", x=21, y=0)
        customers = [CustomerDto(id=f"C{i}", x=i, y=0, demand=1)
                     for i in range(20, 0, -1)]
        ordered = solve_open_path(customers, origin, destination)
        self.assertEqual([c.id for c in ordered], [f"C{i}" for i in range(1, 21)])

    def test_same_depot_at_both_ends_and_singleton(self):
        depot = DepotDto(id="D", x=0, y=0)
        customers = [CustomerDto(id=f"C{i}", x=i, y=0, demand=1) for i in range(1, 4)]
        ordered = solve_open_path(customers, depot, depot)
        self.assertEqual(Counter(c.id for c in ordered), Counter(c.id for c in customers))
        with patch("solvers.pyvrp_path_solver.Model") as model:
            self.assertEqual(solve_open_path(customers[:1], depot, depot), customers[:1])
            self.assertEqual(solve_open_path([], depot, depot), [])
        model.assert_not_called()

    def test_decimal_asymmetric_costs_choose_the_correct_direction(self):
        origin = DepotDto(id="A", x=0, y=0)
        destination = DepotDto(id="B", x=0, y=0)
        customers = [CustomerDto(id=name, x=0, y=0, demand=1) for name in ["C2", "C1"]]
        cheap = {("A", "C1"), ("C1", "C2"), ("C2", "B")}
        def distance(a, b):
            return 0 if a.id == b.id else (0.1 if (a.id, b.id) in cheap else 0.4)
        ordered = solve_open_path(customers, origin, destination, distance=distance)
        self.assertEqual([c.id for c in ordered], ["C1", "C2"])

    def test_invalid_inputs_and_failed_search_are_rejected(self):
        depot = DepotDto(id="D", x=0, y=0)
        customers = [CustomerDto(id=f"C{i}", x=i, y=0, demand=1) for i in range(2)]
        for limit in [0, -1, math.inf, math.nan]:
            with self.subTest(limit=limit), self.assertRaises(ValueError):
                solve_open_path(customers, depot, depot, max_runtime_sec=limit)
        with self.assertRaisesRegex(ValueError, "repetidos"):
            solve_open_path([customers[0], customers[0]], depot, depot)
        for value in [-1, math.inf, math.nan, 10**20]:
            with self.subTest(distance=value), self.assertRaises(ValueError):
                solve_open_path(customers, depot, depot, distance=lambda a, b: value)
        with patch("solvers.pyvrp_path_solver.Model.solve",
                   return_value=SimpleNamespace(is_feasible=lambda: False)):
            with self.assertRaisesRegex(ValueError, "recorrido completo"):
                solve_open_path(customers, depot, depot)


class TestRouteExpansion(unittest.TestCase):
    def setUp(self):
        self.stop = patch("solvers.pyvrp_path_solver.MaxRuntime", return_value=MaxIterations(400))
        self.stop.start()
        self.addCleanup(self.stop.stop)

    def test_consecutive_groups_use_next_centroid_and_previous_original_customer(self):
        request, subrequest, response = make_problem()
        with patch("services.route_expansion_service.solve_open_path", wraps=solve_open_path) as local:
            expanded, details = expand_quantum_routes(request, [subrequest], response)
        self.assertEqual(local.call_count, 2)
        first, second = local.call_args_list
        self.assertEqual(first.args[1].id, "D")
        self.assertEqual(first.args[2].id, "SC_B")
        self.assertEqual(second.args[1].id, expanded.routes[0].stops[2])
        self.assertEqual(second.args[2].id, "C5")
        self.assertEqual(Counter(expanded.routes[0].stops[1:-1]), Counter(c.id for c in request.customers))
        self.assertEqual(expanded.routes[0].stops[0], "D")
        self.assertEqual(expanded.routes[0].stops[-1], "D")
        self.assertEqual(expanded.routes[0].stops[-2], "C5")
        self.assertEqual(expanded.routes[0].load, 5)
        self.assertEqual(expanded.routes[0].vehicle_id, "V")
        self.assertEqual(expanded.total_distance, 24)
        self.assertEqual(details["compressed_cost"], 999)
        self.assertEqual(details["expanded_superclients"], 2)
        self.assertEqual(details["internal_cost"] + details["global_cost"], 24)
        self.assertEqual(expanded.instance_name, request.instance_name)
        self.assertEqual(expanded.solver_used, response.solver_used)
        self.assertEqual(expanded.qubits, 12)
        self.assertGreaterEqual(expanded.execution_time_ms, response.execution_time_ms)
        self.assertEqual(expanded.internal_routes, [])
        # No se modifica la respuesta reducida usada como entrada.
        self.assertEqual(response.routes[0].stops[1], "SC_A")

    def test_last_group_points_to_the_depot(self):
        request, _, response = make_problem()
        subrequest = request.model_copy(update={"customers": [make_group("SC_ALL", request.customers)]})
        response = response.model_copy(update={"routes": [
            RouteDto(vehicle_id="V", stops=["D", "SC_ALL", "D"], distance=1, load=5),
        ]})
        with patch("services.route_expansion_service.solve_open_path", wraps=solve_open_path) as local:
            expanded, _ = expand_quantum_routes(request, [subrequest], response)
        self.assertEqual(local.call_args.args[1].id, "D")
        self.assertEqual(local.call_args.args[2].id, "D")
        self.assertEqual(expanded.total_distance, 24)

    def test_original_id_starting_with_sc_and_depot_customer_id_overlap(self):
        request = RoutingRequest(
            instance_name="ids", depots=[DepotDto(id="1", x=0, y=0)],
            customers=[CustomerDto(id="1", x=1, y=0, demand=1),
                       CustomerDto(id="SC_original_with_underscores", x=2, y=0, demand=1)],
            vehicles=[VehicleDto(id="V", capacity=3)],
        )
        response = RoutingResponse(
            instance_name="ids", solver_used="test", execution_time_ms=0,
            total_distance=4, is_feasible=True, routes=[RouteDto(
                vehicle_id="V", stops=["1", "1", "SC_original_with_underscores", "1"], distance=4, load=2,
            )],
        )
        with patch("services.route_expansion_service.solve_open_path") as local:
            expanded, _ = expand_quantum_routes(request, [request], response)
        local.assert_not_called()
        self.assertEqual(expanded.routes, response.routes)

    def test_multiple_depots_keep_their_vehicles(self):
        request = RoutingRequest(
            instance_name="two-depots", depots=[DepotDto(id="D1", x=0, y=0), DepotDto(id="D2", x=100, y=0)],
            customers=[CustomerDto(id=f"C{i}", x=x, y=0, demand=1)
                       for i, x in enumerate([1, 2, 101, 102])],
            vehicles=[VehicleDto(id="V1", capacity=3), VehicleDto(id="V2", capacity=3)],
        )
        subrequests = [request.model_copy(update={
            "depots": request.depots[i:i+1], "vehicles": request.vehicles[i:i+1],
            "customers": [make_group(f"SC_{i}", request.customers[2*i:2*i+2])],
        }) for i in range(2)]
        response = RoutingResponse(
            instance_name="two-depots", solver_used="test", execution_time_ms=0,
            total_distance=4, is_feasible=True, routes=[RouteDto(
                vehicle_id=f"V{i+1}", stops=[f"D{i+1}", f"SC_{i}", f"D{i+1}"], distance=2, load=2,
            ) for i in [1, 0]],
        )
        expanded, _ = expand_quantum_routes(request, subrequests, response)
        self.assertEqual([(r.vehicle_id, r.stops[0]) for r in expanded.routes], [("V2", "D2"), ("V1", "D1")])
        self.assertEqual(expanded.total_distance, 8)

    def test_final_cost_uses_real_asymmetric_edges_without_proxy_segments(self):
        request, subrequest, response = make_problem()
        def distance(a, b):
            return abs(a.x - b.x) * (0.1 if a.x <= b.x else 0.2)
        expanded, details = expand_quantum_routes(request, [subrequest], response, distance=distance)
        nodes = {c.id: c for c in request.customers}
        nodes.update({d.id: d for d in request.depots})
        actual = math.fsum(distance(nodes[a], nodes[b]) for a, b in
                           zip(expanded.routes[0].stops, expanded.routes[0].stops[1:]))
        self.assertAlmostEqual(expanded.total_distance, actual)
        self.assertAlmostEqual(expanded.total_distance, 3.6)
        metrics = evaluate_quantum_metrics(
            request, expanded, classical_cost=actual, internal_cost=details["internal_cost"],
        )
        self.assertGreater(metrics["internal_cost"], 0)
        self.assertAlmostEqual(metrics["total_cost"], actual)
        self.assertEqual(metrics["relative_degradation"], 0)

    def test_invalid_reduced_routes_are_rejected(self):
        for case in ["missing", "duplicate", "unknown", "vehicle", "load", "capacity", "return", "infeasible"]:
            with self.subTest(case=case):
                request, subrequest, response = make_problem()
                route = response.routes[0]
                if case == "missing":
                    route.stops.remove("SC_B")
                    route.load = 3
                elif case == "duplicate":
                    response.routes.append(route)
                elif case == "unknown":
                    route.stops[1] = "unknown"
                elif case == "vehicle":
                    route.vehicle_id = "OTHER"
                elif case == "load":
                    route.load = 4
                elif case == "capacity":
                    request.vehicles[0].capacity = 4
                elif case == "return":
                    route.stops[-1] = "OTHER"
                else:
                    response.is_feasible = False
                with self.assertRaises(ValueError):
                    expand_quantum_routes(request, [subrequest], response)

    def test_incomplete_or_duplicate_partition_is_rejected(self):
        for case in ["missing", "duplicate", "demand", "unknown"]:
            with self.subTest(case=case):
                request, subrequest, response = make_problem()
                if case == "missing":
                    subrequest.customers.pop()
                elif case == "duplicate":
                    subrequest.customers[0].internal_nodes[1] = request.customers[0]
                elif case == "demand":
                    subrequest.customers[0].demand += 1
                else:
                    subrequest.customers[0].internal_nodes[0] = CustomerDto(id="OTHER", x=0, y=0, demand=1)
                with self.assertRaises(ValueError):
                    expand_quantum_routes(request, [subrequest], response)

    def test_candidate_selection_uses_expanded_cost_for_both_backends(self):
        for backend in ["qiskit", "dwave"]:
            with self.subTest(backend=backend):
                request, _, response = make_problem()
                one_group = request.model_copy(update={"customers": [make_group("SC_ALL", request.customers)]})
                mixed = request.model_copy(update={"customers": [
                    make_group("SC_A", [request.customers[0], request.customers[3]]),
                    make_group("SC_B", [request.customers[1], request.customers[2]]), request.customers[4],
                ]})
                first = response.model_copy(update={"total_distance": 100, "routes": [
                    RouteDto(vehicle_id="V", stops=["D", "SC_ALL", "D"], distance=100, load=5),
                ]})
                second = response.model_copy(update={"total_distance": 1})
                module = ModuleType(f"solvers.quantum_{backend}")
                method = "solve_with_dwave" if backend == "dwave" else "solve_with_qiskit_with_timeout"
                with patch.object(module, method, create=True, side_effect=[first, second]) as solver, patch.dict(
                    "sys.modules", {f"solvers.quantum_{backend}": module},
                ), patch("services.evaluation_service.prepare_quantum_subrequests", side_effect=[
                    ([one_group], {"k_offset": 0}), ([mixed], {"k_offset": 1}),
                ]):
                    expanded, details = solve_best_quantum_candidate(request, backend=backend, max_k_offset=1)
                self.assertEqual(solver.call_count, 2)
                self.assertEqual(details["k_offset"], 0)
                self.assertEqual(details["compressed_cost"], 100)
                self.assertEqual(expanded.total_distance, 24)

    def test_unexpandable_candidate_raises_preparation_error_for_fallback(self):
        request, subrequest, response = make_problem()
        response.routes[0].stops.remove("SC_B")
        module = ModuleType("solvers.quantum_qiskit")
        with patch.object(module, "solve_with_qiskit_with_timeout", create=True, return_value=response), patch.dict(
            "sys.modules", {"solvers.quantum_qiskit": module},
        ), patch("services.evaluation_service.prepare_quantum_subrequests", return_value=([subrequest], {})):
            with self.assertRaises(QuantumPreparationError):
                solve_best_quantum_candidate(request, max_k_offset=0)

    def test_invalid_candidate_is_skipped_before_a_valid_one(self):
        request, subrequest, response = make_problem()
        one_group = request.model_copy(update={"customers": [make_group("SC_ALL", request.customers)]})
        invalid = response.model_copy(update={"is_feasible": False})
        module = ModuleType("solvers.quantum_qiskit")
        with patch.object(module, "solve_with_qiskit_with_timeout", create=True,
                          side_effect=[invalid, response]), patch.dict(
            "sys.modules", {"solvers.quantum_qiskit": module},
        ), patch("services.evaluation_service.prepare_quantum_subrequests", side_effect=[
            ([one_group], {"k_offset": 0}), ([subrequest], {"k_offset": 1}),
        ]):
            expanded, details = solve_best_quantum_candidate(request, max_k_offset=1)
        self.assertTrue(expanded.is_feasible)
        self.assertEqual(details["k_offset"], 1)
        self.assertEqual(len(details["rejected_candidates"]), 1)
        self.assertEqual(details["rejected_candidates"][0]["k_offset"], 0)

    def test_real_preparation_and_local_search_with_stubbed_quantum_backend(self):
        request = RoutingRequest(
            instance_name="twenty-original-customers", depots=[DepotDto(id="D", x=0, y=0)],
            customers=[CustomerDto(id=f"C{i}", x=i, y=0, demand=1) for i in range(1, 21)],
            vehicles=[VehicleDto(id="V", capacity=40)],
        )
        def quantum_result(subrequests, **options):
            # Solo se simula el backend cuántico. GAP, clustering y PyVRP son reales.
            self.assertEqual(len(subrequests), 1)
            group = subrequests[0].customers[0]
            self.assertEqual(len(group.internal_nodes), 20)
            return RoutingResponse(
                instance_name="backend-label", solver_used="stubbed-quantum", execution_time_ms=0,
                total_distance=20, is_feasible=True, routes=[RouteDto(
                    vehicle_id="V", stops=["D", group.id, "D"], distance=20, load=20,
                )],
            )
        module = ModuleType("solvers.quantum_qiskit")
        with patch.object(module, "solve_with_qiskit_with_timeout", create=True,
                          side_effect=quantum_result), patch.dict(
            "sys.modules", {"solvers.quantum_qiskit": module},
        ):
            expanded, details = solve_best_quantum_candidate(request, max_k_offset=0)
        self.assertEqual(Counter(expanded.routes[0].stops[1:-1]), Counter(c.id for c in request.customers))
        self.assertEqual(expanded.total_distance, 40)
        self.assertEqual(details["expanded_superclients"], 1)


if __name__ == "__main__":
    unittest.main()
