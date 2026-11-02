from pydantic import BaseModel, Field
from typing import List, Optional
import math

# ==========================================
# MODELOS DE ENTRADA (REQUEST)
# ==========================================

class NodeDto(BaseModel):
    """Clase base matemática para cualquier punto en el plano."""
    id: str = Field(..., description="Identificador único del nodo")
    x: float = Field(..., description="Coordenada X (o Longitud)")
    y: float = Field(..., description="Coordenada Y (o Latitud)")

    def euclidean_distance(self, other: 'NodeDto') -> int:
        """Distancia euclidiana redondeada al entero más cercano (Estándar Uchoa)."""
        return round(math.sqrt((self.x - other.x)**2 + (self.y - other.y)**2))

class CustomerDto(NodeDto):
    """Representa a un cliente del CVRP o un Súper-Cliente."""
    demand: int = Field(..., description="Demanda del cliente (qi)")
    
    # Campo opcional para almacenar el sub-grafo interno si este nodo es un Súper-Cliente.
    # Esto es vital para el Coarse-Graining: nos permite calcular a posteriori el C_interno (el TSP local)
    # sin perder la información topológica original del diagrama de Voronoi.
    internal_nodes: Optional[List['CustomerDto']] = Field(default=None, description="Nodos internos si este es un Súper-Cliente")

class DepotDto(NodeDto):
    """Representa el depósito de origen y retorno."""
    pass

class VehicleDto(BaseModel):
    """Características de la flota homogénea o heterogénea."""
    id: str = Field(..., description="Identificador del vehículo")
    capacity: int = Field(..., description="Capacidad máxima de carga (Q)")

class RoutingRequest(BaseModel):
    """Payload principal que FastAPI recibirá tras el particionado de Voronoi."""
    instance_name: str = Field(..., description="Nombre de la instancia de benchmark (ej. X-n101-k25)")
    depots: List[DepotDto] = Field(..., description="Lista de depósitos (Semillas de Voronoi)")
    customers: List[CustomerDto] = Field(..., description="Clientes asignados a este sub-clúster")
    vehicles: List[VehicleDto] = Field(..., description="Vehículos disponibles para este clúster")

    @property
    def total_demand(self) -> int:
        """Calcula la demanda total del clúster."""
        return sum(customer.demand for customer in self.customers)

# ==========================================
# MODELOS DE SALIDA (RESPONSE)
# ==========================================

class RouteDto(BaseModel):
    vehicle_id: str = Field(..., description="ID del vehículo asignado a la ruta")
    stops: List[str] = Field(..., description="Lista ordenada de IDs de nodos visitados")
    distance: float = Field(..., description="Distancia total recorrida en esta ruta")
    load: int = Field(..., description="Carga total transportada en la ruta")

class RoutingResponse(BaseModel):
    request_id: Optional[str] = Field(None, description="ID opcional para trazabilidad")
    instance_name: str = Field(..., description="Nombre de la instancia resuelta")
    solver_used: str = Field(..., description="Métrica de hardware/algoritmo usado")
    execution_time_ms: float = Field(
        ...,
        description="Tiempo de procesamiento en ms, redondeado a tres decimales",
    )
    total_distance: float = Field(..., description="Costo total objetivo de todas las rutas")
    is_feasible: bool = Field(..., description="Indica si la solución respeta la capacidad")
    routes: List[RouteDto] = Field(..., description="Desglose de las rutas calculadas")
    internal_routes: List[RouteDto] = Field(
        default_factory=list,
        description="Rutas internas de los superclientes, si existen",
    )
    qubits: Optional[int] = Field(None, description="Qubits del mayor QUBO generado")
    fallback_reason: Optional[str] = Field(None, description="Motivo de derivacion a solver clasico")


# Resolución de referencias circulares para Pydantic V2
CustomerDto.model_rebuild()
