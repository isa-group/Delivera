import numpy as np
import pulp
from typing import List, Dict
from sklearn.cluster import AgglomerativeClustering

# Importamos los modelos unificados
from domain.models import RoutingRequest, CustomerDto, DepotDto, VehicleDto

class SpatialPartitioner:
    """
    Motor espacial de pre-procesamiento para Delivera.
    Ejecuta el particionamiento de Voronoi Capacitado (GAP) y la compresión geométrica (Súper-Clientes).
    """
    
    def __init__(self, global_request: RoutingRequest):
        self.request = global_request
        self.customers = global_request.customers
        self.depots = global_request.depots
        self.vehicles = global_request.vehicles

    def get_vehicles_by_depot(self) -> Dict[str, List[VehicleDto]]:
        """Devuelve el bloque de vehiculos que corresponde a cada deposito."""
        if not self.depots:
            return {}
        vehicles_per_depot, remainder = divmod(len(self.vehicles), len(self.depots))

        if remainder: # no debe haber resto, la flota debe ser divisible uniformemente entre depósitos
            raise ValueError("La flota no se puede repartir uniformemente entre depositos.")
        return {
            depot.id: self.vehicles[
                depot_index * vehicles_per_depot : (depot_index + 1) * vehicles_per_depot
            ]
            for depot_index, depot in enumerate(self.depots)
        }

    def apply_capacitated_voronoi(self) -> Dict[str, List[CustomerDto]]:
        """
        Resuelve el Problema de Asignación Generalizada (GAP) utilizando Programación Lineal Entera.
        Asigna cada cliente a un depósito minimizando la distancia euclídea, 
        sujeto a la restricción estricta de capacidad total de la flota de ese depósito.
        """
        # 1. Calcular la capacidad total disponible en cada depósito
        vehicles_by_depot = self.get_vehicles_by_depot()
        depot_capacities = {
            depot.id: sum(vehicle.capacity for vehicle in vehicles_by_depot[depot.id])
            for depot in self.depots
        }

        # 2. Definir el problema MILP
        prob = pulp.LpProblem("Capacitated_Voronoi_GAP", pulp.LpMinimize)

        # 3. Variables de Decisión: x[i][j] = 1 si el cliente i se asigna al depósito j
        x = pulp.LpVariable.dicts("x", 
                                  ((c.id, d.id) for c in self.customers for d in self.depots),
                                  cat='Binary')

        # 4. Función Objetivo: Minimizar la suma de distancias
        prob += pulp.lpSum(
            c.euclidean_distance(d) * x[c.id, d.id] 
            for c in self.customers for d in self.depots
        )

        # 5. Restricción A: Cada cliente debe ser asignado a exactamente un depósito
        for c in self.customers:
            prob += pulp.lpSum(x[c.id, d.id] for d in self.depots) == 1

        # 6. Restricción B: La demanda asignada no puede superar la capacidad del depósito
        for d in self.depots:
            prob += pulp.lpSum(
                c.demand * x[c.id, d.id] for c in self.customers
            ) <= depot_capacities[d.id]

        # 7. Resolver el modelo de forma silenciosa
        prob.solve(pulp.PULP_CBC_CMD(msg=False))

        # 8. Decodificar el resultado
        clusters = {d.id: [] for d in self.depots}
        for c in self.customers:
            for d in self.depots:
                if pulp.value(x[c.id, d.id]) == 1.0:
                    clusters[d.id].append(c)
                    
        return clusters

    def generate_super_clients(self, cluster: List[CustomerDto], n_clusters: int) -> List[CustomerDto]:
        """
        Aplica Coarse-Graining a un clúster de clientes utilizando Agglomerative Clustering.
        Limitamos estrictamente a n_clusters para asegurar la viabilidad en la QPU de IBM.
        """
        # Si el clúster ya tiene el tamaño objetivo (o menor), no hay nada que comprimir
        if len(cluster) <= n_clusters:
            return cluster

        coords = np.array([[c.x, c.y] for c in cluster])

        # Usamos linkage='complete' (máxima distancia) para forzar agrupaciones compactas
        clustering = AgglomerativeClustering(
            n_clusters=n_clusters, 
            linkage='complete',
            metric='euclidean'
        )
        labels = clustering.fit_predict(coords)

        super_clients = []
        
        for label in set(labels):
            indices = np.where(labels == label)[0]
            internal_customers = [cluster[i] for i in indices]
            
            # Si el algoritmo dejó a un cliente solo (Outlier), no creamos un Súper-Cliente, 
            # simplemente devolvemos el nodo original intacto.
            if len(indices) == 1:
                super_clients.append(internal_customers[0])
                continue
                
            # Si hay varios, fusionamos:
            total_demand = sum(c.demand for c in internal_customers)
            avg_x = sum(c.x for c in internal_customers) / len(indices)
            avg_y = sum(c.y for c in internal_customers) / len(indices)
            
            # Indicamos visualmente que es un nodo comprimido
            composed_id = "SC_" + "_".join(c.id for c in internal_customers)
            
            # Guardamos los nodos internos en el DTO para poder 
            # calcular el C_interno (el TSP local) en la fase de evaluación.
            super_clients.append(CustomerDto(
                id=composed_id, 
                x=round(avg_x, 2), 
                y=round(avg_y, 2), 
                demand=total_demand,
                internal_nodes=internal_customers
            ))

        return super_clients

