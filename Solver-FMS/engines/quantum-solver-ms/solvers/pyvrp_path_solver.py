"""Recorrido de un grupo entre dos extremos externos, sin regresar al centroide."""

from collections import Counter
from collections.abc import Callable, Sequence
import math

from pyvrp import Model, PenaltyParams, SolveParams
from pyvrp.constants import MAX_VALUE
from pyvrp.stop import MaxRuntime

from domain.models import CustomerDto, NodeDto


DistanceFunction = Callable[[NodeDto, NodeDto], float] # alias de tipo
DISTANCE_SCALE = 1000


def checked_distance(distance: DistanceFunction, origin: NodeDto, destination: NodeDto) -> float:
    value = float(distance(origin, destination))
    if not math.isfinite(value) or value < 0:
        raise ValueError("Las distancias deben ser finitas y no negativas.")
    return value


def solve_open_path(
    customers: Sequence[CustomerDto],
    origin: NodeDto,
    destination: NodeDto,
    *, # Los parámetros siguientes son opcionales y deben pasarse como argumentos con nombre.
    distance: DistanceFunction = lambda o, d: float(o.euclidean_distance(d)),
    max_runtime_sec: float = 0.1,
    seed: int = 42,
) -> list[CustomerDto]:
    """Devuelve solo los clientes ordenados. Los extremos no son entregas del grupo.

    El destino puede ser el centroide del siguiente grupo. Solo orienta la salida,
    no se incorpora a las paradas finales ni se contabiliza como tramo definitivo.
    """
    if not math.isfinite(max_runtime_sec) or max_runtime_sec <= 0:
        raise ValueError("El tiempo de búsqueda local debe ser positivo y finito.")
    if len({customer.id for customer in customers}) != len(customers):
        raise ValueError("El grupo contiene clientes repetidos.")
    if any(customer.demand <= 0 for customer in customers):
        raise ValueError("Las demandas de los clientes deben ser positivas.")
    if len(customers) <= 1:
        return list(customers)

    model = Model()
    nodes = [origin, destination, *customers]
    locations = [model.add_location(x=node.x, y=node.y) for node in nodes]
    # Son extremos del modelo auxiliar, no depósitos operativos ni recargas.
    start = model.add_depot(location=locations[0])
    end = model.add_depot(location=locations[1])
    for customer, location in zip(customers, locations[2:]):
        model.add_client(location=location, delivery=customer.demand)
    model.add_vehicle_type(
        num_available=1,
        capacity=sum(customer.demand for customer in customers), # vehículo ficticio con capacidad suficiente para todo el grupo
        start_depot=start,
        end_depot=end,
    )
    for i, frm in enumerate(locations):
        for j, to in enumerate(locations):
            value = checked_distance(distance, nodes[i], nodes[j])
            if value > MAX_VALUE / DISTANCE_SCALE:
                raise ValueError("Una distancia supera el límite admitido por PyVRP.")
            model.add_edge(frm, to, distance=round(value * DISTANCE_SCALE))

    # Las penalizaciones se escalan junto a las distancias, como en PyVRP v2.
    defaults = PenaltyParams()
    penalties = PenaltyParams(
        min_penalty=defaults.min_penalty * DISTANCE_SCALE,
        max_penalty=defaults.max_penalty * DISTANCE_SCALE,
    )
    result = model.solve(
        stop=MaxRuntime(max_runtime_sec), seed=seed, display=False,
        params=SolveParams(penalty=penalties),
    )
    if not result.is_feasible() or len(result.best.routes()) != 1:
        raise ValueError("PyVRP no encontró un recorrido completo para el grupo.")
    route = result.best.routes()[0]
    if route.start_depot() != 0 or route.end_depot() != 1:
        raise ValueError("El recorrido local no conserva sus extremos.")
    indices = [activity.idx for activity in route if activity.is_client()]
    if Counter(indices) != Counter(range(len(customers))):
        raise ValueError("El recorrido local pierde o repite clientes.")
    return [customers[index] for index in indices]
