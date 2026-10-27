import math
from typing import Any, Dict, List, Tuple

from domain.models import RoutingRequest, RoutingResponse, RouteDto
from domain.spatial_partitioner import SpatialPartitioner
from services.graph_analyzer import GraphAnalyzer
from solvers.pyvrp_solver import solve_internal_tsp, solve_with_pyvrp


class QuantumPreparationError(ValueError):
    """Indica que un subproblema no cumple los requisitos de la via cuantica."""

# Suponemos que la flota es homogenea por deposito y que cada subproblema tiene un solo deposito.
def _homogeneous_capacity(request: RoutingRequest) -> int:
    if not request.vehicles:
        raise QuantumPreparationError(
            f"El deposito {request.instance_name} no tiene vehiculos disponibles."
        )
    capacities = {vehicle.capacity for vehicle in request.vehicles}
    if len(capacities) != 1:
        raise QuantumPreparationError("Este flujo requiere una flota homogenea por subproblema.")
    return next(iter(capacities))

# Calcula una estimación del número mínimo de clústeres necesarios para un subproblema dado (pre-GAP).
def calculate_cluster_k(request: RoutingRequest) -> int:
    """Calcula K_i = ceil(demanda del cluster / capacidad de sus vehiculos)."""
    capacity = _homogeneous_capacity(request) # capacidad de cada vehiculo en el cluster
    if any(customer.demand > capacity for customer in request.customers):
        raise QuantumPreparationError(
            f"Un cliente de {request.instance_name} supera la capacidad Q={capacity}."
        )
    if not request.customers:
        return 0
    return max(1, math.ceil(request.total_demand / capacity))


def validate_superclients(request: RoutingRequest) -> None:
    """Valida la salida final del clustering antes de construir el QUBO."""
    capacity = _homogeneous_capacity(request)
    if any(customer.demand > capacity for customer in request.customers):
        raise QuantumPreparationError(
            f"Un supercliente de {request.instance_name} supera Q={capacity}."
        )
    if request.total_demand > len(request.vehicles) * capacity:
        raise QuantumPreparationError(
            f"El cluster {request.instance_name} supera su capacidad total."
        )


def prepare_quantum_subrequests(
    request: RoutingRequest,
    k_offset: int = 0,
) -> Tuple[List[RoutingRequest], Dict[str, Any]]:
    """Aplica GAP, calcula K_i por deposito y crea subrequests validados."""
    partitioner = SpatialPartitioner(request)
    regions = partitioner.apply_capacitated_voronoi()
    vehicles_by_depot = partitioner.get_vehicles_by_depot()
    clusters_by_depot: Dict[str, int] = {}
    preparation: List[Dict[str, Any]] = []

    for depot in request.depots:
        customers = regions.get(depot.id, [])
        if not customers:
            continue
        vehicles = vehicles_by_depot[depot.id]
        region_request = RoutingRequest(
            instance_name=f"{request.instance_name}_GAP_{depot.id}",
            depots=[depot],
            customers=customers,
            vehicles=vehicles,
        )
        k_i = calculate_cluster_k(region_request)
        requested_k = k_i + k_offset
        clusters_by_depot[depot.id] = min(requested_k, len(customers))
        preparation.append(
            {
                "depot_id": depot.id,
                "original_customers": len(customers),
                "capacity": _homogeneous_capacity(region_request),
                "total_demand": region_request.total_demand,
                "min_superclients": k_i,
                "superclients_requested": clusters_by_depot[depot.id],
            }
        )

    subrequests = []
    # Comprimir cada region y construir su subproblema sin repetir el GAP.
    for depot in request.depots:
        customers = regions.get(depot.id, [])
        if not customers:
            continue
        cluster_limit = clusters_by_depot[depot.id]
        compressed_customers = partitioner.generate_super_clients(
            customers,
            cluster_limit,
        )
        subrequest = RoutingRequest(
            instance_name=(
                f"{request.instance_name}_Sub_{depot.id}_K{cluster_limit}"
            ),
            depots=[depot],
            customers=compressed_customers,
            vehicles=vehicles_by_depot[depot.id],
        )
        subrequests.append(subrequest)
        
        validate_superclients(subrequest)
        region = next(item for item in preparation if item["depot_id"] == subrequest.depots[0].id)
        if len(subrequest.customers) != region["superclients_requested"]:
            raise QuantumPreparationError(
                f"La region {region['depot_id']} genero "
                f"{len(subrequest.customers)} superclientes, pero se solicitaron "
                f"{region['superclients_requested']}."
            )

    k_values = [region["min_superclients"] for region in preparation]
    return subrequests, {
        "regions": preparation,
        "min_superclients_mean": sum(k_values) / len(k_values) if k_values else 0.0,
        "min_superclients_max": max(k_values, default=0),
        "superclients_requested_total": sum(
            region["superclients_requested"] for region in preparation
        ),
        "k_offset": k_offset,
        "gap_regions": len(regions),
        "gap_non_empty_regions": len(subrequests),
    }


def calculate_internal_results(
    subrequests: List[RoutingRequest],
) -> Tuple[float, List[RouteDto]]:
    """Calcula coste y rutas TSP internas de todos los superclientes."""
    internal_cost = 0.0
    internal_routes = []
    for subrequest in subrequests:
        for superclient in subrequest.customers:
            response = solve_internal_tsp(superclient)
            internal_cost += response.total_distance
            internal_routes.extend(response.routes)
    return internal_cost, internal_routes


