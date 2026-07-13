package com.delivera.fms.routing.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "Solicitud de resolucion de un problema de ruteo de vehiculos")
public record RoutingRequest(
        @Schema(description = "Identificador unico del problema a resolver", example = "PROBLEM-001")
        @NotBlank String problemId,

        @ArraySchema(schema = @Schema(description = "Lista de depositos disponibles"))
        @NotEmpty @Valid List<DepotDto> depots,

        @ArraySchema(schema = @Schema(description = "Lista de clientes a atender"))
        @NotEmpty @Valid List<CustomerDto> customers,

        @ArraySchema(schema = @Schema(description = "Lista de vehiculos disponibles (opcional)"))
        @Valid List<VehicleDto> vehicles,

        @Schema(description = "Matriz de distancias entre todos los nodos (depositos + clientes)")
        @NotNull double[][] distanceMatrix,

        @Schema(description = "Tipo de solver a utilizar para la resolucion")
        @NotNull TypeSolver solverType
) {
}
