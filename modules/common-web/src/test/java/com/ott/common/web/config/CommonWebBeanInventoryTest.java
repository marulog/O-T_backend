package com.ott.common.web.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ott.common.web.exception.GlobalExceptionHandler;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

class CommonWebBeanInventoryTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(LegacyModuleScanConfiguration.class);

    @Test
    void broadModuleScanDiscoversExistingWebBeansOnce() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(WebMvcConfig.class);
            assertThat(context).hasSingleBean(SwaggerConfig.class);
            assertThat(context).hasSingleBean(OpenAPI.class);
            assertThat(context).hasSingleBean(GlobalExceptionHandler.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackages = "com.ott.common.web")
    static class LegacyModuleScanConfiguration {
    }
}
