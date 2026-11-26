"""La vía cuántica y su fallback miden con la misma matriz que los demás algoritmos.

Las coordenadas de estos casos están puestas a propósito para que la euclídea
diga una cosa y la matriz otra: si un paso midiera con la euclídea, el test falla.
"""

import unittest

from pyvrp.stop import MaxIterations

from api.adapter.adapter import Adapter
from api.adapter.models import RoutingRequestStd
from domain.models import CustomerDto, DepotDto, RoutingRequest, VehicleDto
from domain.spatial_partitioner import SpatialPartitioner
from services.evaluation_service import solve_with_classical_fallback
from services.graph_analyzer import GraphAnalyzer
from solvers.pyvrp_solver import solve_with_pyvrp
from solvers.quantum_qiskit import _build_qiskit_model


def matrix_request(customers, depots, vehicles, matrix, name="matriz"):
    return RoutingRequest(
        instance_name=name, depots=depots, customers=customers,
        vehicles=vehicles, distance_matrix=matrix,
    )


class TestRequestDistance(unittest.TestCase):
    def test_reads_the_matrix_when_there_is_one(self):
        depot = DepotDto(id="D", x=0, y=0, matrix_index=0)
        customer = CustomerDto(id="C", x=3, y=4, demand=1, matrix_index=1)
        request = matrix_request([customer], [depot], [VehicleDto(id="V", capacity=5)],
                                 [[0, 12.5], [13.0, 0]])

        self.assertEqual(request.distance(depot, customer), 12.5)
        self.assertEqual(request.distance(customer, depot), 13.0)

    def test_keeps_the_rounded_euclidean_without_matrix(self):
        depot = DepotDto(id="D", x=0, y=0)
        customer = CustomerDto(id="C", x=3, y=4, demand=1)
        request = RoutingRequest(instance_name="sin-matriz", depots=[depot], customers=[customer],
                                 vehicles=[VehicleDto(id="V", capacity=5)])

        self.assertFalse(request.uses_matrix)
        self.assertEqual(request.distance(depot, customer), 5.0)

    def test_an_empty_matrix_means_no_matrix(self):
        request = matrix_request([CustomerDto(id="C", x=3, y=4, demand=1)], [DepotDto(id="D", x=0, y=0)],
                                 [VehicleDto(id="V", capacity=5)], [])
        self.assertFalse(request.uses_matrix)

    def test_rejects_a_matrix_that_does_not_cover_every_node(self):
        with self.assertRaises(ValueError):
            matrix_request([CustomerDto(id="C", x=1, y=1, demand=1)],
                           [DepotDto(id="D", x=0, y=0, matrix_index=0)],
                           [VehicleDto(id="V", capacity=5)], [[0, 1], [1, 0]])


class TestAdapterKeepsTheMatrix(unittest.TestCase):
    def test_delivera_request_keeps_matrix_and_indexes(self):
        std = RoutingRequestStd.model_validate({
            "problemId": "p",
            "solverType": "QISKIT",
            "depots": [{"id": "D", "lat": 37.38, "lng": -5.98, "matrixIndex": 0}],
            "customers": [{"id": "C", "lat": 37.28, "lng": -5.92, "demand": 1, "matrixIndex": 1}],
            "vehicles": [{"id": "V", "capacity": 5, "startDepotId": "D"}],
            "distanceMatrix": [[0, 12.1], [12.1, 0]],
        })

        request = Adapter.adapt_request(std)

        self.assertTrue(request.uses_matrix)
        self.assertEqual(request.distance(request.depots[0], request.customers[0]), 12.1)


