from api.adapter.models import RoutingRequestStd, RoutingResponseStd, RouteStd
from domain.models import RoutingRequest, RoutingResponse
from api.adapter.mapper import DtoMapper


class Adapter:

    @staticmethod
    def adapt_request(request: RoutingRequestStd) -> RoutingRequest:
        return RoutingRequest(
            instance_name=request.problem_id,
            depots=[
                DtoMapper.to_depot(depot)
                for depot in request.depots
            ],
            customers=[
                DtoMapper.to_customer(customer)
                for customer in request.customers
            ],
            vehicles=[
                DtoMapper.to_vehicle(vehicle)
                for vehicle in request.vehicles
            ],
        )

    @staticmethod
    def adapt_response(
        response: RoutingResponse
    ) -> RoutingResponseStd:

        return RoutingResponseStd(
            problem_id=response.instance_name,
            solver_used=response.solver_used,
            computation_time_ms=response.execution_time_ms,
            total_cost=response.total_distance,
            is_feasible=response.is_feasible,
            seed=42, # TODO: MODEL MUST HAVE THE SEED   
            routes=[
                RouteStd(
                    vehicle_id=route.vehicle_id,
                    depot_id=route.stops[0],
                    stops=route.stops[1:-1],
                    total_distance=route.distance,
                    total_load=route.load,
                )
                for route in response.routes
            ],
        )