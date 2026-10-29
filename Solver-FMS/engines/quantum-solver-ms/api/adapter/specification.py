from pydantic import BaseModel
from enum import StrEnum
from abc import ABC, abstractmethod
from typing import Type


class ParamException(Exception):

    def __init__(
        self,
        message: str,
        param_name: str | None = None,
        status_code: int = 400,
    ):
        self.message = message
        self.param_name = param_name
        self.status_code = status_code

        super().__init__(message)

class ParamTypes(StrEnum):
    # Integer type.
    INTEGER = "integer"
    # Float type.
    NUMBER = "number"
    # Enum type. requires an options set
    ENUM = "enum"
    # Strings
    STRING = "string"
    # bool
    BOOLEAN = "boolean"


class ParamSpecification(BaseModel):
    # Parameter name
    name: str

    # Parameter type
    type: ParamTypes

    # Minimum value (numbers)
    minimum: object | None = None

    # Maximum value (numbers)
    maximum: object | None = None

    # Default value
    default: object | None = None

    # If True, user must explicitly provide the value
    required: bool = False

    # Enum type used, example: ParamTypes
    enum_type: Type[StrEnum] | None = None

class TypeFactory(ABC):

    @staticmethod
    def resolve_value(
        param: object,
        specification: ParamSpecification,
    ) -> object:

        if param is None and specification.required:
            raise ParamException(
                f"Required value for parameter "
                f"'{specification.name}'"
            )

        if param is None:
            param = specification.default

        return param

    @staticmethod
    def validate_min_max(
        value: int | float,
        specification: ParamSpecification,
    ) -> None:

        if (
            specification.minimum is not None
            and value < specification.minimum
        ):
            raise ParamException(
                f"Parameter '{specification.name}' "
                f"must be >= {specification.minimum}"
            )

        if (
            specification.maximum is not None
            and value > specification.maximum
        ):
            raise ParamException(
                f"Parameter '{specification.name}' "
                f"must be <= {specification.maximum}"
            )

    @staticmethod
    @abstractmethod
    def claim(
        entry: object,
        specification: ParamSpecification,
    ) -> object:
        pass

class EnumFactory(TypeFactory):

    @staticmethod
    def claim(
        param: object,
        specification: ParamSpecification,
    ) -> object:

        param = TypeFactory.resolve_value(
            param,
            specification,
        )

        if specification.enum_type is None:
            raise ParamException(
                f"Parameter '{specification.name}' "
                "requires an enum type"
            )

        try:
            return specification.enum_type(param)

        except ValueError:
            raise ParamException(
                f"Invalid value '{param}' "
                f"for parameter '{specification.name}'"
            )

class NumberFactory(TypeFactory):

    @staticmethod
    def claim(
        param: object,
        specification: ParamSpecification,
    ) -> float:

        param = TypeFactory.resolve_value(
            param,
            specification,
        )

        try:
            value = float(param)

        except (TypeError, ValueError):
            raise ParamException(
                f"Parameter '{specification.name}' "
                "must be a number"
            )

        TypeFactory.validate_min_max(
            value,
            specification,
        )

        return value
        
class IntegerFactory(TypeFactory):

    @staticmethod
    def claim(
        param: object,
        specification: ParamSpecification,
    ) -> int:

        param = TypeFactory.resolve_value(
            param,
            specification,
        )

        try:
            value = int(param)

        except (TypeError, ValueError):
            raise ParamException(
                f"Parameter '{specification.name}' "
                "must be an integer"
            )

        TypeFactory.validate_min_max(
            value,
            specification,
        )

        return value      

class StringFactory(TypeFactory):

    @staticmethod
    def claim(
        param: object,
        specification: ParamSpecification,
    ) -> str:

        param = TypeFactory.resolve_value(
            param,
            specification,
        )
        if param is None:
            return None

        return str(param)     

