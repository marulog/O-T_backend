package com.ott.api_admin;

import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ott.api_admin.config.SecurityConfig;
import com.ott.common.security.filter.JwtAuthenticationFilter;
import com.ott.common.security.handler.JwtAccessDeniedHandler;
import com.ott.common.security.handler.JwtAuthenticationEntryPoint;
import com.ott.common.security.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

@SpringJUnitWebConfig(classes = {
        SecurityConfig.class,
        ApiAdminSecurityPolicyTest.SecurityTestConfig.class,
        ApiAdminSecurityPolicyTest.PolicyProbeController.class
})
public class ApiAdminSecurityPolicyTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp(@Autowired WebApplicationContext context) {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void permitAllAdminLogin_whenUnauthenticated() throws Exception {
        mockMvc.perform(post("/back-office/login"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectGeneralBackOfficeApi_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/back-office/contents"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allowGeneralBackOfficeApi_whenEditorAuthenticated() throws Exception {
        mockMvc.perform(get("/back-office/contents").with(user("editor").roles("EDITOR")))
                .andExpect(status().isOk());
    }

    @Test
    void rejectGeneralBackOfficeApi_whenMemberAuthenticated() throws Exception {
        mockMvc.perform(get("/back-office/contents").with(user("member").roles("MEMBER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectAdminOnlyBackOfficeApi_whenEditorAuthenticated() throws Exception {
        mockMvc.perform(get("/back-office/admin/dashboard").with(user("editor").roles("EDITOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowAdminOnlyBackOfficeApi_whenAdminAuthenticated() throws Exception {
        mockMvc.perform(get("/back-office/admin/dashboard").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @TestConfiguration
    @EnableWebSecurity
    static class SecurityTestConfig {

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            return new JwtAuthenticationFilter(mock(JwtTokenProvider.class));
        }

        @Bean
        JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint() {
            return new StatusOnlyAuthenticationEntryPoint();
        }

        @Bean
        JwtAccessDeniedHandler jwtAccessDeniedHandler() {
            return new StatusOnlyAccessDeniedHandler();
        }

        @Bean
        HandlerMappingIntrospector mvcHandlerMappingIntrospector() {
            return new HandlerMappingIntrospector();
        }
    }

    static class StatusOnlyAuthenticationEntryPoint extends JwtAuthenticationEntryPoint {

        StatusOnlyAuthenticationEntryPoint() {
            super(new ObjectMapper());
        }

        @Override
        public void commence(
                HttpServletRequest request,
                HttpServletResponse response,
                AuthenticationException authException
        ) throws IOException {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    static class StatusOnlyAccessDeniedHandler extends JwtAccessDeniedHandler {

        StatusOnlyAccessDeniedHandler() {
            super(new ObjectMapper());
        }

        @Override
        public void handle(
                HttpServletRequest request,
                HttpServletResponse response,
                AccessDeniedException accessDeniedException
        ) throws IOException {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
        }
    }

    @RestController
    static class PolicyProbeController {

        @PostMapping("/back-office/login")
        String login() {
            return "ok";
        }

        @GetMapping("/back-office/contents")
        String generalBackOfficeApi() {
            return "ok";
        }

        @GetMapping("/back-office/admin/dashboard")
        String adminOnlyBackOfficeApi() {
            return "ok";
        }
    }
}
