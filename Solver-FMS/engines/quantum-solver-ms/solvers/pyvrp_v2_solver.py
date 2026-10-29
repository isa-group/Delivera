from dataclasses import dataclass
import time
from collections import defaultdict
from pyvrp import Model
from pyvrp.stop import MaxRuntime
from typing import Any
from abc import ABC, abstractmethod
from services.vehicle_strategy_service import VehicleStrategy, VehicleAssignmentContext, HybridPresets, VehicleStrategies
from api.adapter.specification import OpenSpecificationFactory, ParamSpecification, ParamTypes, OpenMetada
from api.adapter.models import (
    CustomerStd,
    DepotStd,
    RouteStd,
    RoutingRequestStd,
    RoutingResponseStd,
    VehicleStd,
    NodeStd
)

SOLVER_TYPE = 'HGS-ADC'
PARAM_SPECIFICATIONS = [
    ParamSpecification(
        name="seed",
        type=ParamTypes.INTEGER,
        minimum=0,
        maximum=2**31-1,
        default=42
    ),
    ParamSpecification(
        name="max_runtime_sec",
        type=ParamTypes.NUMBER,
        minimum=0.1,
        maximum=300,
        default=0.1,
        required=False
    ),
    ParamSpecification(
        name="use_vehicle_repetition",
        type=ParamTypes.BOOLEAN,
        default=True,
    ),
    ParamSpecification(
        name="max_vehicle_repetition",
        type=ParamTypes.INTEGER,
        minimum=1,
        maximum=20,
        default=1
    ),
        ParamSpecification(
        name="vehicle_strategy_config",
        type=ParamTypes.ENUM,
        enum_type=HybridPresets,
        default=HybridPresets.BALANCED
    ),
    ParamSpecification(
        name="vehicle_strategy",
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
    """
    Resuelve un RoutingRequest utilizando el estado del arte clásico PyVRP (metaheurística HGS-ADC).
    Devuelve la respuesta completa, incluyendo rutas y cargas.
    """
    start_time = time.perf_counter()
    m = Model()
    
    # El contrato del proyecto ordena los vehículos por bloques de depósito.
    # Cada bloque representa una flota homogénea, aunque dos depósitos pueden
    # tener capacidades distintas entre sí.
    if not request.depots:
        raise ValueError("La instancia debe incluir al menos un depósito.")
    if not request.vehicles:
        raise ValueError("La instancia debe incluir al menos un vehículo.")
    
    # Add vehicles by depot

    vehicles_by_depot = {}
    for vehicle in request.vehicles:
        start_depot_id = vehicle.start_depot_id
        capacity = vehicle.capacity
        if start_depot_id not in vehicles_by_depot:
            vehicles_by_depot[start_depot_id] = {}
        depot_types = vehicles_by_depot[start_depot_id]
        if capacity not in depot_types:
            depot_types[capacity] = []
        vehicles_by_depot[start_depot_id][capacity].append(vehicle.id)

    # 1. Añadir depósitos al modelo
    # PyVRP maneja internamente las distancias usando los objetos devueltos por add_location, add_depot y add_client.
    #vehicle_ids_by_type: list[list[str]] = []
    location_by_matrix_index = {}


    model_depots = []
    vehicle_type_mapping = {}
    next_vehicle_type = 0
    context_by_vehicle_type : dict[int, VehicleAssignmentContext] = {}
    for depot in request.depots:
        if depot.id in vehicles_by_depot:
            loc = m.add_location(x=depot.lng, y=depot.lat)
            location_by_matrix_index[depot.matrix_index] = loc
            model_depot = m.add_depot(location=loc)
            model_depots.append(model_depot)
            for capacity, vehicle_ids in vehicles_by_depot[depot.id].items():
                m.add_vehicle_type(
                    num_available=len(vehicle_ids) * max_vehicle_repetition, # Multiple routes per vehicle 
                    capacity=capacity,
                    start_depot=model_depot,
                    end_depot=model_depot,
                )
                # El índice de esta lista coincide con el tipo de vehículo que PyVRP
                # devolverá en cada ruta mediante route.vehicle_type().
                #vehicle_ids_by_type.append(vehicle_ids)

                vehicle_type_mapping[next_vehicle_type] = vehicle_ids
                wd, wl = VehicleStrategy.get_weights(presets=vehicle_strategy_config)
                context_by_vehicle_type[next_vehicle_type] =  VehicleAssignmentContext(
                    vehicle_ids=vehicle_ids,
                    vehicle_position=0,
                    route_distance=0,
                    route_load=0,
                    distance_by_vehicle={},
                    load_by_vehicle={},
                    distance_weight=wd,
                    load_weight=wl,
                )
                
                next_vehicle_type += 1

             
        
    # 2. Añadir clientes al modelo
    for customer in request.customers:
        loc = m.add_location(x=customer.lng, y=customer.lat)
        location_by_matrix_index[customer.matrix_index] = loc
        m.add_client(location=loc, delivery=customer.demand)
  

        


    use_matrix_or_default(
        model=m,
        location_by_matrix_index=location_by_matrix_index,
        request=request
    )
            
    # 5. Ejecutar la Búsqueda Genética Híbrida (HGS) de PyVRP (silenciado para orquestación)
    res = m.solve(stop=MaxRuntime(max_runtime_sec), seed=seed, display=False)
    
    # Se mide el tiempo completo, incluida la construcción del modelo y la
    # búsqueda de PyVRP, para devolverlo en la respuesta de la API.
    execution_time_ms = round((time.perf_counter() - start_time) * 1000, 3)

    # Sin una solución candidata no existen rutas que decodificar. El coste
    # infinito permite a la capa superior reconocer el fallo de factibilidad.
    if not res.best:
        return RoutingResponseStd(
            problem_id=request.problem_id,
            solver_used=SOLVER_TYPE,
            computation_time_ms= execution_time_ms,
            total_cost=float("inf"),
            seed=seed,
            is_feasible=False,
            routes=[],
        )

    # Convertimos la solución interna de PyVRP a los DTO públicos de la API.
    routes = []
    depot_by_index = {index: depot for index, depot in enumerate(request.depots)}
    customer_by_index = {
        index: customer
        for index, customer in enumerate(request.customers)
    }

    # PyVRP identifica una ruta por su tipo de vehículo, no por el ID original
    # de la entrada. Este contador asigna a cada ruta un ID distinto dentro de
    # la misma flota homogénea, por ejemplo V1 y V2 para dos rutas del depósito.
    used_vehicle_ids_by_type = defaultdict(int)
    

    for route in res.best.routes():
        # Las actividades contienen clientes. Se añaden explícitamente los
        # depósitos inicial y final para respetar el contrato de RouteDto.
        activities = list(route)
        start_depot = depot_by_index.get(route.start_depot(), request.depots[0])
        stops = [start_depot.id]
        for activity in activities:
            if activity.is_client():
                customer = customer_by_index[activity.idx]
                stops.append(customer.id)
        end_depot = depot_by_index.get(route.end_depot(), start_depot)
        stops.append(end_depot.id)

        # El tipo indica a qué depósito pertenece la ruta. La posición cuenta
        # cuántas rutas anteriores ya han consumido IDs de ese mismo tipo.
        vehicle_type = route.vehicle_type() # devuelve el tipo de vehículo de una flota no el índice del vehículo que hace la ruta
        vehicle_ids = vehicle_type_mapping[vehicle_type]

        vehicle_position = used_vehicle_ids_by_type[vehicle_type]
       
        total_distance = float(route.distance())
        total_load = int(sum(route.delivery()))

        ctx = context_by_vehicle_type[vehicle_type]        

        ctx.route_distance = total_distance
        ctx.route_load = total_load
        ctx.vehicle_position = vehicle_position

        vehicle_id = VehicleStrategy.decide(strategy=vehicle_strategy, ctx=ctx) # Multiple routes per vehicle 

        used_vehicle_ids_by_type[vehicle_type] += 1
        routes.append(
            RouteStd(
                vehicle_id=vehicle_id,
                depot_id=stops[0],
                stops=stops[1:-1],
                total_distance=total_distance,
                total_load=total_load,
            )
        )

    # El coste y la factibilidad global proceden directamente de la mejor
    # solución encontrada por PyVRP.


    return RoutingResponseStd(
        problem_id=request.problem_id,
        solver_used=SOLVER_TYPE,
        computation_time_ms=execution_time_ms,
        total_cost=float(res.cost()),
        seed=seed,
        is_feasible=all(route.is_feasible() for route in res.best.routes()),
        routes=routes,
    )



def use_matrix_or_default(
    model: Model,
    location_by_matrix_index: dict[int,Any], 
    request: RoutingRequestStd
):
    all_dtos: list[NodeStd]  = request.depots + request.customers
    matrix: list[list[float]] = request.distance_matrix

    not_one_row_per_node = len(matrix) != len(all_dtos)
    not_all_rows_have_all_nodes =  any(len(row) != len(all_dtos) for row in matrix)
    incorrect_indexed_nodes = len(location_by_matrix_index) != len(all_dtos)

    use_fallback = (
        not_one_row_per_node
        or
        not_all_rows_have_all_nodes
        or
        incorrect_indexed_nodes
    )

    if (use_fallback):
        # Euclidean distance - fallback  
        for from_node in all_dtos:
            i = from_node.matrix_index
            from_loc = location_by_matrix_index[i]

            for to_node in all_dtos:
                j = to_node.matrix_index

                if  i == j: continue

                to_loc = location_by_matrix_index[j]

                dist = from_node.euclidean_distance(to_node)

                model.add_edge(from_loc, to_loc, distance=dist)
    else:
        for node in all_dtos:
            i = node.matrix_index
            for j in range(0,len(all_dtos)):
                if i == j: continue

                from_loc = location_by_matrix_index[i]
                to_loc = location_by_matrix_index[j]
                dist =  matrix[i][j]

                model.add_edge(from_loc, to_loc, distance=dist)