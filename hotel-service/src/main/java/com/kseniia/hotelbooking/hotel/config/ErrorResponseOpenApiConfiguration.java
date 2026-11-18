package com.kseniia.hotelbooking.hotel.config;

import com.kseniia.hotelbooking.hotel.dto.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ErrorResponseOpenApiConfiguration {

    private static final String ERROR_SCHEMA = "#/components/schemas/ErrorResponse";

    @Bean
    OpenApiCustomizer errorResponseOpenApiCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            ModelConverters.getInstance().read(ErrorResponse.class)
                    .forEach(openApi.getComponents()::addSchemas);

            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
                if (operation.getRequestBody() != null) {
                    addResponse(operation, "400", "Invalid request");
                }
                addResponse(operation, "401", "Authentication required");
                addResponse(operation, "403", "Access is denied");
                addResponse(operation, "500", "Internal server error");
                operation.getResponses().forEach((code, response) -> {
                    if (code.startsWith("4") || code.startsWith("5")) {
                        documentBody(response);
                    }
                });
            }));
        };
    }

    private void addResponse(Operation operation, String code, String description) {
        operation.getResponses().putIfAbsent(code, new ApiResponse().description(description));
    }

    private void documentBody(ApiResponse response) {
        if (response.getContent() == null) {
            response.setContent(new Content());
        }
        response.getContent().addMediaType(
                org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                new MediaType().schema(new Schema<>().$ref(ERROR_SCHEMA))
        );
    }
}