def solve_best_quantum_candidate(
    request: RoutingRequest,
    backend: str = "qiskit",
    max_qubits: int = 24,
    max_k_offset: int = 3,
    include_mtz: bool = False,
    quantum_timeout_sec: float = 30.0,
) -> Tuple[RoutingResponse, Dict[str, Any]]:
    """Explora K_min..K_min+N y devuelve el candidato de menor coste total."""
    candidates = []
    rejected_candidates = []
    attempted_cluster_counts = set()

    for k_offset in range(max_k_offset + 1):
        try:
            subrequests, preparation = prepare_quantum_subrequests(
                request,
                k_offset=k_offset,
            )
            cluster_count = tuple(
                len(subrequest.customers) for subrequest in subrequests
            )
            # Cuando un offset supera el numero de clientes de una region,
            # min(requested_k, len(customers)) produce exactamente el mismo QUBO.
            # La tupla permite detectar esa repeticion sin descartar regiones distintas.
            if cluster_count in attempted_cluster_counts:
               continue
            attempted_cluster_counts.add(cluster_count)
            if backend == "dwave":
                # D-Wave solo se carga cuando se solicita el backend remoto;
                # asi el filtro, Qiskit y PyVRP no dependen de sus DLL nativas.
                from solvers.quantum_dwave import solve_with_dwave

                response = solve_with_dwave(subrequests)
            else:
                from solvers.quantum_qiskit import solve_with_qiskit_with_timeout

                response = solve_with_qiskit_with_timeout(
                    subrequests,
                    max_qubits=max_qubits,
                    include_mtz=include_mtz,
                    timeout_sec=quantum_timeout_sec,
                )
            internal_cost, internal_routes = calculate_internal_results(subrequests)
            total_cost = response.total_distance + internal_cost
            response = response.model_copy(update={"internal_routes": internal_routes})
            candidates.append((total_cost, response, preparation, internal_cost))
        except Exception as error:
            rejected_candidates.append({"k_offset": k_offset, "reason": str(error)})

    if not candidates:
        reasons = "; ".join(
            f"offset={item['k_offset']}: {item['reason']}"
            for item in rejected_candidates
        )
        raise QuantumPreparationError(
            f"Ningun candidato K_min..K_min+{max_k_offset} es viable. {reasons}"
        )

    total_cost, response, preparation, internal_cost = min(
        candidates, key=lambda candidate: candidate[0] # minimiza coste total (cuantico + interno)
    )
    preparation["global_cost"] = response.total_distance
    preparation["internal_cost"] = internal_cost
    preparation["total_cost"] = total_cost
    preparation["rejected_candidates"] = rejected_candidates
    return response, preparation


def evaluate_initial_quantum_viability(
    request: RoutingRequest,
) -> Dict[str, Any]:
    """Aplica el filtro inicial aproximado antes de ejecutar GAP y QAOA."""
    features = GraphAnalyzer.extract_features(request)
    is_viable = GraphAnalyzer.evaluate_quantum_viability(features)
    return {"is_viable": is_viable, "features": features}


def evaluate_quantum_metrics(
    request: RoutingRequest,
    response: RoutingResponse,
    classical_cost: float | None = None,
    internal_cost: float = 0.0,
    classical_time_limit: int = 60,
) -> Dict[str, Any]:
    """Calcula metricas de factibilidad y coste sin alterar la respuesta."""
    route_loads = [route.load for route in response.routes]
    capacities = {vehicle.id: vehicle.capacity for vehicle in request.vehicles}
    capacity_violations = [
        {
            "vehicle_id": route.vehicle_id,
            "load": route.load,
            "capacity": capacities[route.vehicle_id],
        }
        for route in response.routes
        if route.vehicle_id in capacities and route.load > capacities[route.vehicle_id]
    ]
    if classical_cost is None:
        classical_response = (
            solve_with_pyvrp(request, max_runtime_sec=classical_time_limit)
            if request.customers
            else None
        )
        classical_cost = classical_response.total_distance if classical_response else 0.0
    quantum_cost = response.total_distance
    total_cost = quantum_cost + internal_cost
    degradation = (
        (total_cost - classical_cost) / classical_cost
        if classical_cost != 0 and math.isfinite(classical_cost)
        else None
    )
    return {
        "quantum_total_distance": quantum_cost,
        "internal_cost": internal_cost,
        "total_cost": total_cost,
        "classical_reference_distance": classical_cost,
        "relative_degradation": degradation,
        "route_count": len(response.routes),
        "route_loads": route_loads,
        "capacities": capacities,
        "capacity_violations": capacity_violations,
        "is_feasible": response.is_feasible and not capacity_violations,
        "qubits": response.qubits,
    }


def solve_with_classical_fallback(
    request: RoutingRequest,
    reason: str,
    max_runtime_sec: int = 60,
) -> RoutingResponse:
    """Devuelve una respuesta clasica cuando la via cuantica no es viable."""
    if not request.customers:
        return RoutingResponse(
            instance_name=request.instance_name,
            solver_used="PyVRP classical fallback",
            execution_time_ms=0.0,
            total_distance=0.0,
            is_feasible=True,
            routes=[],
            fallback_reason=reason,
        )
    response = solve_with_pyvrp(request, max_runtime_sec=max_runtime_sec)
    return response.model_copy(
        update={
            "solver_used": "PyVRP classical fallback",
            "fallback_reason": reason,
        }
    )


def solve_with_classical(
    request: RoutingRequest,
    max_runtime_sec: int = 60,
) -> RoutingResponse:
    """Resuelve directamente la instancia global con PyVRP.

    Esta función representa una solicitud explícita del backend clásico, por
    lo que no debe etiquetarse como fallback ni incorporar un motivo de
    derivación.
    """
    if not request.customers:
        return RoutingResponse(
            instance_name=request.instance_name,
            solver_used="PyVRP",
            execution_time_ms=0.0,
            total_distance=0.0,
            is_feasible=True,
            routes=[],
        )
    return solve_with_pyvrp(request, max_runtime_sec=max_runtime_sec)
