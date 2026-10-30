import unittest

from services.vehicle_strategy_service import VehicleStrategies
from api.adapter.specification import ParamException, ParamTypes, ParamSpecification
from api.adapter.specification import ParamFactory

class TestSpecificationParamFactory(unittest.TestCase):

    def test_claim_params(self):

        params = {
            "strategy": "distance",
            "weight": "0.5",
            "enabled": "true"
        }

        specifications = [
            ParamSpecification(
                name="strategy",
                type=ParamTypes.ENUM,
                enum_type=VehicleStrategies
            ),
            ParamSpecification(
                name="weight",
                type=ParamTypes.NUMBER
            ),
            ParamSpecification(
                name="enabled",
                type=ParamTypes.BOOLEAN
            )
        ]

        values = ParamFactory.claim_params(
            params,
            specifications
        )

        self.assertEqual(
            values["strategy"],
            VehicleStrategies.DISTANCE
        )

        self.assertEqual(
            values["weight"],
            0.5
        )

        self.assertTrue(
            values["enabled"]
        )