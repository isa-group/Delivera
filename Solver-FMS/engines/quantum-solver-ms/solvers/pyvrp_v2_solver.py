"""PyVRP heterogéneo con viajes limitados por vehículo y objetivo de distancia."""

from collections import Counter, defaultdict
import math
import time

from pyvrp import Model, PenaltyParams, SolveParams
from pyvrp.constants import MAX_VALUE
from pyvrp.stop import MaxRuntime
from api.adapter.specification import OpenSpecificationFactory, ParamSpecification, ParamTypes, OpenMetada
from api.adapter.models import (
    RouteStd,
    RoutingRequestStd,
    RoutingResponseStd,
    SolverDetailsStd,
)
from services.vehicle_strategy_service import (
    HybridPresets,
    VehicleAssignmentContext,
    VehicleStrategies,
    VehicleStrategy,
)

SOLVER_TYPE = "HGS-ADC"
# El factor conserva tres decimales de la unidad recibida, sin suponer kilómetros.
# Se aplica también a la matriz recibida en distanceMatrix, no solo a la calculada.
DISTANCE_SCALE = 1000


def _validate_request(request: RoutingRequestStd) -> None:
    if not request.depots or not request.vehicles:
        raise ValueError("La instancia debe incluir depósitos y una flota explícita.")
    nodes = request.depots + request.customers
    if len({depot.id for depot in request.depots}) != len(request.depots) or len(
        {customer.id for customer in request.customers}
    ) != len(request.customers):
        raise ValueError("Los IDs deben ser únicos dentro de depósitos y dentro de clientes.")
    if {node.matrix_index for node in nodes} != set(range(len(nodes))):
        raise ValueError("matrixIndex debe identificar cada fila una sola vez, desde 0 hasta N-1.")
    if len({vehicle.id for vehicle in request.vehicles}) != len(request.vehicles):
        raise ValueError("Los IDs de los vehículos deben ser únicos.")
    depot_ids = {depot.id for depot in request.depots}
    for vehicle in request.vehicles:
        if vehicle.start_depot_id not in depot_ids or vehicle.capacity <= 0:
            raise ValueError("Cada vehículo necesita un depósito existente y capacidad positiva.")
    if any(customer.demand <= 0 for customer in request.customers):
        raise ValueError("La demanda de cada cliente debe ser positiva.")
    if any(depot.max_duration for depot in request.depots) or any(
        customer.service_duration for customer in request.customers
    ):
        raise ValueError("Este solver todavía no admite maxDuration ni serviceDuration distintos de cero.")


def use_matrix_or_default(model: Model, location_by_matrix_index: dict,
                          request: RoutingRequestStd) -> list[list[float]]:
    """Usa la matriz recibida, o euclídea cartesiana solo cuando está ausente."""
    nodes = request.depots + request.customers
    size = len(nodes)
    matrix = request.distance_matrix
    if matrix is None:
        ordered = sorted(nodes, key=lambda node: node.matrix_index)
        matrix = [
            [math.hypot(a.lng - b.lng, a.lat - b.lat) for b in ordered]
            for a in ordered
        ]
    if len(matrix) != size or any(len(row) != size for row in matrix):
        raise ValueError("distanceMatrix debe ser cuadrada y contener todos los nodos.")
    for i, row in enumerate(matrix):
        for j, distance in enumerate(row):
            if not math.isfinite(distance) or distance < 0:
                raise ValueError("Las distancias deben ser finitas y no negativas.")
            if i == j and distance != 0:
                raise ValueError("La diagonal de distanceMatrix debe ser cero.")
            if distance > MAX_VALUE / DISTANCE_SCALE:
                raise ValueError("Una distancia supera el rango admitido por PyVRP con esta escala.")
            model.add_edge(
                location_by_matrix_index[i], location_by_matrix_index[j],
                distance=round(distance * DISTANCE_SCALE),
            )
    return matrix

PARAM_SPECIFICATIONS = [
    ParamSpecification(
        name="seed",
        type=ParamTypes.INTEGER,
        minimum=0,
        maximum=2**31-1,
        default=42
    ),
    ParamSpecification(
        name="maxRuntimeSec",
        type=ParamTypes.NUMBER,
        minimum=0.1,
        maximum=300,
        default=0.1,
        required=False
    ),
    ParamSpecification(
        name="maxVehicleRepetition",
        type=ParamTypes.INTEGER,
        minimum=1,
        maximum=20,
        default=1
    ),
        ParamSpecification(
        name="vehicleStrategyConfig",
        type=ParamTypes.ENUM,
        enum_type=HybridPresets,
        default=HybridPresets.BALANCED
    ),
    ParamSpecification(
        name="vehicleStrategy",
        type=ParamTypes.ENUM,
        enum_type=VehicleStrategies,
        default=VehicleStrategies.FUTURE_HYBRID
    )
]

# TODO
def hgs_adc_manifest():
    metadata = OpenMetada(
        name="hgs-adc",
        namespace="delivera.solvers",
        version="1.0.0"
    )    

    return OpenSpecificationFactory.schema(
        apiVersion="????",
        kind="Engine",
        metadata=metadata,
        param_specifications=PARAM_SPECIFICATIONS
    )


