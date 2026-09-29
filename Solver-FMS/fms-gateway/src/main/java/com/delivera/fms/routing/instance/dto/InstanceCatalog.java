package com.delivera.fms.routing.instance.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "InstanceCatalog",
        description = "Catalogue of standard instances available in the gateway. It is derived from "
                + "the contents of the instance directory, so it grows just by adding files")
public record InstanceCatalog(

        @Schema(description = "Number of instances returned", example = "33")
        int total,

        @ArraySchema(
                arraySchema = @Schema(description = "Instances in the bank, sorted by name"),
                schema = @Schema(implementation = InstanceSummary.class))
        List<InstanceSummary> instances
) {

    public static InstanceCatalog of(List<InstanceSummary> instances) {
        return new InstanceCatalog(instances.size(), instances);
    }
}
