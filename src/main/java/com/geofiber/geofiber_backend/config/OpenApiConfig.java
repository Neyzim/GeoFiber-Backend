package com.geofiber.geofiber_backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/** Documentação OpenAPI. Swagger UI em {@code /swagger-ui.html}, spec em {@code /v3/api-docs}. */
@Configuration
public class OpenApiConfig {
    private static final Map<String, String> ERROR_RESPONSES = Map.of(
        "400", "Payload inválido ou regra de entrada violada (ProblemDetail; validação traz `errors`)",
        "404", "Recurso inexistente ou que não pertence ao recurso pai (ProblemDetail)",
        "409", "Conflito com dados existentes, como ligação ocupada ou dependência (ProblemDetail)"
    );

    @Bean
    public OpenAPI geofiberOpenApi() {
        return new OpenAPI().info(new Info()
            .title("GeoFiber API")
            .version("v1")
            .description("""
                API open source para cadastrar e documentar redes FTTH: OLTs, cabos, tubos e fibras, \
                caixas (CTO/CEO/DIO), bandejas, emendas, conexões, splitters, portas de atendimento e clientes.

                Coordenadas em WGS 84 (longitude, latitude). A ponta A de uma fibra fica do lado da OLT \
                e a B do lado do cliente. Toda ligação é explícita: um cabo só pode ter fibras ligadas \
                nos elementos da sua lista (`/cables/{cableId}/attachments`). Veja também o `API.md`."""));
    }

    /** Toda operação documenta as respostas de erro padronizadas da API. */
    @Bean
    public OpenApiCustomizer problemDetailResponses() {
        return openApi -> {
            openApi.getComponents().addSchemas("ProblemDetail", problemDetailSchema());
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
                ApiResponses responses = operation.getResponses();
                ERROR_RESPONSES.forEach((status, description) -> {
                    if (!responses.containsKey(status)) {
                        responses.addApiResponse(status, new ApiResponse().description(description).content(
                            new Content().addMediaType("application/problem+json",
                                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ProblemDetail")))));
                    }
                });
            }));
        };
    }

    private static Schema<?> problemDetailSchema() {
        return new ObjectSchema()
            .description("Erro no formato RFC 9457")
            .addProperty("type", new StringSchema())
            .addProperty("title", new StringSchema().example("Infrastructure data conflict"))
            .addProperty("status", new IntegerSchema().example(409))
            .addProperty("detail", new StringSchema().example("Fiber end B is not available"))
            .addProperty("instance", new StringSchema())
            .addProperty("errors", new ArraySchema().description("Só em erros de validação")
                .items(new ObjectSchema()
                    .addProperty("field", new StringSchema().example("name"))
                    .addProperty("message", new StringSchema().example("must not be blank"))));
    }
}