def solve_hgs_adc(
    request: RoutingRequestStd,
    vehicle_strategy_config: HybridPresets,
    vehicle_strategy: VehicleStrategies,
    seed: int = 42,
    max_runtime_sec: float = 3,
    max_vehicle_repetition: int = 1,
) -> RoutingResponseStd:
    """Minimiza distancia dentro del límite de viajes, sin minimizar su número."""
    start_time = time.perf_counter()
    if type(max_vehicle_repetition) is not int or max_vehicle_repetition < 1:
        raise ValueError("El máximo de viajes por vehículo debe ser un entero positivo.")
    if not math.isfinite(max_runtime_sec) or max_runtime_sec <= 0:
        raise ValueError("El tiempo de búsqueda debe ser positivo y finito.")
    vehicle_strategy = VehicleStrategies(vehicle_strategy)
    vehicle_strategy_config = HybridPresets(vehicle_strategy_config)
    _validate_request(request)
    model = Model()
    vehicles_by_depot = defaultdict(lambda: defaultdict(list)) # depot_id -> capacity -> list of vehicle ids
    for vehicle in request.vehicles:
        vehicles_by_depot[vehicle.start_depot_id][vehicle.capacity].append(vehicle.id)

    details = SolverDetailsStd(
        fleet_mode="MULTI_TRIP" if max_vehicle_repetition > 1 else "SINGLE_TRIP",
        max_trips_per_vehicle=max_vehicle_repetition,
    )

    def response(routes: list[RouteStd], failure_reason: str | None = None) -> RoutingResponseStd:
        counts = Counter(route.vehicle_id for route in routes)
        return RoutingResponseStd(
            problem_id=request.problem_id,
            solver_used=SOLVER_TYPE,
            computation_time_ms=round((time.perf_counter() - start_time) * 1000, 3),
            total_cost=math.fsum(route.total_distance for route in routes) if failure_reason is None else None,
            is_feasible=failure_reason is None,
            seed=seed,
            routes=routes,
            solver_details=details.model_copy(update={
                "trip_count": len(routes),
                "vehicles_used": len(counts),
                "additional_trips": sum(max(0, count - 1) for count in counts.values()),
                "failure_reason": failure_reason,
            }),
        )

    locations = {}
    contexts = {}
    type_depots = {}
    type_capacities = {}
    wd, wl = VehicleStrategy.get_weights(vehicle_strategy_config)
    # También se añaden los depósitos sin flota para conservar índices y matriz.
    for depot in request.depots:
        location = model.add_location(x=depot.lng, y=depot.lat)
        locations[depot.matrix_index] = location
        model_depot = model.add_depot(location=location)
        for capacity, ids in sorted(vehicles_by_depot[depot.id].items()):
            type_index = len(contexts)
            vehicle_ids = sorted(ids)
            model.add_vehicle_type(
                num_available=min(len(request.customers), len(ids) * max_vehicle_repetition)
                if request.customers else len(ids),
                capacity=capacity,
                start_depot=model_depot,
                end_depot=model_depot,
            )
            type_depots[type_index] = depot
            type_capacities[type_index] = capacity
            contexts[type_index] = VehicleAssignmentContext(
                vehicle_ids=vehicle_ids, vehicle_position=0,
                route_distance=0, route_load=0,
                distance_by_vehicle={}, load_by_vehicle={},
                distance_weight=wd, load_weight=wl,
                max_trips_per_vehicle=max_vehicle_repetition,
            )
    for customer in request.customers:
        location = model.add_location(x=customer.lng, y=customer.lat)
        locations[customer.matrix_index] = location
        model.add_client(location=location, delivery=customer.demand)
    matrix = use_matrix_or_default(model, locations, request)
    if not request.customers:
        return response([])

    # Las penalizaciones de capacidad mantienen la proporción con la distancia escalada.
    penalties = PenaltyParams()
    params = SolveParams(penalty=PenaltyParams(
        min_penalty=penalties.min_penalty * DISTANCE_SCALE,
        max_penalty=penalties.max_penalty * DISTANCE_SCALE,
    ))
    result = model.solve(stop=MaxRuntime(max_runtime_sec), seed=seed, display=False, params=params)
    if not result.is_feasible():
        return response([], "No se encontró una solución factible con la flota, el límite de viajes y el tiempo indicados.")

    routes = []
    visited = []
    for route in result.best.routes():
        type_index = route.vehicle_type()
        depot = type_depots[type_index]
        if route.start_depot() != route.end_depot() or request.depots[route.start_depot()].id != depot.id:
            return response([], "La solución no conserva el depósito de los vehículos.")
        customers = [request.customers[activity.idx] for activity in route if activity.is_client()]
        load = sum(customer.demand for customer in customers)
        if not customers or load > type_capacities[type_index]:
            return response([], "La solución contiene un viaje vacío o que supera la capacidad.")
        cycle = [depot.matrix_index] + [customer.matrix_index for customer in customers] + [depot.matrix_index]
        distance = math.fsum(matrix[i][j] for i, j in zip(cycle, cycle[1:]))
        ctx = contexts[type_index]
        ctx.route_distance = distance
        ctx.route_load = load
        vehicle_id = VehicleStrategy.decide(ctx, vehicle_strategy)
        routes.append(RouteStd(
            vehicle_id=vehicle_id, depot_id=depot.id,
            stops=[customer.id for customer in customers],
            total_distance=distance, total_load=load,
            trip_index=ctx.trip_count_by_vehicle[vehicle_id],
        ))
        visited.extend(customer.id for customer in customers)
    if Counter(visited) != Counter(customer.id for customer in request.customers):
        return response([], "La solución no visita todos los clientes exactamente una vez.")
    return response(routes)
