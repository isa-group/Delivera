package com.delivera.fms.routing.config;

import com.delivera.fms.routing.instance.schema.ProblemInstance;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .servers(List.of(
                        new Server().url("/").description("Default Server")
                ))
                .info(new Info()
                        .title("FMS Gateway API")
                        .version("1.0.0")
                        .description("API del Gateway de optimizacion de rutas del Fleet Management System"));
    }

    /**
     * Registra en la seccion de esquemas (tipos) la estructura del fichero de
     * instancia MD-CVRP. Ningun endpoint la recibe/devuelve directamente (el
     * endpoint de instancias solo acepta el nombre del fichero), por lo que se
     * anade con un customizer que se ejecuta despues de que springdoc genere el
     * documento; de lo contrario springdoc descartaria los esquemas no referenciados.
     *
     * Los tipos de deposito y cliente de la instancia se inlinan dentro de
     * ProblemInstance en lugar de registrarse como esquemas de nivel superior,
     * para que solo se vean anidados dentro de la instancia y no generen confusion.
     */
    @Bean
    public OpenApiCustomizer instanceSchemaCustomizer() {
        return openApi -> {
            Components components = openApi.getComponents();
            if (components == null) {
                components = new Components();
                openApi.setComponents(components);
            }
            Map<String, Schema> resolved = ModelConverters.getInstance().readAll(ProblemInstance.class);
            Schema<?> problemInstance = resolved.get("ProblemInstance");
            inlineArrayItems(problemInstance, "depots", resolved.get("InstanceDepot"));
            inlineArrayItems(problemInstance, "customers", resolved.get("InstanceCustomer"));
            components.addSchemas("ProblemInstance", problemInstance);
        };
    }

    /**
     * Sustituye la referencia ($ref) del item de una propiedad de tipo array por
     * el esquema completo, de modo que quede inlinado y el tipo no aparezca como
     * esquema independiente.
     */
    private void inlineArrayItems(Schema<?> parent, String property, Schema<?> itemSchema) {
        if (parent == null || parent.getProperties() == null || itemSchema == null) {
            return;
        }
        Schema<?> arraySchema = (Schema<?>) parent.getProperties().get(property);
        if (arraySchema == null) {
            return;
        }
        itemSchema.set$ref(null);
        arraySchema.setItems(itemSchema);
    }
}
