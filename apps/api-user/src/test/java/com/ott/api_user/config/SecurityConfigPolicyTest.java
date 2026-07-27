package com.ott.api_user.config;

import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ott.api_user.auth.oauth2.CustomOAuth2UserService;
import com.ott.api_user.auth.oauth2.handler.OAuth2FailureHandler;
import com.ott.api_user.auth.oauth2.handler.OAuth2SuccessHandler;
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
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.test.context.TestPropertySource;
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
        SecurityConfigPolicyTest.SecurityTestConfig.class,
        SecurityConfigPolicyTest.PolicyProbeController.class
})
@TestPropertySource(properties = {
        "app.frontend-url=http://localhost:3000",
        "jwt.access-token-expiry=3600",
        "jwt.refresh-token-expiry=86400"
})
class SecurityConfigPolicyTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp(@Autowired WebApplicationContext context) {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void permitAllTokenReissue_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/auth/reissue"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectMemberApi_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/members/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectTestAuthToken_whenUnauthenticated() throws Exception {
        mockMvc.perform(post("/test/auth/token/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allowMemberApi_whenMemberAuthenticated() throws Exception {
        mockMvc.perform(get("/members/me").with(user("member").roles("MEMBER")))
                .andExpect(status().isOk());
    }

    @Test
    void rejectMemberApi_whenAdminAuthenticated() throws Exception {
        mockMvc.perform(get("/members/me").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
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
        CustomOAuth2UserService customOAuth2UserService() {
            return mock(CustomOAuth2UserService.class);
        }

        @Bean
        OAuth2SuccessHandler oAuth2SuccessHandler() {
            return mock(OAuth2SuccessHandler.class);
        }

        @Bean
        OAuth2FailureHandler oAuth2FailureHandler() {
            return mock(OAuth2FailureHandler.class);
        }

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            return registrationId -> null;
        }

        @Bean
        OAuth2AuthorizedClientRepository authorizedClientRepository() {
            return mock(OAuth2AuthorizedClientRepository.class);
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

        @GetMapping("/auth/reissue")
        String reissue() {
            return "ok";
        }

        @GetMapping("/members/me")
        String memberApi() {
            return "ok";
        }

        @PostMapping("/test/auth/token/{memberId}")
        String testAuthToken() {
            return "ok";
        }
    }
}
