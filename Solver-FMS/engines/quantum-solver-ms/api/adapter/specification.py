from pydantic import BaseModel
from enum import StrEnum
from abc import ABC, abstractmethod
from typing import Type

class OpenMetada(BaseModel):
    namespace: str
    name: str
    version: str


class OpenSpecificationFactory:

    @staticmethod
    def schema(
        model: type[BaseModel],
        apiVersion: str,
        kind: str,
        metadata: OpenMetada,
    ) -> dict[str, object]:
        
        return {
            "apiVersion": apiVersion,
            "kind": kind,
            "metadata": metadata.model_dump(),
            "spec": {
                "featureValues": {
                    "selector": "only",
                    "values": ["scalar"]
                },
                "optionsSchema": {
                    "type": "object",
                    "properties": (
                        OpenSpecificationFactory
                        .get_options_schema(model)
                    )
                },
                "additionalProperties": False
            }
        }

    @staticmethod
    def get_options_schema(
        model: type[BaseModel]
    ) -> dict[str, object]:

        schema = model.model_json_schema(
            by_alias=True
        )

        required = set(
            schema.get("required", [])
        )

        defs = schema.get("$defs", {})

        result = {}

        for name, prop in schema["properties"].items():

            field_schema = {}

            if "default" in prop:
                field_schema["default"] = prop["default"]

            if "minimum" in prop:
                field_schema["minimum"] = prop["minimum"]

            if "maximum" in prop:
                field_schema["maximum"] = prop["maximum"]

            if "type" in prop:
                field_schema["type"] = prop["type"]

            if "$ref" in prop:

                ref = prop["$ref"].split("/")[-1]

                field_schema["enum"] = defs[ref]["enum"]

            result[name] = field_schema

        return result