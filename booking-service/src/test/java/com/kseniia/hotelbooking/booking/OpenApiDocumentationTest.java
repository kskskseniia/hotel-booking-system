package com.kseniia.hotelbooking.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "jwt.secret=test-only-jwt-secret-32-bytes-minimum!"
})
@AutoConfigureMockMvc
class OpenApiDocumentationTest {

    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void publishesAuthenticationOperations() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/user/register'].post").exists())
                .andExpect(jsonPath("$.paths['/user/auth'].post").exists())
                .andExpect(jsonPath("$.paths['/user'].post").exists())
                .andExpect(jsonPath("$.paths['/user'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/user'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/user'].patch.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/user'].patch.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/user'].delete.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/user'].delete.requestBody.content['application/json']").exists())
                .andExpect(jsonPath("$.paths['/user'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/user'].delete.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/user'].delete.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/user'].delete.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/user'].delete.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/user'].delete.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/user'].delete.responses['503']").exists())
                .andExpect(jsonPath("$.paths['/user'].delete.responses['409'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ErrorResponse"))
                .andExpect(jsonPath("$.paths['/booking'].post.responses['400'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ErrorResponse"))
                .andExpect(jsonPath("$.paths['/booking'].post.responses['401'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ErrorResponse"))
                .andExpect(jsonPath("$.paths['/booking'].post.responses['403'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ErrorResponse"))
                .andExpect(jsonPath("$.paths['/booking'].post.responses['500'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ErrorResponse"))
                .andExpect(jsonPath("$.paths['/booking'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/booking'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/booking/{bookingId}'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/booking/{bookingId}'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/booking/{bookingId}'].delete.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/booking/{bookingId}'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/bookings'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/bookings'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/bookings/rooms/{roomId}'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/bookings/rooms/{roomId}'].get.responses['200']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.required").isArray())
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.timestamp").exists())
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.status").exists())
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.error").exists())
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.message").exists())
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.path").exists());
    }

    @Test
    void documentsEveryOperationWithSummaryAndDescription() throws Exception {
        String openApi = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertEveryOperationIsDocumented(objectMapper.readTree(openApi).path("paths"));
    }

    private void assertEveryOperationIsDocumented(JsonNode paths) {
        Iterator<Map.Entry<String, JsonNode>> pathEntries = paths.fields();
        while (pathEntries.hasNext()) {
            Map.Entry<String, JsonNode> path = pathEntries.next();
            Iterator<Map.Entry<String, JsonNode>> operations = path.getValue().fields();
            while (operations.hasNext()) {
                Map.Entry<String, JsonNode> operation = operations.next();
                if (HTTP_METHODS.contains(operation.getKey())) {
                    assertThat(operation.getValue().path("summary").asText())
                            .as("%s %s summary", operation.getKey(), path.getKey())
                            .isNotBlank();
                    assertThat(operation.getValue().path("description").asText())
                            .as("%s %s description", operation.getKey(), path.getKey())
                            .isNotBlank();
                }
            }
        }
    }
}
