from pydantic import BaseModel, Field
from typing import List, Optional, Any
import math
# ==========================================
# STANDARD INPUT (REQUEST)
# ==========================================

from pydantic import BaseModel, ConfigDict
from services.vehicle_strategy_service import HybridPresets, VehicleStrategies

class BaseApiModel(BaseModel):
    # We can use , e.g matrix_index in the constructor, without this we can only create it with matrixIndex
    model_config = ConfigDict(
        populate_by_name=True
    )


class NodeStd(BaseApiModel):
    id: str = Field(..., description="Unique node identifier")
    lng: float = Field(..., description="Longitude of the node's location (X)")
    lat: float = Field(..., description="Latitude of the node's location (Y)")

    matrix_index: int = Field(..., description="Index of the depot in the distance matrix",  
        alias="matrixIndex" 
    )

    def euclidean_distance(self, other: 'NodeStd') -> int:
        """Distancia euclidiana redondeada al entero más cercano (Estándar Uchoa)."""
        return round(math.sqrt((self.lng - other.lng)**2 + (self.lat - other.lat)**2))



class CustomerStd(NodeStd):
    """It represents MD-CVRP customers."""
    demand: int = Field(..., description="Customer demand (units to deliver)(qi)")
    service_duration: Optional[float] = Field(default=None, ge=0,
        description="""Service time at the customer, in the same units as the maximum route duration. Absent means 0)""",
         alias="serviceDuration"
    )

    # TODO: DELETE THIS?????
    # Campo opcional para almacenar el sub-grafo interno si este nodo es un Súper-Cliente.
    # Esto es vital para el Coarse-Graining: nos permite calcular a posteriori el C_interno (el TSP local)
    # sin perder la información topológica original del diagrama de Voronoi.
    internal_nodes: Optional[List['CustomerStd']] = Field(default=None, description="Nodos internos si este es un Súper-Cliente")


class DepotStd(NodeStd):
    """It represents MD-CVRP depots."""
    
    max_duration: Optional[float] = Field(default=None, ge=0, description=(
        "Maximum duration of a route leaving from this depot, in the same "
        "units as the distance matrix. Absent or 0 means no limit)"),
        alias="maxDuration"
    )

class VehicleStd(BaseApiModel):
    """It represents Fleet features."""
    id: str = Field(..., description="Unique vehicle identifier")
    capacity: int = Field(..., description="Maximum load capacity of the vehicle (Qi)")
    start_depot_id: str = Field(..., description="ID of the home depot assigned to the vehicle", 
        alias="startDepotId"
    )


class PyvrpParameters(BaseApiModel):
    """Opciones del nuevo PyVRP recibidas dentro de request.parameters."""
    model_config = ConfigDict(populate_by_name=True, extra="forbid")

    classical_time_limit: float = Field(default=0.1, alias="classicalTimeLimit",
        ge=0.1, le=300, strict=True, allow_inf_nan=False,
        description="Segundos de búsqueda PyVRP, admite decimales")
    seed: int = Field(default=42, ge=0, le=2**31-1, strict=True,
        description="Semilla de la búsqueda")
    max_vehicle_repetition: int = Field(default=1, alias="maxVehicleRepetition",
        ge=1, le=10, strict=True,
        description="Máximo de viajes por vehículo, incluido el primero")
    vehicle_strategy: VehicleStrategies = Field(default=VehicleStrategies.FUTURE_HYBRID,
        alias="vehicleStrategy", description="Estrategia de reparto entre vehículos compatibles")
    vehicle_strategy_config: HybridPresets = Field(default=HybridPresets.INTERURBAN,
        alias="vehicleStrategyConfig", description="Perfil de pesos de la estrategia híbrida")


class RoutingRequestStd(BaseApiModel):
    """Standard request payload."""
    problem_id: str = Field(..., description="Unique identifier of the problem to solve", alias="problemId")
    depots: List[DepotStd] = Field(..., description="List of available depots")
    customers: List[CustomerStd] = Field(..., description="List of customers to serve")
    vehicles: List[VehicleStd] = Field(..., description="List of available vehicles (optional)")
    distance_matrix: Optional[List[List[float]]] = Field(default=None, description=(
        "Optional distance matrix between all nodes (depots + customers). "
        "If absent, the heterogeneous solver uses Cartesian Euclidean distances. "
        "indexed by matrixIndex. Rows are the origin node and columns the destination node: "
        "distanceMatrix[origin][destination]. It must be square, of size (depots + customers)."),
        alias="distanceMatrix"
    )
    solver_type: str = Field(..., description="Solver type to use", alias="solverType")
    parameters: dict[str, Any] = Field(default_factory=dict, description=(
        "Solver invocation parameters, by name. Those not sent"
        "take their default value. Each solver publishes the ones it accepts, with their meaning, "
        "range and default value, at GET /api/v1/fms/solvers/{type}: GREEDY accepts "
        "none, RANDOM only 'seed', GENETIC fourteen (populationSize, "
        "maxEvaluations, minGenerations, crossoverProbability, intraDepotMutationProbability, "
        "interDepotMutationProbability, elitismCount, tournamentSize, localSearchFrequency, "
        "interDepotFrequency, restartStagnantGenerations, maxRestarts, heuristicSeedRatio and seed) "
        "and ANNEALING twelve (timeLimitMs, maxLevels, calibrationQuantile, initialAcceptanceRate, "
        "finalAcceptanceRate, coolingRate, movesPerTemperatureFactor, interDepotMoveProbability, "
        "depotCandidateRatio, localSearchFrequency, rebalanceFrequency and seed). "
        "'seed' fixes the random generator and makes the run reproducible; if not sent, "
        "the engine draws one and returns it in the response's 'seed' field. "
        "Gateway metadata resolves engine parameters. The heterogeneous PyVRP endpoint accepts "
        "classicalTimeLimit, seed, maxVehicleRepetition, vehicleStrategy and vehicleStrategyConfig. "
        "That endpoint rejects undeclared parameters and invalid values")
    )

    @property
    def total_demand(self) -> int:
        """Calcula la demanda total del clúster."""
        return sum(customer.demand for customer in self.customers)



