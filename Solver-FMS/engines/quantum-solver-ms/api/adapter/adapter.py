from api.adapter.models import RoutingRequestStd, RoutingResponseStd, RouteStd
from domain.models import RoutingRequest, RoutingResponse
from api.adapter.mapper import DtoMapper
from api.adapter.specification import ParamSpecification, ParamFactory, ParamException
from fastapi import HTTPException
import re

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
    
    @staticmethod
    def specification_to_snake(specifications: list[ParamSpecification]) ->  list[ParamSpecification]:
        snake_case_params = []
        for param in specifications:
            snake_param = param.model_copy()
            snake_param.name = Adapter.camel_to_snake(param.name)
            snake_case_params.append(snake_param)
        return snake_case_params
    
    @staticmethod
    def request_params_to_snake(request: RoutingRequestStd) -> dict[str, object]:
        params = {}
        for key, value in request.parameters.items():
            params[Adapter.camel_to_snake(key)] = value
        return params

    @staticmethod
    def adapt_params(
        request: RoutingRequestStd,
        specifications: list[ParamSpecification]
    ) -> dict[str, object]:
        snake_specification = Adapter.specification_to_snake(specifications)
        
        try:
            return ParamFactory.claim_params(
                params=Adapter.request_params_to_snake(request),
                specifications=snake_specification
            )
        except ParamException as ex:
            raise HTTPException(
                status_code=ex.status_code,
                detail=ex.message,
            )
       
        
        
    
    @staticmethod
    def camel_to_snake(value: str) -> str:
        # this ignore first uppercase letter 
        # and find other uppercase letters to put "_" before it
        return re.sub(
            r"(?<!^)(?=[A-Z])",
            "_",
            value
        ).lower()
