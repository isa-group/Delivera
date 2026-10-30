import unittest


from api.adapter.specification import ParamException, ParamTypes, ParamSpecification
from api.adapter.specification import NumberFactory

class TestSpecificationNumberFactory(unittest.TestCase):

    def test_number_factory(self):
        spec = ParamSpecification(
            name="weight",
            type=ParamTypes.NUMBER
        )

        value = NumberFactory.claim(
            "0.5",
            spec
        )

        self.assertEqual(value, 0.5)
    
    def test_number_factory_minimum(self):
        spec = ParamSpecification(
            name="weight",
            type=ParamTypes.NUMBER,
            minimum=0
        )

        with self.assertRaises(ParamException):
            NumberFactory.claim(
                -1,
                spec
            )
    
    def test_number_factory_maximun(self):
        spec = ParamSpecification(
            name="weight",
            type=ParamTypes.NUMBER,
            maximum=10
        )

        with self.assertRaises(ParamException):
            NumberFactory.claim(
                11.5,
                spec
            )