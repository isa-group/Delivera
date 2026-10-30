import unittest

from services.vehicle_strategy_service import VehicleStrategies
from api.adapter.specification import ParamException, ParamTypes, ParamSpecification
from api.adapter.specification import EnumFactory

class TestSpecificationEnumFactory(unittest.TestCase):

    def test_enum_factory_valid(self):
        spec = ParamSpecification(
            name="strategy",
            type=ParamTypes.ENUM,
            enum_type=VehicleStrategies
        )

        value = EnumFactory.claim(
            "distance",
            spec
        )

        self.assertEqual(
            value,
            VehicleStrategies.DISTANCE
        )
    
    def test_enum_factory_invalid(self):
        spec = ParamSpecification(
            name="strategy",
            type=ParamTypes.ENUM,
            enum_type=VehicleStrategies
        )

        with self.assertRaises(ParamException):
            EnumFactory.claim(
                "pepito",
                spec
            )

    def test_enum_factory_requires_enum_type(self):
        spec = ParamSpecification(
            name="strategy",
            type=ParamTypes.ENUM
        )

        with self.assertRaises(ParamException):
            EnumFactory.claim(
                "distance",
                spec
            )