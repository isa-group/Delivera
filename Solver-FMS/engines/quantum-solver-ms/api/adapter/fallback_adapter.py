"""Puente entre el contrato cuántico nativo y el PyVRP de Delivera."""

from api.adapter.models import (
    CustomerStd, DepotStd, PyvrpParameters, RoutingRequestStd, RoutingResponseStd, VehicleStd,
)
from domain.models import RouteDto, RoutingRequest, RoutingResponse
from domain.spatial_partitioner import SpatialPartitioner


def fallback_parameters(request: RoutingRequest, max_runtime_sec: float = 60) -> PyvrpParameters:
    """Las opciones del fallback no configuran QAOA ni la referencia clásica."""
    unknown = set(request.parameters) - {"fallback"} # diferencia conjuntista
    if unknown:
        raise ValueError(f"Parámetros cuánticos desconocidos: {', '.join(sorted(unknown))}.")
    values = request.parameters.get("fallback", {})
    if not isinstance(values, dict):
        raise ValueError("parameters.fallback debe ser un objeto JSON.")
    # El tiempo heredado de la cabecera se puede sustituir solo para el fallback.
    return PyvrpParameters.model_validate({"classicalTimeLimit": max_runtime_sec, **values})


class PyvrpFallbackAdapter:
    @staticmethod
    def to_request(request: RoutingRequest) -> RoutingRequestStd:
        if any(customer.internal_nodes for customer in request.customers):
            raise ValueError("El fallback debe recibir los clientes originales, sin agrupar.")
        fleets = SpatialPartitioner(request).get_vehicles_by_depot()
        nodes = [*request.depots, *request.customers]
        return RoutingRequestStd(
            problem_id=request.instance_name,
            solver_type="HGS-ADC",
            depots=[DepotStd(id=d.id, lng=d.x, lat=d.y, matrix_index=i)
                    for i, d in enumerate(request.depots)],
            customers=[CustomerStd(id=c.id, lng=c.x, lat=c.y, demand=c.demand,
                                   matrix_index=len(request.depots) + i)
                       for i, c in enumerate(request.customers)],
            vehicles=[VehicleStd(id=v.id, capacity=v.capacity, start_depot_id=depot.id)
                      for depot in request.depots for v in fleets[depot.id]],
            # Se conserva la métrica nativa, incluida su regla de redondeo.
            # Dejar que PyVRP2 calcule su euclídea decimal cambiaría el objetivo.
            distance_matrix=[[a.euclidean_distance(b) for b in nodes] for a in nodes],
        )

    @staticmethod
    def to_response(response: RoutingResponseStd, reason: str) -> RoutingResponse:
        return RoutingResponse(
            instance_name=response.problem_id,
            solver_used=f"{response.solver_used} classical fallback",
            execution_time_ms=response.computation_time_ms,
            total_distance=response.total_cost,
            is_feasible=response.is_feasible,
            routes=[RouteDto(
                vehicle_id=r.vehicle_id, stops=[r.depot_id, *r.stops, r.depot_id],
                distance=r.total_distance, load=r.total_load, trip_index=r.trip_index,
            ) for r in response.routes],
            fallback_reason=reason,
            seed=response.seed,
            solver_details=response.solver_details.model_dump(mode="json", by_alias=True)
            if response.solver_details else None,
        )
