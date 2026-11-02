from api.adapter.models import CustomerStd, DepotStd, VehicleStd
from domain.models import CustomerDto, DepotDto, VehicleDto


class DtoMapper:

    @staticmethod
    def to_customer(customer: CustomerStd) -> CustomerDto:
        return CustomerDto(
            id=customer.id,
            x=customer.lng,
            y=customer.lat,
            demand=customer.demand,
            internal_nodes=(
                [
                    DtoMapper.to_customer(node)
                    for node in customer.internal_nodes
                ]
                if customer.internal_nodes
                else None
            ),
        )

    @staticmethod
    def to_depot(depot: DepotStd) -> DepotDto:
        return DepotDto(
            id=depot.id,
            x=depot.lng,
            y=depot.lat,
        )

    @staticmethod
    def to_vehicle(vehicle: VehicleStd) -> VehicleDto:
        return VehicleDto(
            id=vehicle.id,
            capacity=vehicle.capacity,
        )
