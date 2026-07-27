package com.ott.common.security.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ott.common.security.filter.JwtAuthenticationFilter;
import com.ott.common.security.handler.JwtAccessDeniedHandler;
import com.ott.common.security.handler.JwtAuthenticationEntryPoint;
import com.ott.common.security.jwt.JwtTokenProvider;
import com.ott.common.security.util.CookieUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

class CommonSecurityBeanInventoryTest {

    private static final String TEST_SECRET =
            "dGVzdC1zZWNyZXQtdGhhdC1pcy1sb25nLWVub3VnaC1mb3ItSFMyNTY=";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(LegacyModuleScanConfiguration.class)
            .withPropertyValues(
                    "jwt.secret=" + TEST_SECRET,
                    "jwt.access-token-expiry=60000",
                    "jwt.refresh-token-expiry=120000"
            );

    @Test
    void broadModuleScanDiscoversExistingSecurityBeansOnce() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(JwtTokenProvider.class);
            assertThat(context).hasSingleBean(JwtAuthenticationFilter.class);
            assertThat(context).hasSingleBean(JwtAuthenticationEntryPoint.class);
            assertThat(context).hasSingleBean(JwtAccessDeniedHandler.class);
            assertThat(context).hasSingleBean(CookieUtil.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackages = "com.ott.common.security")
    static class LegacyModuleScanConfiguration {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
