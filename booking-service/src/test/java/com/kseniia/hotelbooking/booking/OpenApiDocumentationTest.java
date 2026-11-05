package com.kseniia.hotelbooking.booking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

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

    @Autowired
    private MockMvc mockMvc;

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
                .andExpect(jsonPath("$.paths['/booking'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/booking'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/booking/{id}'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/booking/{id}'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/booking/{id}'].delete.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/booking/{id}'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/bookings'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/bookings'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/bookings/rooms/{roomId}'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/bookings/rooms/{roomId}'].get.responses['200']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }
}
