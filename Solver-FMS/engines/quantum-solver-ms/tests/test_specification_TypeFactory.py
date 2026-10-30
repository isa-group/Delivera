import unittest

from api.adapter.specification import ParamException, ParamTypes, ParamSpecification
from api.adapter.specification import TypeFactory

class TestSpecificationTypeFactory(unittest.TestCase):

    def test_resolve_value_required(self):
        spec = ParamSpecification(
            name="test",
            type=ParamTypes.STRING,
            required=True
        )

        with self.assertRaises(ParamException):
            TypeFactory.resolve_value(None, spec)
    
    def test_resolve_value_default(self):
        spec = ParamSpecification(
            name="test",
            type=ParamTypes.STRING,
            default="hello"
        )

        value = TypeFactory.resolve_value(None, spec)

        self.assertEqual(value, "hello")
    
    def test_validate_minimum(self):
        spec = ParamSpecification(
            name="weight",
            type=ParamTypes.NUMBER,
            minimum=0
        )

        with self.assertRaises(ParamException):
            TypeFactory.validate_min_max(-1, spec)
    
    def test_validate_maximum(self):
        spec = ParamSpecification(
            name="weight",
            type=ParamTypes.NUMBER,
            maximum=10
        )

        with self.assertRaises(ParamException):
            TypeFactory.validate_min_max(11, spec)