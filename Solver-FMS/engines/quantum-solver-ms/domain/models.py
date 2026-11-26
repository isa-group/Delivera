from pydantic import BaseModel, Field, model_validator
from typing import Any, List, Optional
import math

# ==========================================
# MODELOS DE ENTRADA (REQUEST)
# ==========================================

class NodeDto(BaseModel):
    """Clase base matemática para cualquier punto en el plano."""
    id: str = Field(..., description="Identificador único del nodo")
    x: float = Field(..., description="Coordenada X (o Longitud)")
    y: float = Field(..., description="Coordenada Y (o Latitud)")
    matrix_index: Optional[int] = Field(default=None, ge=0, description=(
        "Fila del nodo en RoutingRequest.distance_matrix. En un Súper-Cliente es la "
        "de su cliente más central (medoide)"))

    def euclidean_distance(self, other: 'NodeDto') -> int:
        """Distancia euclidiana redondeada al entero más cercano (Estándar Uchoa)."""
        return round(math.sqrt((self.x - other.x)**2 + (self.y - other.y)**2))

class CustomerDto(NodeDto):
    """Representa a un cliente del CVRP o un Súper-Cliente."""
    demand: int = Field(..., description="Demanda del cliente (qi)")
    
    # Campo opcional para almacenar el sub-grafo interno si este nodo es un Súper-Cliente.
    # Permite reconstruir la ruta sobre los clientes originales después del coarse-graining,
    # sin intentar recuperar sus IDs a partir del nombre compuesto del Súper-Cliente.
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
    parameters: dict[str, Any] = Field(default_factory=dict, description=(
        "Opciones de ejecución. parameters.fallback configura exclusivamente el fallback PyVRP2, "
        "incluidos maxVehicleRepetition, vehicleStrategy, vehicleStrategyConfig, seed y classicalTimeLimit"
    ))
    distance_matrix: Optional[List[List[float]]] = Field(default=None, description=(
        "Matriz de distancias de la petición original (la misma que reciben los demás "
        "algoritmos), indexada por matrix_index. Sin ella se usa la euclídea redondeada"))

    @model_validator(mode="after")
    def _matrix_covers_every_node(self) -> "RoutingRequest":
        # Una matriz vacía equivale a no tenerla (data-service la manda así si no la construye).
        if not self.distance_matrix:
            self.distance_matrix = None
            return self
        size = len(self.distance_matrix)
        for node in [*self.depots, *self.customers]:
            if node.matrix_index is None or node.matrix_index >= size:
                raise ValueError(
                    f"El nodo {node.id} no tiene una fila válida en distance_matrix "
                    f"(matrix_index={node.matrix_index}, tamaño={size})."
                )
        return self

    @property
    def uses_matrix(self) -> bool:
        return self.distance_matrix is not None

    def distance(self, origin: NodeDto, destination: NodeDto) -> float:
        """Distancia entre dos nodos con la métrica de la petición.

        Si la petición trae matriz, se lee de ella, igual que hacen los demás
        algoritmos. Si no, se conserva la euclídea redondeada (benchmarks y v1).
        """
        if self.distance_matrix is not None:
            if origin.matrix_index is None or destination.matrix_index is None:
                raise ValueError(
                    f"Falta matrix_index para medir {origin.id} -> {destination.id} con la matriz."
                )
            return float(self.distance_matrix[origin.matrix_index][destination.matrix_index])
        return float(origin.euclidean_distance(destination))

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
    trip_index: Optional[int] = Field(default=None, ge=1,
        description="Orden del viaje del vehículo en el fallback, sin planificación horaria")

class RoutingResponse(BaseModel):
    request_id: Optional[str] = Field(None, description="ID opcional para trazabilidad")
    instance_name: str = Field(..., description="Nombre de la instancia resuelta")
    solver_used: str = Field(..., description="Métrica de hardware/algoritmo usado")
    execution_time_ms: float = Field(
        ...,
        description="Tiempo de procesamiento en ms, redondeado a tres decimales",
    )
    total_distance: Optional[float] = Field(..., description="Distancia de las rutas completas, o null si el fallback no encuentra solución factible")
    is_feasible: bool = Field(..., description="Indica si la solución respeta la capacidad")
    routes: List[RouteDto] = Field(..., description="Desglose de las rutas calculadas")
    internal_routes: List[RouteDto] = Field(
        default_factory=list,
        description="Campo legado. En las respuestas expandidas queda vacío porque routes ya contiene todos los clientes",
    )
    qubits: Optional[int] = Field(None, description="Qubits del mayor QUBO generado")
    fallback_reason: Optional[str] = Field(None, description="Motivo de derivacion a solver clasico")
    seed: Optional[int] = Field(None, description="Semilla utilizada por el fallback clásico")
    solver_details: Optional[dict[str, Any]] = Field(None,
        description="Detalles del fallback, incluidos el límite de viajes y los viajes adicionales")


# Resolución de referencias circulares para Pydantic V2
CustomerDto.model_rebuild()
