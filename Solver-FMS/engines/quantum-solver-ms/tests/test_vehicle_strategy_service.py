import unittest

from services.vehicle_strategy_service import VehicleStrategy,RobinStrategy
from services.vehicle_strategy_service import DistanceStrategy, VehicleAssignmentContext
from services.vehicle_strategy_service import LoadStrategy, FutureHybridStrategy
from services.vehicle_strategy_service import HybridPresets, HybridStrategy, HybridBase
from services.vehicle_strategy_service import VehicleStrategies

class TestVehicleStrategyService(unittest.TestCase):

    def test_every_strategy_enforces_the_physical_trip_limit(self):
        for strategy in VehicleStrategies:
            with self.subTest(strategy=strategy):
                ctx = VehicleAssignmentContext(
                    vehicle_ids=["V1", "V2"], vehicle_position=0,
                    route_distance=0, route_load=0,
                    distance_by_vehicle={}, load_by_vehicle={},
                    distance_weight=0.5, load_weight=0.5,
                    max_trips_per_vehicle=2,
                )
                assigned = []
                # Sin filtrar los agotados, DISTANCE daría V1,V2,V1,V1.
                for distance in [1, 100, 1, 1]:
                    ctx.route_distance = distance
                    ctx.route_load = distance
                    assigned.append(VehicleStrategy.decide(ctx, strategy))
                self.assertEqual(assigned[:2], ["V1", "V2"])
                self.assertEqual(ctx.trip_count_by_vehicle, {"V1": 2, "V2": 2})
                self.assertEqual(ctx.vehicle_position, 4)
                with self.assertRaisesRegex(ValueError, "No quedan vehículos"):
                    VehicleStrategy.decide(ctx, strategy)
                self.assertEqual(ctx.vehicle_position, 4)

    def test_round_robin_skips_exhausted_vehicles(self):
        ctx = VehicleAssignmentContext(
            vehicle_ids=["V1", "V2", "V3"], vehicle_position=3,
            route_distance=1, route_load=1,
            distance_by_vehicle={}, load_by_vehicle={},
            distance_weight=0.5, load_weight=0.5,
            max_trips_per_vehicle=2,
            trip_count_by_vehicle={"V1": 2, "V2": 1, "V3": 1},
        )
        self.assertEqual(VehicleStrategy.decide(ctx, VehicleStrategies.ROBIN_ROUND), "V2")

    def test_round_robin_preserves_full_order_when_candidates_are_filtered(self):
        ctx = VehicleAssignmentContext(
            vehicle_ids=["V1", "V2", "V3"], vehicle_position=4,
            route_distance=0, route_load=0,
            distance_by_vehicle={}, load_by_vehicle={},
            distance_weight=0.5, load_weight=0.5,
        )
        # El turno empieza en V2. Al estar excluido, corresponde V3, no V1.
        self.assertEqual(RobinStrategy.next(ctx, candidates=["V1", "V3"]), "V3")

    def test_every_strategy_uses_only_the_supplied_candidates(self):
        ctx = VehicleAssignmentContext(
            vehicle_ids=["V1", "V2", "V3"], vehicle_position=0,
            route_distance=10, route_load=5,
            distance_by_vehicle={"V1": 0, "V2": 100, "V3": 10},
            load_by_vehicle={"V1": 0, "V2": 100, "V3": 10},
            distance_weight=0.5, load_weight=0.5,
        )
        for name, strategy in VehicleStrategy.STRATEGIES.items():
            with self.subTest(strategy=name):
                self.assertEqual(strategy.next(ctx, candidates=["V2"]), "V2")

    def test_empty_candidates_are_not_replaced_by_the_full_fleet(self):
        ctx = VehicleAssignmentContext(
            vehicle_ids=["V1"], vehicle_position=0,
            route_distance=0, route_load=0,
            distance_by_vehicle={}, load_by_vehicle={},
            distance_weight=0.5, load_weight=0.5,
        )
        for name, strategy in VehicleStrategy.STRATEGIES.items():
            with self.subTest(strategy=name):
                with self.assertRaisesRegex(ValueError, "No hay vehículos candidatos"):
                    strategy.next(ctx, candidates=[])

    def test_direct_strategy_calls_report_exhausted_vehicles(self):
        ctx = VehicleAssignmentContext(
            vehicle_ids=["V1", "V2"], vehicle_position=0,
            route_distance=0, route_load=0,
            distance_by_vehicle={}, load_by_vehicle={},
            distance_weight=0.5, load_weight=0.5,
            max_trips_per_vehicle=1,
            trip_count_by_vehicle={"V1": 1, "V2": 1},
        )
        for name, strategy in VehicleStrategy.STRATEGIES.items():
            for candidates in [None, ["V1", "V2"]]:
                with self.subTest(strategy=name, candidates=candidates):
                    with self.assertRaisesRegex(ValueError, "No quedan vehículos con viajes disponibles"):
                        strategy.next(ctx, candidates=candidates)

    def test_foreign_candidates_are_reported_as_invalid(self):
        ctx = VehicleAssignmentContext(
            vehicle_ids=["V1"], vehicle_position=0,
            route_distance=0, route_load=0,
            distance_by_vehicle={}, load_by_vehicle={},
            distance_weight=0.5, load_weight=0.5,
        )
        for name, strategy in VehicleStrategy.STRATEGIES.items():
            with self.subTest(strategy=name):
                with self.assertRaisesRegex(ValueError, "no pertenecen a esta flota"):
                    strategy.next(ctx, candidates=["V1", "OTHER"])

    def test_hybrid_extrema_are_computed_only_from_candidates(self):
        ctx = VehicleAssignmentContext(
            vehicle_ids=["EXCLUDED", "V1", "V2"], vehicle_position=0,
            route_distance=5, route_load=2,
            distance_by_vehicle={"EXCLUDED": 10000, "V1": 10, "V2": 20},
            load_by_vehicle={"EXCLUDED": 10000, "V1": 100, "V2": 0},
            distance_weight=0.6, load_weight=0.4,
        )
        for strategy in [HybridStrategy, FutureHybridStrategy]:
            with self.subTest(strategy=strategy.__name__):
                # V1 puntúa 0.4 y V2 0.6. Incluir el excluido cambiaría la elección.
                self.assertEqual(strategy.next(ctx, candidates=["V1", "V2"]), "V1")

    def test_future_hybrid_matches_hybrid_when_the_same_route_is_added(self):
        ctx = VehicleAssignmentContext(
            vehicle_ids=["V1", "V2", "V3"], vehicle_position=0,
            route_distance=0, route_load=0,
            distance_by_vehicle={"V1": 20, "V2": 100, "V3": 60},
            load_by_vehicle={"V1": 100, "V2": 20, "V3": 50},
            distance_weight=0.6, load_weight=0.4,
        )
        for distance, load in [(0, 0), (10, 15), (1000000, 1000)]:
            ctx.route_distance, ctx.route_load = distance, load
            for candidates in [["V1", "V2", "V3"], ["V2", "V3"]]:
                with self.subTest(distance=distance, load=load, candidates=candidates):
                    self.assertEqual(
                        FutureHybridStrategy.next(ctx, candidates=candidates),
                        HybridStrategy.next(ctx, candidates=candidates),
                    )

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
