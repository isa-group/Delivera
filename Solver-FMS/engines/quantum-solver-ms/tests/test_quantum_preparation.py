import unittest

from domain.models import CustomerDto, DepotDto, RoutingRequest, VehicleDto
from domain.spatial_partitioner import SpatialPartitioner
from services.evaluation_service import (
    calculate_cluster_k,
    prepare_quantum_subrequests,
    validate_superclients,
)


class TestQuantumPreparation(unittest.TestCase):
    def setUp(self):
        self.depots = [
            DepotDto(id="D1", x=0, y=0),
            DepotDto(id="D2", x=100, y=100),
        ]
        self.vehicles = [
            VehicleDto(id="V1", capacity=10),
            VehicleDto(id="V2", capacity=10),
            VehicleDto(id="V3", capacity=10),
            VehicleDto(id="V4", capacity=10),
        ]

    def test_vehicles_are_kept_with_their_depot(self):
        request = RoutingRequest(
            instance_name="mapping",
            depots=self.depots,
            customers=[],
            vehicles=self.vehicles,
        )

        groups = SpatialPartitioner(request).get_vehicles_by_depot()

        self.assertEqual([vehicle.id for vehicle in groups["D1"]], ["V1", "V2"])
        self.assertEqual([vehicle.id for vehicle in groups["D2"]], ["V3", "V4"])

    def test_cluster_k_uses_total_demand_and_local_capacity(self):
        request = RoutingRequest(
            instance_name="cluster",
            depots=[self.depots[0]],
            customers=[
                CustomerDto(id="1", x=1, y=0, demand=9),
                CustomerDto(id="2", x=2, y=0, demand=9),
            ],
            vehicles=self.vehicles[:2],
        )

        self.assertEqual(calculate_cluster_k(request), 2)

    def test_superclient_capacity_is_validated_after_clustering(self):
        request = RoutingRequest(
            instance_name="invalid-superclient",
            depots=[self.depots[0]],
            customers=[CustomerDto(id="SC_1_2", x=1, y=0, demand=11)],
            vehicles=self.vehicles[:2],
        )

        with self.assertRaises(ValueError):
            validate_superclients(request)

    def test_post_gap_calculates_k_per_region(self):
        # El GAP asigna cada cliente al deposito y la preparacion calcula un
        # K minimo independiente para cada region, usando su propia flota.
        request = RoutingRequest(
            instance_name="regional-k",
            depots=self.depots,
            customers=[
                CustomerDto(id="1", x=100, y=100, demand=9),
                CustomerDto(id="2", x=101, y=100, demand=9),
                CustomerDto(id="3", x=0, y=1, demand=2),
            ],
            vehicles=self.vehicles,
        )

        subrequests, metrics = prepare_quantum_subrequests(request)
        regions = {item["depot_id"]: item for item in metrics["regions"]}

        # K se conserva como estimacion minima; el nombre de la subrequest
        # refleja el numero solicitado para esa ejecucion concreta.
        self.assertEqual(regions["D1"]["superclients_requested"], 1)
        self.assertEqual(regions["D2"]["superclients_requested"], 2)
        self.assertEqual(metrics["min_superclients_mean"], 1.5)
        self.assertEqual(metrics["min_superclients_max"], 2)
        self.assertEqual(
            [subrequest.instance_name for subrequest in subrequests],
            ["regional-k_Sub_D1_K1", "regional-k_Sub_D2_K2"],
        )
        self.assertEqual(
            {subrequest.depots[0].id: [vehicle.id for vehicle in subrequest.vehicles]
             for subrequest in subrequests},
            {"D1": ["V1", "V2"], "D2": ["V3", "V4"]},
        )


if __name__ == "__main__":
    unittest.main()
