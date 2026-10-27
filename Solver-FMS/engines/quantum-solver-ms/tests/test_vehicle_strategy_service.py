import unittest

from services.vehicle_strategy_service import VehicleStrategy,RobinStrategy
from services.vehicle_strategy_service import DistanceStrategy, VehicleAssignmentContext
from services.vehicle_strategy_service import LoadStrategy, FutureHybridStrategy
from services.vehicle_strategy_service import HybridPresets, HybridStrategy, HybridBase

class TestVehicleStrategyService(unittest.TestCase):

    def test_get_weights_urban(self):
        wd, wl = VehicleStrategy.get_weights(
            HybridPresets.URBAN
        )
        _wd, _wl = VehicleStrategy.PRESETS[HybridPresets.URBAN]

        self.assertEqual(wd,_wd)
        self.assertEqual(wl,_wl)



    def test_get_weights_balanced_by_default(self):
        wd, wl = VehicleStrategy.get_weights()

        _wd, _wl = VehicleStrategy.PRESETS[HybridPresets.BALANCED]

        self.assertEqual(wd,_wd)
        self.assertEqual(wl,_wl)

    def test_round_robin_reuses_vehicle_by_position(self):
        ctx = VehicleAssignmentContext(
            vehicle_ids=["V1", "V2"],
            vehicle_position=3,
            route_distance=0,
            distance_by_vehicle={},
            distance_weight=0.5,
            route_load=0,
            load_by_vehicle={},
            load_weight=0.5,
        )
        next_vehicle = RobinStrategy.next(ctx)

        self.assertEqual(next_vehicle, "V2")

    def test_distance_strategy_selects_min_distance(self):
        ctx = VehicleAssignmentContext(
            vehicle_ids=["V1", "V2"],
            vehicle_position=0,
            route_distance=0,
            distance_by_vehicle={
                "V1": 100,
                "V2": 50,
            },
            distance_weight=0.5,
            route_load=0,
            load_by_vehicle={},
            load_weight=0.5,
        )
        next_vehicle = DistanceStrategy.next(ctx)

        self.assertEqual(next_vehicle, "V2")


    def test_load_strategy_selects_min_load(self):
        ctx = VehicleAssignmentContext(
            vehicle_ids=["V1", "V2"],
            vehicle_position=0,
            route_distance=0,
            distance_by_vehicle={},
            distance_weight=0.5,
            route_load=0,
            load_by_vehicle={
                "V1": 70,
                "V2": 20,
            },
            load_weight=0.5,
        )

        next_vehicle = LoadStrategy.next(ctx)

        self.assertEqual(next_vehicle, "V2")

    def test_future_hybrid_prioritizes_distance(self):
        ctx = VehicleAssignmentContext(
            vehicle_ids=["V1", "V2"],
            vehicle_position=2,
            route_distance=10,
            route_load=10,
            distance_by_vehicle={
                "V1": 20,
                "V2": 100,
            },
            load_by_vehicle={
                "V1": 100,
                "V2": 20,
            },
            distance_weight=0.9,
            load_weight=0.1,
            max_distance=100,
            max_load=100,
        )

        next_vehicle = FutureHybridStrategy.next(ctx)

        self.assertEqual(next_vehicle, "V1")
    
    def test_hybrid_prioritizes_distance(self):
        ctx = VehicleAssignmentContext(
            vehicle_ids=["V1", "V2"],
            vehicle_position=2,
            route_distance=10,
            route_load=10,
            distance_by_vehicle={
                "V1": 20,
                "V2": 100,
            },
            load_by_vehicle={
                "V1": 100,
                "V2": 20,
            },
            distance_weight=0.9,
            load_weight=0.1,
            max_distance=100,
            max_load=100,
        )

        next_vehicle = HybridStrategy.next(ctx)

        self.assertEqual(next_vehicle, "V1")
    

    def test_normalize_context(self):
        ctx = VehicleAssignmentContext(
            vehicle_ids=[],
            vehicle_position=0,
            route_distance=0,
            distance_by_vehicle={},
            distance_weight=0.5,
            route_load=0,
            load_by_vehicle={},
            load_weight=0.5,
        )

        wd, distance, wl, load = HybridBase.normalize_context(
            ctx=ctx,
            distance=50,
            load=10,
            min_distance=0,
            max_distance=100,
            min_load=0,
            max_load=20,
        )

        # (50 - 0) / (100 - 0) = 0.5
        # (10 - 0) / (20 - 0) = 0.5

        self.assertEqual(wd, 0.5)
        self.assertEqual(wl, 0.5)
        self.assertEqual(distance, 0.5)
        self.assertEqual(load, 0.5)

    def test_normalize_context_uses_default_weights(self):
        ctx = VehicleAssignmentContext(
            vehicle_ids=[],
            vehicle_position=0,
            route_distance=0,
            distance_by_vehicle={},
            distance_weight=0,
            route_load=0,
            load_by_vehicle={},
            load_weight=0,
        )

        wd, _, wl, _ = HybridBase.normalize_context(
            ctx=ctx,
            distance=50,
            load=10,
            min_distance=0,
            max_distance=100,
            min_load=0,
            max_load=20,
        )

        _wd = HybridBase.default_distance_weight()
        _wl = HybridBase.default_load_weight()

        self.assertEqual(wd, _wd)
        self.assertEqual(wl, _wl)

    def test_normalize_context_same_values(self):
        ctx = VehicleAssignmentContext(
            vehicle_ids=[],
            vehicle_position=0,
            route_distance=0,
            distance_by_vehicle={},
            distance_weight=0.5,
            route_load=0,
            load_by_vehicle={},
            load_weight=0.5,
        )

        wd, distance, wl, load = HybridBase.normalize_context(
            ctx=ctx,
            distance=10,
            load=20,
            min_distance=10,
            max_distance=10,
            min_load=20,
            max_load=20,
        )

        # max_distance == min_distance

        self.assertEqual(distance, 0)
        self.assertEqual(load, 0)

    def test_calculate_goal(self):
        ctx = VehicleAssignmentContext(
            vehicle_ids=[],
            vehicle_position=0,
            route_distance=0,
            distance_by_vehicle={},
            distance_weight=0.7,
            route_load=0,
            load_by_vehicle={},
            load_weight=0.3,
        )

        score = HybridBase.calculate_goal(
            ctx=ctx,
            distance=70,
            load=15,
            min_distance=0,
            max_distance=100,
            min_load=0,
            max_load=30,
        )

        self.assertAlmostEqual(score, 0.64)

if __name__ == "__main__":
    unittest.main()