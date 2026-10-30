import unittest


from api.adapter.specification import ParamException, ParamTypes, ParamSpecification
from api.adapter.specification import IntegerFactory

class TestSpecificationIntegerFactory(unittest.TestCase):

    def test_integer_factory(self):
        spec = ParamSpecification(
            name="repetitions",
            type=ParamTypes.INTEGER
        )

        value = IntegerFactory.claim(
            "5",
            spec
        )

        self.assertEqual(value, 5)
    
    def test_integer_factory_float_str(self):
        spec = ParamSpecification(
            name="weight",
            type=ParamTypes.INTEGER,
        )

        with self.assertRaises(ParamException):
            IntegerFactory.claim(
                "0.5",
                spec
            )
    
    def test_integer_factory_float(self):
        spec = ParamSpecification(
            name="weight",
            type=ParamTypes.INTEGER,
        )

        with self.assertRaises(ParamException):
            IntegerFactory.claim(
                0.5,
                spec
            )

    


    
    def test_integer_factory_minimum(self):
        spec = ParamSpecification(
            name="weight",
            type=ParamTypes.INTEGER,
            minimum=0
        )

        with self.assertRaises(ParamException):
            IntegerFactory.claim(
                -1,
                spec
            )
    
    def test_integer_factory_maximun(self):
        spec = ParamSpecification(
            name="weight",
            type=ParamTypes.INTEGER,
            maximum=10
        )

        with self.assertRaises(ParamException):
            IntegerFactory.claim(
                11,
                spec
            )