from dataclasses import dataclass
from typing import Any
from abc import ABC, abstractmethod
from typing import Any
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

    max_distance: float = 0
    max_load: float = 0


class VehicleAssignmentStrategy(ABC):

    @staticmethod
    @abstractmethod
    def next(ctx: VehicleAssignmentContext, **kwargs) -> str:
        pass



class RobinStrategy(VehicleAssignmentStrategy):
    @staticmethod
    def next(ctx: VehicleAssignmentContext, **kwargs) -> str:
        return ctx.vehicle_ids[ 
            ctx.vehicle_position % len(ctx.vehicle_ids)
        ]
   
        
class LoadStrategy(VehicleAssignmentStrategy):
    @staticmethod
    def next(ctx: VehicleAssignmentContext, **kwargs) -> str:
        return min(
            ctx.vehicle_ids, 
            key=lambda vehicle_id: ctx.load_by_vehicle.get(vehicle_id,0)
        ) 

class DistanceStrategy(VehicleAssignmentStrategy):
    @staticmethod
    def next(ctx: VehicleAssignmentContext, **kwargs) -> str:
        return min(
            ctx.vehicle_ids, 
            key=lambda vehicle_id: ctx.distance_by_vehicle.get(vehicle_id,0)
        ) 

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
        **kwargs
    ) -> tuple[float, float, float, float]:
        # USING A MIN-MAX NORMALIZATION

        wd = ctx.distance_weight
        wl = ctx.load_weight
        total = wd + wl

        # FALLBACK
        if total <= 0:
            wd = HybridBase.default_distance_weight()
            wl = HybridBase.default_load_weight()
            total = wd + wl

        # Weights between 0 and 1
        wd = wd / total
        wl = wl / total

        # We use mix-max to avoid one element growing faster than the other
        distance_norm = (
            0
            if (max_distance - min_distance) <= 0
            else (distance - min_distance) / (max_distance - min_distance)
        )

        load_norm = (
            0
            if (max_load - min_load) <= 0
            else (load - min_load) / (max_load - min_load)
        )

        return wd, distance_norm,  wl, load_norm

    @staticmethod
    def calculate_goal(**kwargs):
        wd, distance, wl,load = HybridBase.normalize_context(**kwargs)
        return wd*distance + wl*load


class FutureHybridStrategy(HybridBase):

    @staticmethod
    def calculate_ponderation(
        ctx: VehicleAssignmentContext, 
        vehicle_id: str,
        max_distance: float,
        min_distance: float,
        max_load: float,
        min_load: float,
        ** kwargs
    ):
        distance = ctx.distance_by_vehicle.get(vehicle_id, 0) +ctx.route_distance
        load = ctx.load_by_vehicle.get(vehicle_id, 0) + ctx.route_load

        return FutureHybridStrategy.calculate_goal(
            ctx=ctx,
            load=load,
            distance=distance,
            max_distance= max_distance,
            min_distance= min_distance,
            max_load= max_load,
            min_load=min_load,
            ** kwargs
        )

    @staticmethod
    def next(ctx: VehicleAssignmentContext, **kwargs) -> str:
        max_distance = 0
        min_distance = float('inf')

        max_load = 0
        min_load = float('inf')

        for v_id in ctx.vehicle_ids:
            new_distance = ctx.route_distance + ctx.distance_by_vehicle.get(v_id,0)
        
            max_distance = max(max_distance, new_distance)
            min_distance = min(min_distance, new_distance)

            new_load = ctx.route_load + ctx.load_by_vehicle.get(v_id,0)

            max_load = max(max_load, new_load)
            min_load = min(min_load, new_load)

        return min(
            ctx.vehicle_ids, 
            key=lambda vehicle_id: FutureHybridStrategy.calculate_ponderation(
                ctx=ctx, 
                vehicle_id= vehicle_id,
                max_distance= max_distance,
                min_distance= min_distance,
                max_load= max_load,
                min_load= min_load,
                **kwargs
            )
        )

    