class TestSuperClientsUseTheMatrix(unittest.TestCase):
    """Cuatro clientes en línea: por coordenadas A-B y C-D están juntos, por matriz A-C y B-D."""

    def setUp(self):
        self.depot = DepotDto(id="D", x=0, y=0, matrix_index=0)
        self.customers = [
            CustomerDto(id="A", x=1, y=0, demand=1, matrix_index=1),
            CustomerDto(id="B", x=2, y=0, demand=1, matrix_index=2),
            CustomerDto(id="C", x=50, y=0, demand=1, matrix_index=3),
            CustomerDto(id="E", x=51, y=0, demand=1, matrix_index=4),
        ]
        near, far = 1.0, 100.0
        #         D    A    B    C    E
        self.matrix = [
            [0,   10,  10,  10,  10],   # D
            [10,  0,   far, near, far],  # A
            [10,  far, 0,   far, near],  # B
            [10,  near, far, 0,   far],  # C
            [10,  far, near, far, 0],    # E
        ]
        self.request = matrix_request(self.customers, [self.depot], [VehicleDto(id="V", capacity=10)],
                                      self.matrix)

    def test_groups_follow_the_matrix_and_point_to_their_medoid(self):
        super_clients = SpatialPartitioner(self.request).generate_super_clients(self.customers, 2)

        groups = sorted(sorted(node.id for node in sc.internal_nodes) for sc in super_clients)
        self.assertEqual(groups, [["A", "C"], ["B", "E"]])
        members = {sc.matrix_index for sc in super_clients}
        for sc in super_clients:
            self.assertIn(sc.matrix_index, {node.matrix_index for node in sc.internal_nodes})
        self.assertEqual(len(members), 2)

    def test_depot_assignment_follows_the_matrix(self):
        # Por coordenadas el cliente está al lado de D1; por matriz, de D2.
        d1 = DepotDto(id="D1", x=0, y=0, matrix_index=0)
        d2 = DepotDto(id="D2", x=100, y=0, matrix_index=1)
        customer = CustomerDto(id="C", x=1, y=0, demand=1, matrix_index=2)
        request = matrix_request([customer], [d1, d2],
                                 [VehicleDto(id="V1", capacity=5), VehicleDto(id="V2", capacity=5)],
                                 [[0, 0, 90], [0, 0, 2], [90, 2, 0]])

        regions = SpatialPartitioner(request).apply_capacitated_voronoi()

        self.assertEqual([c.id for c in regions["D2"]], ["C"])

    def test_viability_features_use_the_matrix(self):
        features = GraphAnalyzer.extract_features(self.request)
        # Vecino más cercano 1 y depósito a 10 en la matriz: S = 0,1.
        self.assertAlmostEqual(features["spatial_compressibility_index"], 0.1)


class TestSolversUseTheMatrix(unittest.TestCase):
    def setUp(self):
        self.depot = DepotDto(id="D", x=0, y=0, matrix_index=0)
        self.customers = [CustomerDto(id="C1", x=1, y=0, demand=1, matrix_index=1),
                          CustomerDto(id="C2", x=2, y=0, demand=1, matrix_index=2)]
        # Por coordenadas la ruta mediría 4; por matriz, 7,5 + 2,25 + 8 = 17,75.
        self.matrix = [[0, 7.5, 8], [7.5, 0, 2.25], [8, 2.25, 0]]
        self.request = matrix_request(self.customers, [self.depot], [VehicleDto(id="V", capacity=5)],
                                      self.matrix)

    def test_qubo_costs_come_from_the_matrix(self):
        problem, _ = _build_qiskit_model(self.request, include_mtz=False)
        linear = problem.objective.linear.to_dict(use_name=True)

        self.assertEqual(linear["x_D_C1_V"], 7.5)
        self.assertEqual(linear["x_C1_C2_V"], 2.25)

    def test_classical_reference_reports_matrix_distances(self):
        response = solve_with_pyvrp(self.request, max_runtime_sec=0.2)

        self.assertTrue(response.is_feasible)
        self.assertAlmostEqual(response.total_distance, 17.75, places=2)

    def test_fallback_reports_matrix_distances(self):
        response = solve_with_classical_fallback(self.request, "prueba", max_runtime_sec=0.2)

        self.assertTrue(response.is_feasible)
        self.assertAlmostEqual(response.total_distance, 17.75, places=2)


if __name__ == "__main__":
    unittest.main()
