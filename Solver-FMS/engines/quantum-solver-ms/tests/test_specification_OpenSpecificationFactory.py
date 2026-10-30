import unittest

from services.vehicle_strategy_service import VehicleStrategies
from api.adapter.specification import  ParamSpecification, ParamTypes
from api.adapter.specification import OpenSpecificationFactory

class TestSpecificationOpenSpecificationFactory(unittest.TestCase):

    def test_param_schema_enum(self):
        schema = OpenSpecificationFactory.param_schema(
            ParamSpecification(
                name="strategy",
                type=ParamTypes.ENUM,
                enum_type=VehicleStrategies
            )
        )

        self.assertIn("enum", schema)
        self.assertIn("distance", schema["enum"])

    def test_param_schema_number(self):
        schema = OpenSpecificationFactory.param_schema(
            ParamSpecification(
                name="weight",
                type=ParamTypes.NUMBER,
                minimum=0,
                maximum=1
            )
        )

        self.assertEqual(
            schema["type"],
            ParamTypes.NUMBER
        )

        self.assertEqual(
            schema["minimum"],
            0
        )

        self.assertEqual(
            schema["maximum"],
            1
        )
    
    def test_param_schema_default(self):
        schema = OpenSpecificationFactory.param_schema(
            ParamSpecification(
                name="weight",
                type=ParamTypes.NUMBER,
                default=0.5
            )
        )

        self.assertEqual(
            schema["default"],
            0.5
        )
    
    def test_required_fields(self):
        open_spec = {}

        OpenSpecificationFactory.get_options_schema(
            open_spec,
            [
                ParamSpecification(
                    name="strategy",
                    type=ParamTypes.STRING,
                    required=True
                )
            ]
        )

        self.assertEqual(
            open_spec["optionsSchema"]["required"],
            ["strategy"]
        )

    def test_schema_builds_open_spec(self):
        result = OpenSpecificationFactory.schema(
            apiVersion="v1",
            kind="solver"
        )

        self.assertEqual(
            result["apiVersion"],
            "v1"
        )

        self.assertEqual(
            result["kind"],
            "solver"
        )

        self.assertIn(
            "spec",
            result
        )