import unittest
from unittest.mock import MagicMock, patch

from fastapi import HTTPException
from api.adapter.adapter import Adapter
from services.vehicle_strategy_service import VehicleStrategies
from api.adapter.specification import ParamException, ParamSpecification, ParamTypes
from api.adapter.models import (
    RoutingRequestStd,
    DepotStd,
    CustomerStd,
    VehicleStd,
)

from domain.models import (
    RoutingResponse,
    RouteDto,
)

class TestAdapter(unittest.TestCase):

    def test_adapt_request_maps_all_fields(self):

        request = RoutingRequestStd(
            problem_id="test-problem",
            depots=[
                DepotStd(
                    id="D1",
                    lng=20,
                    lat=10,
                    matrix_index=0,
                    max_duration=0,
                )
            ],
            customers=[
                CustomerStd(
                    id="C1",
                    lng=40,
                    lat=30,
                    demand=5,
                    matrix_index=1,
                    service_duration=0,
                )
            ],
            vehicles=[
                VehicleStd(
                    id="V1",
                    capacity=100,
                    start_depot_id="D1",
                )
            ],
            distance_matrix=[
                [0, 10],
                [10, 0],
            ],
            solver_type="PYVRP",
            parameters={},
        )

        result = Adapter.adapt_request(request)

        self.assertEqual(result.instance_name, "test-problem")

        self.assertEqual(result.depots[0].id, "D1")
        self.assertEqual(result.depots[0].x, 20)
        self.assertEqual(result.depots[0].y, 10)

        self.assertEqual(result.customers[0].id, "C1")
        self.assertEqual(result.customers[0].x, 40)
        self.assertEqual(result.customers[0].y, 30)
        self.assertEqual(result.customers[0].demand, 5)

        self.assertEqual(result.vehicles[0].id, "V1")
        self.assertEqual(result.vehicles[0].capacity, 100)
        
    def test_adapt_response_maps_routes_correctly(self):

        response = RoutingResponse(
            instance_name="problem-1",
            solver_used="PyVRP",
            execution_time_ms=100,
            total_distance=250,
            is_feasible=True,
            routes=[
                RouteDto(
                    vehicle_id="V1",
                    stops=["D1", "C1", "C2", "D1"],
                    distance=250,
                    load=80,
                )
            ],
        )

        result = Adapter.adapt_response(
            response
        )

        self.assertEqual(result.problem_id, "problem-1")
        self.assertEqual(result.solver_used, "PyVRP")
        self.assertEqual(result.computation_time_ms, 100)
        self.assertEqual(result.total_cost, 250)

        route = result.routes[0]

        self.assertEqual(route.vehicle_id, "V1")
        self.assertEqual(route.depot_id, "D1")
        self.assertEqual(route.stops, ["C1", "C2"])
        self.assertEqual(route.total_distance, 250)
        self.assertEqual(route.total_load, 80)

    def test_adapt_response_removes_depot_from_stops(self):

        response = RoutingResponse(
            instance_name="problem-1",
            solver_used="PyVRP",
            execution_time_ms=100,
            total_distance=250,
            is_feasible=True,
            routes=[
                RouteDto(
                    vehicle_id="V1",
                    stops=["DEPOT", "C1", "C2", "C3", "DEPOT"],
                    distance=250,
                    load=80,
                )
            ],
        )

        result = Adapter.adapt_response(response)

        self.assertEqual(
            result.routes[0].stops,
            ["C1", "C2", "C3"]
        )

        self.assertNotIn(
            "DEPOT",
            result.routes[0].stops
        )

        self.assertEqual(
            result.routes[0].depot_id,
            "DEPOT"
        )

    def test_camel_to_snake(self):
        self.assertEqual(
            Adapter.camel_to_snake(
                "vehicleReuseStrategy"
            ),
            "vehicle_reuse_strategy"
        )
    
    def test_camel_to_snake_single_word(self):
        self.assertEqual(
            Adapter.camel_to_snake(
                "distance"
            ),
            "distance"
        )
    
    def test_camel_to_snake_multiple_words(self):
        self.assertEqual(
            Adapter.camel_to_snake(
                "futureHybridStrategy"
            ),
            "future_hybrid_strategy"
        )
    
    def test_specification_to_snake(self):

        specifications = [
            ParamSpecification(
                name="vehicleReuseStrategy",
                type=ParamTypes.STRING
            )
        ]

        snake = Adapter.specification_to_snake(
            specifications
        )

        self.assertEqual(
            snake[0].name,
            "vehicle_reuse_strategy"
        )

        self.assertEqual(
            specifications[0].name,
            "vehicleReuseStrategy"
        )
    
    def test_adapt_params(self):
        request = RoutingRequestStd(
            problem_id="test-problem",
            depots=[
                DepotStd(
                    id="D1",
                    lng=20,
                    lat=10,
                    matrix_index=0,
                    max_duration=0,
                )
            ],
            customers=[
                CustomerStd(
                    id="C1",
                    lng=40,
                    lat=30,
                    demand=5,
                    matrix_index=1,
                    service_duration=0,
                )
            ],
            vehicles=[
                VehicleStd(
                    id="V1",
                    capacity=100,
                    start_depot_id="D1",
                )
            ],
            distance_matrix=[
                [0, 10],
                [10, 0],
            ],
            solver_type="PYVRP",
            parameters={
                "vehicleReuseStrategy": "distance"
            }
        )

        specifications = [
            ParamSpecification(
                name="vehicleReuseStrategy",
                type=ParamTypes.ENUM,
                enum_type=VehicleStrategies
            )
        ]

        result = Adapter.adapt_params(
            request=request,
            specifications=specifications
        )

        self.assertEqual(
            result["vehicle_reuse_strategy"],
            VehicleStrategies.DISTANCE
        )
    
    def test_adapt_params_invalid_value(self):

        request = RoutingRequestStd(
            problem_id="test-problem",
            depots=[
                DepotStd(
                    id="D1",
                    lng=20,
                    lat=10,
                    matrix_index=0,
                    max_duration=0,
                )
            ],
            customers=[
                CustomerStd(
                    id="C1",
                    lng=40,
                    lat=30,
                    demand=5,
                    matrix_index=1,
                    service_duration=0,
                )
            ],
            vehicles=[
                VehicleStd(
                    id="V1",
                    capacity=100,
                    start_depot_id="D1",
                )
            ],
            distance_matrix=[
                [0, 10],
                [10, 0],
            ],
            solver_type="PYVRP",
            parameters={
                "vehicleReuseStrategy": "pepito"
            }
        )

        specifications = [
            ParamSpecification(
                name="vehicleReuseStrategy",
                type=ParamTypes.ENUM,
                enum_type=VehicleStrategies
            )
        ]

        with self.assertRaises(HTTPException) as ctx:
            Adapter.adapt_params(
                request=request,
                specifications=specifications
            )

        self.assertEqual(
            ctx.exception.status_code,
            422
        )

        
    #Exception conversion test with mock
    @patch(
        "api.adapter.specification.ParamFactory.claim_params"
    )
    def test_adapt_params_converts_param_exception(
        self,
        mock_claim_params
    ):

        mock_claim_params.side_effect = ParamException(
            "boom",
            status_code=422
        )

        with self.assertRaises(HTTPException) as ctx:
            Adapter.adapt_params(
                request=MagicMock(),
                specifications=[]
            )

        self.assertEqual(
            ctx.exception.status_code,
            422
        )

        self.assertEqual(
            ctx.exception.detail,
            "boom"
        )

if __name__ == "__main__":
    unittest.main()