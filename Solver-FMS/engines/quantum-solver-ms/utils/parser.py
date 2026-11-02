import math
from typing import List
# Importamos los modelos de Pydantic definidos en domain/models.py
from domain.models import NodeDto, CustomerDto, DepotDto, VehicleDto, RoutingRequest

class CordeauParser:
    """
    Parser para leer instancias del benchmark MD-CVRP (formato Cordeau).
    Convierte el archivo de texto estructurado a los modelos Pydantic.
    Se utilizará exclusivamente de manera local, ya que en producción los 
    datos ingresan nativamente estructurados por FastAPI.
    """
    
    @staticmethod
    def parse_instance(filepath: str, instance_name: str) -> RoutingRequest:
        with open(filepath, 'r') as file:
            lines = file.readlines()
            
        # 1. Leer la primera línea de configuración global
        # Estructura: type m n t
        params = lines[0].strip().split()
        m = int(params[1])  # Cantidad máxima de vehículos por depósito
        n = int(params[2])  # Conteo total de clientes activos
        t = int(params[3])  # Cantidad total de depósitos logísticos
        
        # 2. Leer las líneas de capacidad y restricciones de los depósitos
        depot_lines = lines[1:t+1]
        
        vehicles = []
        vehicle_counter = 1
        
        # Creamos los vehículos asignados a cada depósito
        for d_idx in range(t):
            # Estructura: D (Duración máxima) Q (Capacidad máxima)
            d_params = depot_lines[d_idx].strip().split()
            capacity = int(d_params[1])
            
            # Instanciamos 'm' vehículos por cada depósito
            for _ in range(m):
                vehicles.append(VehicleDto(id=f"V{vehicle_counter}", capacity=capacity))
                vehicle_counter += 1

        # 3. Leer la matriz de Clientes y Depósitos
        node_lines = lines[t+1:]
        
        customers = []
        depots = []
        
        # Las primeras 'n' líneas corresponden a los clientes
        for i in range(n):
            if not node_lines[i].strip():
                continue
            parts = node_lines[i].strip().split()
            
            # Estructura: i x y d q f a list e l
            node_id = parts[0]
            x_coord = float(parts[1])
            y_coord = float(parts[2])
            demand = int(parts[4])
            
            customers.append(CustomerDto(id=node_id, x=x_coord, y=y_coord, demand=demand))
            
        # Las siguientes 't' líneas corresponden a los depósitos físicos
        for i in range(n, n+t):
            if not node_lines[i].strip():
                continue
            parts = node_lines[i].strip().split()
            
            node_id = parts[0]
            x_coord = float(parts[1])
            y_coord = float(parts[2])
            
            depots.append(DepotDto(id=node_id, x=x_coord, y=y_coord))
            
        # 4. Construir y retornar el payload estructurado
        return RoutingRequest(
            instance_name=instance_name,
            depots=depots,
            customers=customers,
            vehicles=vehicles
        )