# ==========================================
# STANDARD OUTPUT (RESPONSE)
# ==========================================

class RouteStd(BaseApiModel):
    vehicle_id: str = Field(..., description="ID of the vehicle driving the route", alias="vehicleId")
    depot_id: str = Field(..., description="ID of the route's home depot", alias="depotId")
    stops: List[str] = Field(..., description="Ordered list of customer IDs (stops) of the route")
    total_distance: float = Field(..., description="Total distance travelled on the route, in the units of the distance matrix", alias="totalDistance")
    total_load: int = Field(..., description="Total load carried on the route (sum of demands)", alias="totalLoad")
    trip_index: Optional[int] = Field(default=None, ge=1, alias="tripIndex",
        description="Orden del viaje de este vehículo, sin planificación horaria")


class SolverDetailsStd(BaseApiModel):
    # Provisional para PyVRP. Coordinar con David su encaje en los detalles de cada solver.
    objective: str = Field(default="DISTANCE",
        description="Criterio optimizado. Actualmente, distancia total")
    distance_weight: float = Field(default=1, alias="distanceWeight",
        description="Peso de la distancia, actualmente 1. No representa un precio por kilómetro")
    time_cost: float = Field(default=0, alias="timeCost",
        description="Componente futuro de coste por tiempo, actualmente desactivado (0)")
    departure_cost: float = Field(default=0, alias="departureCost",
        description="Componente futuro de coste por salida, actualmente desactivado (0)")
    fleet_mode: str = Field(alias="fleetMode",
        description="Modo autorizado: SINGLE_TRIP permite un viaje y MULTI_TRIP permite varios por vehículo")
    max_trips_per_vehicle: int = Field(alias="maxTripsPerVehicle",
        description="Máximo de viajes permitido por camión, incluido el primero")
    trip_count: int = Field(default=0, alias="tripCount",
        description="Número total de viajes de la solución devuelta")
    additional_trips: int = Field(default=0, alias="additionalTrips",
        description="Suma de los viajes posteriores al primero de cada vehículo")
    vehicles_used: int = Field(default=0, alias="vehiclesUsed",
        description="Número de vehículos físicos distintos utilizados")
    schedule_validated: bool = Field(default=False, alias="scheduleValidated",
        description="Indica si se han comprobado horarios y recargas. Actualmente siempre false")
    failure_reason: Optional[str] = Field(default=None, alias="failureReason",
        description="Motivo por el que no se devuelve solución factible, sin certificar imposibilidad")


class RoutingResponseStd(BaseApiModel):
    problem_id: str = Field(..., description="Identifier of the solved problem", alias="problemId")
    solver_used: str = Field(..., description="Solver type used", alias="solverUsed")
    computation_time_ms: float = Field(
        ...,
        description="Computation time in milliseconds",
        alias="computationTimeMs"
    )
    total_cost: Optional[float] = Field(..., description="Sum of route distances, or null when no feasible solution was found", alias="totalCost")
    is_feasible: bool = Field(..., description="A complete solution respecting capacity, depot and trip limits was found", alias="isFeasible")
    seed: int = Field(..., description=(
            "Seed the solver ran with. Sending it back in "
            "'parameters' reproduces exactly this solution. Absent in deterministic "
            "solvers, which do not depend on chance"
        )
    )
    routes: List[RouteStd] = Field(..., description="List of routes that make up the solution")
    solver_details: Optional[SolverDetailsStd] = Field(default=None, alias="solverDetails")
    # TODO: CHANGE
    qubits: Optional[int] = Field(None, description="Qubits del mayor QUBO generado")
    fallback_reason: Optional[str] = Field(None, description="Motivo de derivacion a solver clasico", alias="fallbackReason")


# Resolución de referencias circulares para Pydantic V2
CustomerStd.model_rebuild()
