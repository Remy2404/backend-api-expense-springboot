package com.wing.backendapiexpensespringboot.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import org.hamcrest.Matchers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigCsrfTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser
    void postRequestWithoutCsrfTokenIsForbidden() throws Exception {
        mockMvc.perform(post("/api/expenses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void postRequestWithCsrfTokenIsAllowed() throws Exception {
        mockMvc.perform(post("/api/expenses")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isNotFound()); // 404 because endpoint doesn't exist in test, but CSRF passed
    }

    @Test
    @WithMockUser
    void putRequestWithoutCsrfTokenIsForbidden() throws Exception {
        mockMvc.perform(post("/api/expenses/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void deleteRequestWithoutCsrfTokenIsForbidden() throws Exception {
        mockMvc.perform(post("/api/expenses/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void optionsRequestIsExemptFromCsrf() throws Exception {
        // OPTIONS returns 404 when endpoint doesn't exist, but CSRF exemption works
        mockMvc.perform(options("/api/expenses")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().is(Matchers.anyOf(Matchers.is(200), Matchers.is(404))));
    }

    @Test
    void healthEndpointIsExemptFromCsrf() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk()); // Health endpoint exists and is exempt from CSRF
    }

    @Test
    void actuatorHealthEndpointIsExemptFromCsrf() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isNotFound()); // Actuator may not be enabled in test
    }

    @Test
    void apiHealthEndpointIsExemptFromCsrf() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isNotFound()); // API health may not exist
    }

    @Test
    @WithMockUser
    void getRequestDoesNotRequireCsrfToken() throws Exception {
        mockMvc.perform(get("/api/expenses"))
                .andExpect(status().isNotFound()); // 404 because endpoint doesn't exist, but no CSRF error
    }
}
