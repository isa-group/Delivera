"""Reparto de viajes entre vehículos equivalentes, respetando su disponibilidad."""

from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from enum import StrEnum


@dataclass
class VehicleAssignmentContext:
    vehicle_ids: list[str]
    vehicle_position: int
    route_distance: float
    distance_by_vehicle: dict[str, float]
    distance_weight: float
    route_load: int
    load_by_vehicle: dict[str, int]
    load_weight: float
    trip_count_by_vehicle: dict[str, int] = field(default_factory=dict)
    max_trips_per_vehicle: int | None = None


class VehicleAssignmentStrategy(ABC):
    @staticmethod
    @abstractmethod
    def next(ctx: VehicleAssignmentContext, candidates: list[str] | None = None) -> str:
        pass

    @staticmethod
    def candidate_ids(ctx: VehicleAssignmentContext, candidates: list[str] | None) -> list[str]:
        ids = ctx.vehicle_ids if candidates is None else candidates
        if not ids:
            raise ValueError("No hay vehículos candidatos para la estrategia.")
        if not set(ids).issubset(ctx.vehicle_ids):
            raise ValueError("Los candidatos no pertenecen a esta flota.")
        available = [
            v for v in ids
            if ctx.max_trips_per_vehicle is None
            or ctx.trip_count_by_vehicle.get(v, 0) < ctx.max_trips_per_vehicle
        ]
        if not available:
            raise ValueError("No quedan vehículos con viajes disponibles entre los candidatos.")
        return available


class RobinStrategy(VehicleAssignmentStrategy):
    @staticmethod
    def next(ctx: VehicleAssignmentContext, candidates: list[str] | None = None) -> str:
        eligible = set(RobinStrategy.candidate_ids(ctx, candidates))
        # El turno usa el orden completo, aunque algunos camiones estén agotados.
        for offset in range(len(ctx.vehicle_ids)):
            position = (ctx.vehicle_position + offset) % len(ctx.vehicle_ids)
            vehicle_id = ctx.vehicle_ids[position]
            if vehicle_id in eligible:
                return vehicle_id
        raise RuntimeError("No se pudo resolver el turno circular con los candidatos validados.")


class LoadStrategy(VehicleAssignmentStrategy):
    @staticmethod
    def next(ctx: VehicleAssignmentContext, candidates: list[str] | None = None) -> str:
        ids = LoadStrategy.candidate_ids(ctx, candidates)
        return min(ids, key=lambda v: ctx.load_by_vehicle.get(v, 0))


class DistanceStrategy(VehicleAssignmentStrategy):
    @staticmethod
    def next(ctx: VehicleAssignmentContext, candidates: list[str] | None = None) -> str:
        ids = DistanceStrategy.candidate_ids(ctx, candidates)
        return min(ids, key=lambda v: ctx.distance_by_vehicle.get(v, 0))


class HybridBase(VehicleAssignmentStrategy):
    @staticmethod
    def default_distance_weight() -> float:
        return 0.6

    @staticmethod
    def default_load_weight() -> float:
        return 0.4

    @staticmethod
    def normalize_context(
        ctx: VehicleAssignmentContext,
        distance: float,
        load: float,
        min_distance: float,
        max_distance: float,
        min_load: float,
        max_load: float,
    ) -> tuple[float, float, float, float]:
        wd, wl = ctx.distance_weight, ctx.load_weight
        total = wd + wl
        if total <= 0:
            wd = HybridBase.default_distance_weight()
            wl = HybridBase.default_load_weight()
            total = wd + wl
        wd, wl = wd / total, wl / total
        distance_norm = 0 if max_distance <= min_distance else (
            (distance - min_distance) / (max_distance - min_distance)
        )
        load_norm = 0 if max_load <= min_load else (
            (load - min_load) / (max_load - min_load)
        )
        return wd, distance_norm, wl, load_norm

    @staticmethod
    def calculate_goal(
        ctx: VehicleAssignmentContext,
        distance: float,
        load: float,
        min_distance: float,
        max_distance: float,
        min_load: float,
        max_load: float,
    ) -> float:
        wd, distance_norm, wl, load_norm = HybridBase.normalize_context(
            ctx, distance, load, min_distance, max_distance, min_load, max_load,
        )
        return wd * distance_norm + wl * load_norm

    @staticmethod
    def select_vehicle(ctx: VehicleAssignmentContext, candidates: list[str] | None,
                       include_current_route: bool = False) -> str:
        ids = HybridBase.candidate_ids(ctx, candidates)
        route_distance = ctx.route_distance if include_current_route else 0
        route_load = ctx.route_load if include_current_route else 0
        distances = {v: ctx.distance_by_vehicle.get(v, 0) + route_distance for v in ids}
        loads = {v: ctx.load_by_vehicle.get(v, 0) + route_load for v in ids}
        # Mínimos y máximos se calculan juntos, sobre los mismos candidatos.
        min_distance, max_distance = min(distances.values()), max(distances.values())
        min_load, max_load = min(loads.values()), max(loads.values())
        return min(ids, key=lambda v: HybridBase.calculate_goal(
            ctx, distances[v], loads[v], min_distance, max_distance, min_load, max_load,
        ))


