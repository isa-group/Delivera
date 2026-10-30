import unittest


from api.adapter.specification import ParamException, ParamTypes, ParamSpecification
from api.adapter.specification import BooleanFactory

class TestSpecificationBooleanFactory(unittest.TestCase):

    def test_boolean_true(self):
        spec = ParamSpecification(
            name="enabled",
            type=ParamTypes.BOOLEAN
        )

        self.assertTrue(
            BooleanFactory.claim(
                "true",
                spec
            )
        )
    
    def test_boolean_false(self):
        spec = ParamSpecification(
            name="enabled",
            type=ParamTypes.BOOLEAN
        )

        self.assertFalse(
            BooleanFactory.claim(
                "false",
                spec
            )
        )
    
    def test_boolean_invalid(self):
        spec = ParamSpecification(
            name="enabled",
            type=ParamTypes.BOOLEAN
        )

        with self.assertRaises(ParamException):
            BooleanFactory.claim(
                "hola",
                spec
            )