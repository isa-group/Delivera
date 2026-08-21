"""
El modelo de instancia: que pide el problema y si una solucion lo cumple.

No sabe que existe una API ni que existe un informe. Es el modulo que reutiliza
cualquier analisis posterior de los resultados.
"""

import json
import math
import statistics

from .. import BASE_DIR

INSTANCES_DIR = BASE_DIR / "instances-MD-CVRP-JSON"

# Los BKS viven en un fichero de datos que comparten este paquete y el test de
# benchmark. Tenerlos dos veces garantiza que algun dia dejen de coincidir.
BKS_SOURCE = BASE_DIR / "best-known.json"

TOLERANCE = 1e-6


class Instance:
    """
    Una instancia Cordeau con lo necesario para juzgar una solucion.

    Es la version en Python de CordeauInstance, la clase que usa el test de benchmark
    del gateway: mismas reglas de validacion y misma forma de recalcular el coste. Son
    dos implementaciones que hay que mantener a la vez, a cambio de que esto no dependa
    de Maven ni del sistema de test.
    """

    def __init__(self, name, data):
        self.name = name
        self.fleet = data["vehicles_per_depot"]

        # El gateway numera los depositos por posicion, empezando en 1. 0 en
        # max_duration significa sin limite, igual que en el motor.
        self.depots = {}
        for position, depot in enumerate(data["depots"]):
            self.depots[str(position + 1)] = {
                "x": depot["x"], "y": depot["y"],
                "capacity": depot["vehicle_capacity"],
                "max_duration": depot["max_duration"] or math.inf,
                "raw_max_duration": depot["max_duration"],
            }

        self.customers = {
            str(customer["id"]): {
                "x": customer["x"], "y": customer["y"],
                "demand": customer["demand"],
                "service": customer["service_duration"],
            }
            for customer in data["customers"]
        }

    @staticmethod
    def load(name):
        data = json.loads((INSTANCES_DIR / f"{name}.json").read_text(encoding="utf-8"))
        return Instance(name, data)

    def describe(self):
        limit = self.max_duration()
        return (f"{len(self.depots)} depositos, {len(self.customers)} clientes, "
                f"duracion maxima {'sin limite' if limit == 0 else int(limit)}")

    def max_duration(self):
        """0 significa sin limite. Todas las instancias del banco lo comparten entre depositos."""
        return max(depot["raw_max_duration"] for depot in self.depots.values())

    def capacity(self):
        return next(iter(self.depots.values()))["capacity"]

    def cost(self, response):
        # Recalcula el coste desde las paradasç
        return sum(self._route_distance(route) for route in routes_of(response))

    def violations(self, response):
        """
        Todas las restricciones que incumple la solucion, no solo la primera: asi se ve
        de un vistazo si a un motor se le escapa una restriccion concreta o si la
        solucion esta rota entera.
        """
        problems = []
        served = set()
        vehicles_by_depot = {}

        for route in routes_of(response):
            vehicle = str(route.get("vehicleId"))
            depot_id = str(route.get("depotId"))
            depot = self.depots.get(depot_id)
            if depot is None:
                problems.append(f"Ruta {vehicle} sale de un deposito inexistente: {depot_id}")
                continue
            vehicles_by_depot.setdefault(depot_id, set()).add(vehicle)

            load = 0
            service = 0.0
            for stop in route.get("stops") or []:
                customer = self.customers.get(str(stop))
                if customer is None:
                    problems.append(f"Ruta {vehicle} visita un cliente inexistente: {stop}")
                    continue
                if str(stop) in served:
                    problems.append(f"Cliente {stop} servido mas de una vez")
                served.add(str(stop))
                load += customer["demand"]
                service += customer["service"]

            if load > depot["capacity"]:
                problems.append(f"Ruta {vehicle} excede capacidad: {load} > {depot['capacity']}")
            reported_load = route.get("totalLoad")
            if reported_load is not None and reported_load != load:
                problems.append(
                    f"Ruta {vehicle} informa carga {reported_load} pero sus paradas suman {load}")

            # La duracion de una ruta es su distancia mas los tiempos de servicio.
            duration = self._route_distance(route) + service
            if duration > depot["max_duration"] + TOLERANCE:
                problems.append(
                    f"Ruta {vehicle} excede duracion: {duration:.2f} > {depot['max_duration']:.2f}")

        for customer_id in self.customers:
            if customer_id not in served:
                problems.append(f"Cliente {customer_id} sin servir")

        # Se cuentan vehiculos distintos, no rutas: un vehiculo puede hacer mas de un
        # viaje. Lo que no puede es no existir.
        for depot_id, vehicles in vehicles_by_depot.items():
            if len(vehicles) > self.fleet:
                problems.append(f"Deposito {depot_id} usa {len(vehicles)} vehiculos, "
                                f"disponibles {self.fleet}")

        return problems

    def features(self):
        #Devuelve las caracteristicas de la instancia que van al CSV y al informe.
        customers = list(self.customers.values())
        depots = list(self.depots.values())
        capacity = self.capacity()

        demand = sum(c["demand"] for c in customers)
        xs = [c["x"] for c in customers]
        ys = [c["y"] for c in customers]
        area = (max(xs) - min(xs)) * (max(ys) - min(ys))

        return {
            "num_customers": len(customers),
            "num_depots": len(depots),
            "vehicles_per_depot": self.fleet,
            "vehicle_capacity": capacity,
            "max_duration": self.max_duration(),
            "total_demand": demand,
            # Cuanto de la flota hace falta como minimo: mide lo apretada que esta la
            # instancia de capacidad, que es lo que separa a las faciles de las duras.
            "load_ratio": demand / (len(depots) * self.fleet * capacity),
            "avg_service_duration": statistics.fmean(c["service"] for c in customers),
            "customers_per_depot": len(customers) / len(depots),
            "area": area,
            "customer_density": len(customers) / area if area else 0.0,
            "mean_nn_distance": statistics.fmean(
                min(distance(c, other) for other in customers if other is not c)
                for c in customers),
            "mean_depot_distance": statistics.fmean(
                min(distance(c, depot) for depot in depots) for c in customers),
        }

    def _route_distance(self, route):
        depot = self.depots.get(str(route.get("depotId")))
        if depot is None:
            return 0.0

        node = depot
        total = 0.0
        for stop in route.get("stops") or []:
            customer = self.customers.get(str(stop))
            if customer is None:
                continue
            total += distance(node, customer)
            node = customer
        return total + distance(node, depot)


def distance(a, b):
    return math.hypot(a["x"] - b["x"], a["y"] - b["y"])


def routes_of(response):
    return response.get("routes") or []


def all_names():
    """Las 33 del banco, ordenadas."""
    return sorted(path.stem for path in INSTANCES_DIR.glob("*.json"))


def exists(name):
    return (INSTANCES_DIR / f"{name}.json").exists()


def load_best_known():
    if not BKS_SOURCE.exists():
        return {}
    return json.loads(BKS_SOURCE.read_text(encoding="utf-8"))