class HybridStrategy(HybridBase):

   
    @staticmethod
    def calculate_ponderation(
        ctx: VehicleAssignmentContext, 
        vehicle_id: str,
        min_distance: float,
        min_load: float,
        ** kwargs
    ):
        distance = ctx.distance_by_vehicle.get(vehicle_id, 0)
        load = ctx.load_by_vehicle.get(vehicle_id, 0)

   
        return HybridStrategy.calculate_goal(
             ctx=ctx,
            load=load,
            distance=distance,
            max_distance= ctx.max_distance,
            min_distance= min_distance,
            max_load= ctx.max_load,
            min_load= min_load,
            ** kwargs
        )

    @staticmethod
    def next(ctx: VehicleAssignmentContext, **kwargs) -> str:
        min_distance = float('inf')
        min_load = float('inf')
        
        for v_id in ctx.vehicle_ids:
            min_distance = min(min_distance,  ctx.distance_by_vehicle.get(v_id,0))

            min_load = min(min_load,  ctx.load_by_vehicle.get(v_id,0))
        
        return min(
            ctx.vehicle_ids, 
            key=lambda vehicle_id: HybridStrategy.calculate_ponderation(
                ctx=ctx, vehicle_id=vehicle_id,
                min_distance= min_distance,
                min_load= min_load,
            )
        )

class HybridPresets(StrEnum):
    # Last-mile distribution.
    # Stopping and delivering is usually more expensive than driving.
    URBAN = "urban"

    # Long-distance routes.
    # Driving dominates operational costs.
    INTERURBAN = "interurban"

    # Pallets, beverages, food distribution, etc.
    # Handling goods dominates route effort.
    HEAVY_GOODS = "heavy_goods"

    # Equal importance for distance and load.
    BALANCED = "balanced"

class VehicleStrategies(StrEnum):
    # Use an equal vehicle distribution 
    ROBIN_ROUND = "robin_round"


    # Use accumulated vehicle distance
    DISTANCE = "distance"

    # Use accumulated vehicle load
    LOAD = "load"

    # Use accumulated vehicle distance and load
    HYBRID = "hybrid"

    # Use accumulated vehicle distance and load + the route distance and load 
    FUTURE_HYBRID = "future_hybrid"



class VehicleStrategy:

    PRESETS: dict[HybridPresets, tuple[float, float]] = {
        HybridPresets.URBAN: (0.4, 0.6),
        HybridPresets.INTERURBAN: (0.8, 0.2),
        HybridPresets.HEAVY_GOODS: (0.3, 0.7),
        HybridPresets.BALANCED: (0.5, 0.5),
    }

    STRATEGIES: dict[VehicleStrategies, VehicleAssignmentStrategy] = {
            VehicleStrategies.ROBIN_ROUND: RobinStrategy,
            VehicleStrategies.DISTANCE: DistanceStrategy,
            VehicleStrategies.LOAD: LoadStrategy,
            VehicleStrategies.HYBRID: HybridStrategy,
            VehicleStrategies.FUTURE_HYBRID: FutureHybridStrategy
    }

    @staticmethod
    def get_weights(presets: HybridPresets | None = None):
        if presets is not None and presets in VehicleStrategy.PRESETS:
            wd, wl = VehicleStrategy.PRESETS[presets] 
        else:
            wd, wl = VehicleStrategy.PRESETS[HybridPresets.BALANCED] 

        return wd, wl




    @staticmethod
    def decide(ctx: VehicleAssignmentContext, strategy: VehicleStrategies, **kwargs):
        
        if ctx.vehicle_position < len(ctx.vehicle_ids):
            vehicle_id: str = ctx.vehicle_ids[ctx.vehicle_position]
        else: 
            
            executor = VehicleStrategy.STRATEGIES.get(strategy, RobinStrategy)

            vehicle_id: str = executor.next(ctx=ctx,**kwargs)

        new_load = ctx.load_by_vehicle.get(vehicle_id, 0) + ctx.route_load
        new_distance = ctx.distance_by_vehicle.get(vehicle_id, 0) + ctx.route_distance

        ctx.distance_by_vehicle[vehicle_id] = new_distance 
        ctx.max_distance = max(ctx.max_distance, new_distance)
       

        
        ctx.load_by_vehicle[vehicle_id] =  new_load 

        ctx.max_load = max(ctx.max_load, new_load)


        return vehicle_id 
    
    