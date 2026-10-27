from pydantic import BaseModel, Field
from typing import List, Optional, Any
import math
# ==========================================
# STANDARD INPUT (REQUEST)
# ==========================================

from pydantic import BaseModel, ConfigDict

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
    service_duration: float = Field(..., 
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
    
    max_duration: float = Field(..., description=(
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


class RoutingRequestStd(BaseApiModel):
    """Standard request payload."""
    problem_id: str = Field(..., description="Unique identifier of the problem to solve", alias="problemId")
    depots: List[DepotStd] = Field(..., description="List of available depots")
    customers: List[CustomerStd] = Field(..., description="List of customers to serve")
    vehicles: List[VehicleStd] = Field(..., description="List of available vehicles (optional)")
    distance_matrix: List[List[float]] = Field(..., description=(
        "istance matrix between all nodes (depots + customers), " 
        "indexed by matrixIndex. Rows are the origin node and columns the destination node: "
        "distanceMatrix[origin][destination]. It must be square, of size (depots + customers)."),
        alias="distanceMatrix"
    )
    solver_type: str = Field(..., description="Solver type to use", alias="solverType")
    parameters: dict[str, Any] = Field(..., description=(
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
        "An undeclared parameter is ignored with a warning; one out of range rejects the request")
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


class RoutingResponseStd(BaseApiModel):
    problem_id: str = Field(..., description="Identifier of the solved problem", alias="problemId")
    solver_used: str = Field(..., description="Solver type used", alias="solverUsed")
    computation_time_ms: float = Field(
        ...,
        description="Computation time in milliseconds",
        alias="computationTimeMs"
    )
    total_cost: float = Field(..., description="Total cost of the solution (sum of the distances of all routes)", alias="totalCost")
    is_feasible: bool = Field(..., description="Solution respects  the capacity", alias="isFeasible")
    seed: int = Field(..., description=(
            "Seed the solver ran with. Sending it back in "
            "'parameters' reproduces exactly this solution. Absent in deterministic "
            "solvers, which do not depend on chance"
        )
    )
    routes: List[RouteStd] = Field(..., description="List of routes that make up the solution")
    # TODO: CHANGE
    qubits: Optional[int] = Field(None, description="Qubits del mayor QUBO generado")
    fallback_reason: Optional[str] = Field(None, description="Motivo de derivacion a solver clasico", alias="fallbackReason")


# Resolución de referencias circulares para Pydantic V2
CustomerStd.model_rebuild()
