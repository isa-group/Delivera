import time
from collections import defaultdict
from pyvrp import Model
from pyvrp.stop import MaxRuntime
from domain.models import (
    RouteDto,
    RoutingRequest,
    RoutingResponse,
)

def solve_with_pyvrp(
    request: RoutingRequest,
    max_runtime_sec: int = 60,
) -> RoutingResponse:
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
    if len(request.vehicles) % len(request.depots) != 0:
        raise ValueError(
            "La flota debe repartirse uniformemente entre los depósitos."
        )

    # 1. Añadir depósitos al modelo
    # PyVRP maneja internamente las distancias usando los objetos devueltos por add_location, add_depot y add_client.
    model_depots = []
    for d in request.depots:
        loc = m.add_location(x=d.x, y=d.y)
        model_depots.append(m.add_depot(location=loc))
        
    # 2. Añadir clientes al modelo
    for c in request.customers:
        loc = m.add_location(x=c.x, y=c.y)
        m.add_client(location=loc, delivery=c.demand)
        
    # 3. Configurar una flota homogénea por cada depósito.
    # PyVRP necesita un tipo de vehículo por depósito para fijar el origen y
    # retorno de sus rutas. Conservamos los IDs para recuperarlos al decodificar.
    vehicles_per_depot = len(request.vehicles) // len(request.depots)
    vehicle_ids_by_type = []
    for depot_index, model_depot in enumerate(model_depots):
        start = depot_index * vehicles_per_depot
        end = start + vehicles_per_depot
        depot_vehicles = request.vehicles[start:end]
        capacities = {vehicle.capacity for vehicle in depot_vehicles}
        if len(capacities) != 1:
            raise ValueError(
                "La flota de cada depósito debe tener una capacidad homogénea."
            )

        vehicle_ids = [vehicle.id for vehicle in depot_vehicles]
        m.add_vehicle_type(
            num_available=len(vehicle_ids),
            capacity=next(iter(capacities)),
            start_depot=model_depot,
            end_depot=model_depot,
        )
        # El índice de esta lista coincide con el tipo de vehículo que PyVRP
        # devolverá en cada ruta mediante route.vehicle_type().
        vehicle_ids_by_type.append(vehicle_ids)
        
    # 4. Construir la matriz de distancias (Euclídea)
    # PyVRP almacena internamente las ubicaciones en m.locations en el orden de inserción
    all_dtos = request.depots + request.customers
    
    for i, frm_loc in enumerate(m.locations):
        for j, to_loc in enumerate(m.locations):
            # Distancia euclídea redondeada al entero más cercano (estándar de la literatura)
            dist = all_dtos[i].euclidean_distance(all_dtos[j])
            m.add_edge(frm_loc, to_loc, distance=dist)
            
    # 5. Ejecutar la Búsqueda Genética Híbrida (HGS) de PyVRP (silenciado para orquestación)
    res = m.solve(stop=MaxRuntime(max_runtime_sec), seed=42, display=False)
    
    # Se mide el tiempo completo, incluida la construcción del modelo y la
    # búsqueda de PyVRP, para devolverlo en la respuesta de la API.
    execution_time_ms = round((time.perf_counter() - start_time) * 1000, 3)

    # Sin una solución candidata no existen rutas que decodificar. El coste
    # infinito permite a la capa superior reconocer el fallo de factibilidad.
    if not res.best:
        return RoutingResponse(
            instance_name=request.instance_name,
            solver_used="PyVRP",
            execution_time_ms=execution_time_ms,
            total_distance=float("inf"),
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
        vehicle_ids = vehicle_ids_by_type[vehicle_type] # IDs de los vehículos de la flota homogénea del depósito

        vehicle_position = used_vehicle_ids_by_type[vehicle_type]
        vehicle_id = vehicle_ids[vehicle_position]

        used_vehicle_ids_by_type[vehicle_type] += 1
        routes.append(
            RouteDto(
                vehicle_id=vehicle_id,
                stops=stops,
                distance=float(route.distance()),
                load=int(sum(route.delivery())),
            )
        )

    # El coste y la factibilidad global proceden directamente de la mejor
    # solución encontrada por PyVRP.
    return RoutingResponse(
        instance_name=request.instance_name,
        solver_used="PyVRP",
        execution_time_ms=execution_time_ms,
        total_distance=float(res.cost()),
        is_feasible=all(route.is_feasible() for route in res.best.routes()),
        routes=routes,
    )
