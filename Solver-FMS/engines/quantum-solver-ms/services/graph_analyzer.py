import numpy as np
from typing import Dict, Any

from domain.models import RoutingRequest

class GraphAnalyzer:
    """
    Analizador topológico pre-GAP. Genera las 'features' requeridas por el Orquestador 
    de Delivera (Binding System) para decidir si derivar el problema a la vía cuántica o clásica.
    """

    @staticmethod
    def extract_features(request: RoutingRequest) -> Dict[str, Any]:
        
        # ---------------------------------------------------------
        # CARACTERÍSTICAS BÁSICAS DEL GRAFO Y FLOTA
        # ---------------------------------------------------------
        num_customers = len(request.customers)
        num_depots = len(request.depots)
        num_vehicles = len(request.vehicles)
        
        total_demand = request.total_demand
        
        capacities = [v.capacity for v in request.vehicles]
        total_capacity = sum(capacities) if capacities else 1
        avg_capacity = np.mean(capacities) if capacities else 1
        
        # ---------------------------------------------------------
        # 1. GRANULARIDAD DE DEMANDA PROMEDIO (G)
        # ---------------------------------------------------------
        # Relación directa entre la demanda de un cliente medio y la capacidad de un vehículo medio.
        avg_demand = total_demand / num_customers if num_customers > 0 else 0
        demand_granularity = avg_demand / avg_capacity

        # ---------------------------------------------------------
        # 2. TENSIÓN DE CAPACIDAD (Capacity Tightness - Tc)
        # ---------------------------------------------------------
        # Tasa de saturación global de la flota. Evalúa el nivel de asfixia del Bin Packing.
        capacity_tightness = total_demand / total_capacity if total_capacity > 0 else 1.0

        # ---------------------------------------------------------
        # 3. ÍNDICE DE COMPRESIBILIDAD ESPACIAL
        # ---------------------------------------------------------
        avg_dist_to_depot = 0.0
        avg_nn_distance = 0.0
        spatial_compressibility_index = 0.0
        
        if num_customers > 1 and request.depots:
            # Distancia media de cada cliente a su depósito más próximo
            min_depot_dists = [
                min(c.euclidean_distance(d) for d in request.depots) 
                for c in request.customers
            ]
            avg_dist_to_depot = np.mean(min_depot_dists)
            
            # Distancia media de cada cliente a su vecino más cercano (Nearest Neighbor)
            nn_distances = []
            for c1 in request.customers:
                dists = [c1.euclidean_distance(c2) for c2 in request.customers if c1.id != c2.id]
                nn_distances.append(min(dists))
            avg_nn_distance = np.mean(nn_distances)
            
            # Cociente entre la dispersión local y la dispersión global
            if avg_dist_to_depot > 0:
                spatial_compressibility_index = avg_nn_distance / avg_dist_to_depot

        # ---------------------------------------------------------
        # CONSTRUCCIÓN DEL VECTOR DE FEATURES
        # ---------------------------------------------------------
        return {
            "num_customers": num_customers,
            "num_depots": num_depots,
            "total_vehicles": num_vehicles,
            "total_demand": total_demand,
            "total_capacity": total_capacity,
            "avg_capacity": round(avg_capacity, 2),
            "avg_demand": round(avg_demand, 2),
            "max_demand_ratio": round(
                max(
                    (customer.demand / avg_capacity for customer in request.customers),
                    default=0,
                ),
                4,
            ),
            "demand_granularity": round(demand_granularity, 4),
            "capacity_tightness": round(capacity_tightness, 4),
            "avg_dist_to_depot": round(avg_dist_to_depot, 2),
            "avg_nearest_neighbor_dist": round(avg_nn_distance, 2),
            "spatial_compressibility_index": round(spatial_compressibility_index, 4)
        }

    @staticmethod
    def evaluate_quantum_viability(features: Dict[str, Any]) -> bool:
        """
        Reglas de exclusión dura para el Orquestador. Evalúa si el problema es apto 
        para la vía cuántica o debe derivarse inmediatamente al motor clásico.
        """
        # 1. Granularidad (Umbral ajustado a 0.6)
        # Si la demanda media ronda el 60%, agrupar dos clientes generaría un paquete del 120%, 
        # detonando infactibilidad insalvable en el modelo CVRP.
        if features["demand_granularity"] >= 0.6:
            print(f"demand_granularity ({features['demand_granularity']}) exceeds threshold (0.6).")
            return False
            
        # 2. Tensión de Capacidad (Umbral 0.90)
        # Si la flota está saturada por encima del 90%, el problema exige una combinatoria perfecta. 
        # La pérdida de granularidad al agrupar arruinará el Bin Packing.
        if features["capacity_tightness"] > 0.90:
            print(f"capacity_tightness ({features['capacity_tightness']}) exceeds threshold (0.90).")
            return False
            
        # 3. Compresibilidad Espacial (Umbral 1.2)
        # Si los clientes están muy dispersos (> 1.2), el baricentro geométrico no será representativo 
        # y falseará las rutas por violación de la Desigualdad Triangular.
        if features["spatial_compressibility_index"] > 1.2:
            print(f"spatial_compressibility_index ({features['spatial_compressibility_index']}) exceeds threshold (1.2).")
            return False
        
        print("All quantum viability checks passed.")
        return True