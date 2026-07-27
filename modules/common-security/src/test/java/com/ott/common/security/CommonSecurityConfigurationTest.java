package com.ott.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ott.api_user.fixture.TestAppController;
import com.ott.common.security.filter.JwtAuthenticationFilter;
import com.ott.common.security.handler.JwtAccessDeniedHandler;
import com.ott.common.security.handler.JwtAuthenticationEntryPoint;
import com.ott.common.security.jwt.JwtTokenProvider;
import com.ott.common.security.util.CookieUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;

class CommonSecurityConfigurationTest {

    private static final String TEST_SECRET =
            "dGVzdC1zZWNyZXQtdGhhdC1pcy1sb25nLWVub3VnaC1mb3ItSFMyNTY=";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(CommonSecurityConfiguration.class)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withPropertyValues(
                    "jwt.secret=" + TEST_SECRET,
                    "jwt.access-token-expiry=60000",
                    "jwt.refresh-token-expiry=120000"
            );

    @Test
    void importsOnlySecurityModuleBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(JwtTokenProvider.class);
            assertThat(context).hasSingleBean(JwtAuthenticationFilter.class);
            assertThat(context).hasSingleBean(JwtAuthenticationEntryPoint.class);
            assertThat(context).hasSingleBean(JwtAccessDeniedHandler.class);
            assertThat(context).hasSingleBean(CookieUtil.class);
            assertThat(context.getBeanNamesForType(JwtTokenProvider.class)).containsExactly("jwtTokenProvider");
            assertThat(context.getBeanNamesForType(JwtAuthenticationFilter.class))
                    .containsExactly("jwtAuthenticationFilter");
            assertThat(context.getBeanNamesForType(JwtAuthenticationEntryPoint.class))
                    .containsExactly("jwtAuthenticationEntryPoint");
            assertThat(context.getBeanNamesForType(JwtAccessDeniedHandler.class))
                    .containsExactly("jwtAccessDeniedHandler");
            assertThat(context.getBeanNamesForType(CookieUtil.class)).containsExactly("cookieUtil");
            assertThat(context).doesNotHaveBean(TestAppController.class);
            System.out.println("COMMON_SECURITY_CONTEXT jwtTokenProvider=1 jwtAuthenticationFilter=1 "
                    + "jwtAuthenticationEntryPoint=1 jwtAccessDeniedHandler=1 cookieUtil=1 appController=0");
        });
    }

    @Test
    void scansOnlyTypedSecurityModulePackages() {
        ComponentScan componentScan = CommonSecurityConfiguration.class.getAnnotation(ComponentScan.class);

        assertThat(componentScan).isNotNull();
        assertThat(componentScan.basePackages()).isEmpty();
        assertThat(componentScan.basePackageClasses()).containsExactly(
                JwtTokenProvider.class,
                JwtAuthenticationFilter.class,
                JwtAuthenticationEntryPoint.class,
                JwtAccessDeniedHandler.class,
                CookieUtil.class
        );
    }
}
