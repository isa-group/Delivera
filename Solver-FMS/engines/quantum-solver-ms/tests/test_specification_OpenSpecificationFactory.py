import unittest

from api.adapter.specification import (
    OpenMetada,
    OpenSpecificationFactory,
)

from api.adapter.models import (
    PyvrpParameters,
)
from services.vehicle_strategy_service import (
    VehicleStrategies,
    HybridPresets,
)


class TestOpenSpecificationFactory(unittest.TestCase):

    def test_schema_builds_open_spec_from_model(self):

        metadata = OpenMetada(
            namespace="delivera.solvers",
            name="hgs-adc",
            version="1.0.0"
        )

        result = OpenSpecificationFactory.schema(
            model=PyvrpParameters,
            apiVersion="v1",
            kind="Engine",
            metadata=metadata,
        )

        self.assertEqual(
            result["apiVersion"],
            "v1"
        )

        self.assertEqual(
            result["kind"],
            "Engine"
        )

        self.assertEqual(
            result["metadata"],
            metadata.model_dump()
        )

        self.assertIn(
            "spec",
            result
        )
    
    def test_number_properties(self):

        schema = OpenSpecificationFactory.get_options_schema(
            PyvrpParameters
        )

        classical = schema["classicalTimeLimit"]

        self.assertEqual(
            classical["type"],
            "number"
        )

        self.assertEqual(
            classical["default"],
            0.1
        )

        self.assertEqual(
            classical["minimum"],
            0.1
        )

        self.assertEqual(
            classical["maximum"],
            300
        )
    
    def test_integer_properties(self):

        schema = OpenSpecificationFactory.get_options_schema(
            PyvrpParameters
        )

        seed = schema["seed"]

        self.assertEqual(
            seed["type"],
            "integer"
        )

        self.assertEqual(
            seed["default"],
            42
        )

        self.assertEqual(
            seed["minimum"],
            0
        )

        self.assertEqual(
            seed["maximum"],
            2147483647
        )
    
    def test_vehicle_strategy_enum(self):

        schema = OpenSpecificationFactory.get_options_schema(
            PyvrpParameters
        )

        strategy = schema["vehicleStrategy"]

        self.assertEqual(
            strategy["default"],
            VehicleStrategies.FUTURE_HYBRID.value
        )

        self.assertEqual(
            strategy["enum"],
            [
                e.value
                for e in VehicleStrategies
            ]
        )
    
    def test_vehicle_strategy_config_enum(self):

        schema = OpenSpecificationFactory.get_options_schema(
            PyvrpParameters
        )

        preset = schema["vehicleStrategyConfig"]

        self.assertEqual(
            preset["default"],
            HybridPresets.INTERURBAN.value
        )

        self.assertEqual(
            preset["enum"],
            [
                e.value
                for e in HybridPresets
            ]
        )
    
    def test_complete_options_schema(self):

        schema = OpenSpecificationFactory.get_options_schema(
            PyvrpParameters
        )

        self.assertIn(
            "classicalTimeLimit",
            schema
        )

        self.assertIn(
            "seed",
            schema
        )

        self.assertIn(
            "maxVehicleRepetition",
            schema
        )

        self.assertIn(
            "vehicleStrategy",
            schema
        )

        self.assertIn(
            "vehicleStrategyConfig",
            schema
        )