class BooleanFactory(TypeFactory):

    TRUE_VALUES = {
        True,
        "true",
        "True",
        "TRUE",
        "1",
        1,
        "yes",
        "YES",
    }

    FALSE_VALUES = {
        False,
        "false",
        "False",
        "FALSE",
        "0",
        0,
        "no",
        "NO",
    }

    @staticmethod
    def claim(
        param: object,
        specification: ParamSpecification,
    ) -> bool:

        param = TypeFactory.resolve_value(
            param,
            specification,
        )

        if param in BooleanFactory.TRUE_VALUES:
            return True

        if param in BooleanFactory.FALSE_VALUES:
            return False

        raise ParamException(
            f"Parameter '{specification.name}' "
            "must be a boolean"
        )

class ParamFactory:

    TYPES: dict[ParamTypes,Type[TypeFactory]] = {
        ParamTypes.ENUM: EnumFactory,
        ParamTypes.STRING: StringFactory,
        ParamTypes.INTEGER: IntegerFactory,
        ParamTypes.NUMBER: NumberFactory,
        ParamTypes.BOOLEAN: BooleanFactory
    }

    @staticmethod
    def claim_params(
        params: dict[str, object], 
        specifications: list[ParamSpecification]
    ) -> dict[str, object]:
        values = {}
        for specification in specifications:
            param = params.get(specification.name, None)
            if specification.type not in ParamFactory.TYPES:
                 raise ParamException(
                    f"Parameter '{specification.name}' "
                    f"has an invalid type '{specification.type}'"
                )
            else:
                executor = ParamFactory.TYPES[specification.type]

                value = executor.claim(param, specification)

                values[specification.name] = value
        return values

class OpenMetada(BaseModel):
    namespace: str
    name: str
    version: str



class OpenSpecificationFactory:

    @staticmethod
    def schema(
        apiVersion: str = "",
        kind: str = "",
        metadata: OpenMetada | dict = {},
        param_specifications: list[ParamSpecification] = [],
        **kwargs
    ) -> dict[str, object]:
        
        open_spec = {
            "apiVersion": apiVersion,
            "kind":kind,
            "metadata": metadata,
            "spec":{
                "featureValues": {"selector": "only", "values": ["scalar"]},
            }
        }
        OpenSpecificationFactory.get_options_schema(open_spec["spec"], param_specifications)
        return open_spec


    @staticmethod
    def get_options_schema(
        open_spec: dict[str, object], 
        param_specifications: list[ParamSpecification],
        **kwargs
    ):
        options_schema = {}
        options_schema["type"] = "object"
        properties = {}
        required = []
        for param in param_specifications:
            if not isinstance(param, ParamSpecification):
                continue
            if param.required:
                required.append(param.name)
            properties[param.name] = OpenSpecificationFactory.param_schema(param)
        options_schema["properties"] = properties

        if len(required) > 0:
            options_schema["required"] = required
            
        # TODO: ASK AND CONSIDER additionalProperties
        options_schema["additionalProperties"] = False
        open_spec["optionsSchema"] = options_schema

    @staticmethod
    def param_schema(
        specification: ParamSpecification
    ) -> dict[str, object]:

        schema = {
            #"type": specification.type, TODO: DELETE?
            #"required": specification.required, TODO: DELETE?
            "default": specification.default,
        }

        is_enum = specification.type == ParamTypes.ENUM
        enum_types = specification.enum_type
        enum_types = specification.enum_type if enum_types  is not None else []

        # TODO: Enum types
        if not is_enum:
            schema["type"] = specification.type
        elif is_enum:
            schema["enum"] = [
                e.value
                for e in enum_types
            ]

        if specification.minimum is not None:
            schema["minimum"] = specification.minimum

        if specification.maximum is not None:
            schema["maximum"] = specification.maximum
        '''
        if specification.enum_type is not None:
            schema["options"] = [
                e.value
                for e in specification.enum_type
            ]
        '''

        return schema