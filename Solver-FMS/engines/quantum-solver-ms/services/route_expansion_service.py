"""Expande las rutas reducidas conservando grupos, flota y clientes originales."""

from collections import Counter
import math
import time

from domain.models import RouteDto, RoutingRequest, RoutingResponse
from domain.spatial_partitioner import SpatialPartitioner
from solvers.pyvrp_path_solver import (
    DistanceFunction,
    checked_distance,
    solve_open_path,
)


def expand_quantum_routes(
    request: RoutingRequest,
    subrequests: list[RoutingRequest],
    response: RoutingResponse,
    *,
    distance: DistanceFunction = lambda o, d: float(o.euclidean_distance(d)),
    max_runtime_sec: float = 0.1,
    seed: int = 42,
) -> tuple[RoutingResponse, dict[str, float | int]]:
    """Resuelve cada grupo una vez, apuntando al centroide del siguiente.

    La función de distancia debe aceptar también centroides y usar las mismas
    unidades para todos los nodos. Por defecto conserva la métrica nativa.
    Los miembros se recuperan de internal_nodes, nunca separando el ID SC_...
    """
    started = time.perf_counter()
    if not response.is_feasible:
        raise ValueError("No se puede expandir una respuesta no factible.")
    if not math.isfinite(max_runtime_sec) or max_runtime_sec <= 0:
        raise ValueError("El tiempo de búsqueda local debe ser positivo y finito.")
    if not math.isfinite(response.total_distance) or response.total_distance < 0:
        raise ValueError("El coste reducido debe ser finito y no negativo.")
    if not request.depots:
        raise ValueError("La petición original debe incluir al menos un depósito.")

    customers = {customer.id: customer for customer in request.customers}
    depots = {depot.id: depot for depot in request.depots}
    vehicles = {vehicle.id: vehicle for vehicle in request.vehicles}
    if (len(customers) != len(request.customers) or len(depots) != len(request.depots)
            or len(vehicles) != len(request.vehicles)):
        raise ValueError("Los IDs deben ser únicos dentro de clientes, depósitos y vehículos.")
    if any(customer.demand <= 0 or customer.internal_nodes for customer in request.customers):
        raise ValueError("La petición original debe contener clientes sin agrupar y con demanda positiva.")
    if any(vehicle.capacity <= 0 for vehicle in request.vehicles):
        raise ValueError("Las capacidades de los vehículos deben ser positivas.")

    # El contrato nativo vigente distribuye la flota por bloques de depósito.
    home_fleets = SpatialPartitioner(request).get_vehicles_by_depot()
    groups = {}
    members = {}
    partition_visits = []
    for subrequest in subrequests:
        if len(subrequest.depots) != 1 or subrequest.depots[0].id not in depots:
            raise ValueError("Cada subproblema debe referenciar un depósito original.")
        depot_id = subrequest.depots[0].id
        if depot_id in groups:
            raise ValueError("Se ha repetido un subproblema de depósito.")
        expected_fleet = {vehicle.id: vehicle for vehicle in home_fleets[depot_id]}
        if (len(subrequest.vehicles) != len(expected_fleet)
                or {v.id: v for v in subrequest.vehicles} != expected_fleet):
            raise ValueError("El subproblema no conserva la flota de su depósito.")
        groups[depot_id] = {node.id: node for node in subrequest.customers}
        if len(groups[depot_id]) != len(subrequest.customers):
            raise ValueError("El subproblema contiene grupos con IDs repetidos.")
        for node in subrequest.customers:
            leaves = node.internal_nodes or [node]
            if any(leaf.internal_nodes or leaf.id not in customers for leaf in leaves):
                raise ValueError("Un grupo contiene miembros desconocidos o agrupaciones anidadas.")
            # Los datos definitivos siempre proceden de la petición original.
            original_members = [customers[leaf.id] for leaf in leaves]
            if node.demand != sum(customer.demand for customer in original_members):
                raise ValueError("La demanda del grupo no coincide con sus clientes originales.")
            members[depot_id, node.id] = original_members
            partition_visits.extend(customer.id for customer in original_members)
    if Counter(partition_visits) != Counter(customers.keys()):
        raise ValueError("Los grupos pierden o repiten clientes originales.")

    routes = []
    visited = []
    used_vehicles = set()
    reduced_visits = Counter()
    internal_distances = []
    expanded_groups = 0
    for route in response.routes:
        if len(route.stops) < 3 or route.stops[0] != route.stops[-1]:
            raise ValueError("La ruta reducida debe salir de su depósito y regresar a él.")
        depot_id = route.stops[0]
        if depot_id not in groups:
            raise ValueError("La ruta referencia un depósito sin subproblema.")
        if route.vehicle_id not in {vehicle.id for vehicle in home_fleets[depot_id]}:
            raise ValueError("El vehículo no pertenece a la flota del depósito de la ruta.")
        if route.vehicle_id in used_vehicles:
            raise ValueError("El flujo cuántico actual permite un solo viaje por vehículo.")
        used_vehicles.add(route.vehicle_id)
        reduced = route.stops[1:-1]
        if any(node_id not in groups[depot_id] for node_id in reduced):
            raise ValueError("La ruta contiene un grupo desconocido.")
        expanded = []
        previous = depots[depot_id]
        for index, node_id in enumerate(reduced):
            node = groups[depot_id][node_id]
            reduced_visits[depot_id, node_id] += 1
            following = (
                groups[depot_id][reduced[index + 1]]
                if index + 1 < len(reduced) else depots[depot_id]
            )
            group_members = members[depot_id, node_id]
            if node.internal_nodes:
                ordered = solve_open_path(
                    group_members, previous, following, distance=distance,
                    max_runtime_sec=max_runtime_sec, seed=seed,
                )
                if Counter(customer.id for customer in ordered) != Counter(c.id for c in group_members):
                    raise ValueError("El recorrido interno pierde o repite clientes del grupo.")
                expanded_groups += 1
                internal_distances.extend(
                    checked_distance(distance, a, b) for a, b in zip(ordered, ordered[1:])
                )
            else:
                ordered = group_members
            expanded.extend(ordered)
            previous = ordered[-1] # El último nodo del grupo se convierte en el anterior para el siguiente grupo.

        load = sum(customer.demand for customer in expanded)
        if route.load != load or load > vehicles[route.vehicle_id].capacity:
            raise ValueError("La carga de la ruta no coincide o supera la capacidad del vehículo.")
        nodes = [depots[depot_id], *expanded, depots[depot_id]]
        route_distance = math.fsum(
            checked_distance(distance, a, b) for a, b in zip(nodes, nodes[1:])
        )
        routes.append(RouteDto(
            vehicle_id=route.vehicle_id,
            stops=[node.id for node in nodes], distance=route_distance, load=load,
        ))
        visited.extend(customer.id for customer in expanded)

    if reduced_visits != Counter(members.keys()) or Counter(visited) != Counter(customers.keys()):
        raise ValueError("La solución pierde o repite grupos o clientes originales.")
    total_distance = math.fsum(route.distance for route in routes)
    internal_cost = math.fsum(internal_distances)
    elapsed_ms = (time.perf_counter() - started) * 1000
    # internal_routes deja de representar viajes separados. Todo está en routes.
    expanded_response = response.model_copy(update={
        "instance_name": request.instance_name,
        "routes": routes,
        "total_distance": total_distance,
        "internal_routes": [],
        "execution_time_ms": round(response.execution_time_ms + elapsed_ms, 3),
    })
    return expanded_response, {
        "compressed_cost": response.total_distance,
        "global_cost": total_distance - internal_cost,
        "internal_cost": internal_cost,
        "total_cost": total_distance,
        "expanded_superclients": expanded_groups,
        "expansion_time_ms": round(elapsed_ms, 3),
        "expansion_time_limit_sec": max_runtime_sec,
    }