class HybridStrategy(HybridBase):
    @staticmethod
    def next(ctx: VehicleAssignmentContext, candidates: list[str] | None = None) -> str:
        return HybridBase.select_vehicle(ctx, candidates)


class FutureHybridStrategy(HybridBase):
    @staticmethod
    def next(ctx: VehicleAssignmentContext, candidates: list[str] | None = None) -> str:
        # Añade la misma distancia y carga de la ruta a todos los candidatos.
        # Esa constante se cancela al recalcular mínimo y máximo en min-max.
        # Por ello equivale a HYBRID. Se conserva el nombre por compatibilidad.
        return HybridBase.select_vehicle(ctx, candidates, include_current_route=True)


class HybridPresets(StrEnum):
    URBAN = "urban"
    INTERURBAN = "interurban"
    HEAVY_GOODS = "heavy_goods"
    BALANCED = "balanced"


class VehicleStrategies(StrEnum):
    ROBIN_ROUND = "robin_round"
    DISTANCE = "distance"
    LOAD = "load"
    HYBRID = "hybrid"
    FUTURE_HYBRID = "future_hybrid"


class VehicleStrategy:
    PRESETS: dict[HybridPresets, tuple[float, float]] = {
        HybridPresets.URBAN: (0.4, 0.6),
        HybridPresets.INTERURBAN: (0.8, 0.2),
        HybridPresets.HEAVY_GOODS: (0.3, 0.7),
        HybridPresets.BALANCED: (0.5, 0.5),
    }
    STRATEGIES: dict[VehicleStrategies, type[VehicleAssignmentStrategy]] = {
        VehicleStrategies.ROBIN_ROUND: RobinStrategy,
        VehicleStrategies.DISTANCE: DistanceStrategy,
        VehicleStrategies.LOAD: LoadStrategy,
        VehicleStrategies.HYBRID: HybridStrategy,
        VehicleStrategies.FUTURE_HYBRID: FutureHybridStrategy,
    }

    @staticmethod
    def get_weights(presets: HybridPresets | None = None) -> tuple[float, float]:
        return VehicleStrategy.PRESETS.get(presets, VehicleStrategy.PRESETS[HybridPresets.BALANCED])

    @staticmethod
    def decide(ctx: VehicleAssignmentContext, strategy: VehicleStrategies) -> str:
        """Filtra candidatos, delega la elección y actualiza el estado del reparto."""
        if ctx.max_trips_per_vehicle is not None and ctx.max_trips_per_vehicle < 1:
            raise ValueError("El máximo de viajes por vehículo debe ser positivo.")
        eligible = [
            v for v in ctx.vehicle_ids
            if ctx.max_trips_per_vehicle is None
            or ctx.trip_count_by_vehicle.get(v, 0) < ctx.max_trips_per_vehicle
        ]
        if not eligible:
            raise ValueError("No quedan vehículos con viajes disponibles en esta flota.")
        executor = VehicleStrategy.STRATEGIES.get(strategy)
        if executor is None:
            raise ValueError(f"Estrategia de asignación desconocida: {strategy}.")
        unused = [v for v in eligible if ctx.trip_count_by_vehicle.get(v, 0) == 0]
        vehicle_id = unused[0] if unused else executor.next(ctx, candidates=eligible)
        ctx.trip_count_by_vehicle[vehicle_id] = ctx.trip_count_by_vehicle.get(vehicle_id, 0) + 1
        ctx.distance_by_vehicle[vehicle_id] = ctx.distance_by_vehicle.get(vehicle_id, 0) + ctx.route_distance
        ctx.load_by_vehicle[vehicle_id] = ctx.load_by_vehicle.get(vehicle_id, 0) + ctx.route_load
        ctx.vehicle_position += 1
        return vehicle_id
