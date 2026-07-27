package com.ott.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.ott.api_user.fixture.TestAppController;
import com.ott.common.web.config.SwaggerConfig;
import com.ott.common.web.config.WebMvcConfig;
import com.ott.common.web.exception.GlobalExceptionHandler;
import com.ott.common.web.response.PageResponseMapper;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;

class CommonWebConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(CommonWebConfiguration.class);

    @Test
    void importsOnlyWebModuleBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(WebMvcConfig.class);
            assertThat(context).hasSingleBean(SwaggerConfig.class);
            assertThat(context).hasSingleBean(OpenAPI.class);
            assertThat(context).hasSingleBean(GlobalExceptionHandler.class);
            assertThat(context.getBeanNamesForType(WebMvcConfig.class)).containsExactly("webMvcConfig");
            assertThat(context.getBeanNamesForType(SwaggerConfig.class)).containsExactly("swaggerConfig");
            assertThat(context.getBeanNamesForType(OpenAPI.class)).containsExactly("openAPI");
            assertThat(context.getBeanNamesForType(GlobalExceptionHandler.class))
                    .containsExactly("globalExceptionHandler");
            assertThat(context).doesNotHaveBean(TestAppController.class);
            System.out.println("COMMON_WEB_CONTEXT webMvcConfig=1 swaggerConfig=1 openAPI=1 "
                    + "globalExceptionHandler=1 appController=0");
        });
    }

    @Test
    void scansOnlyTypedWebModulePackages() {
        ComponentScan componentScan = CommonWebConfiguration.class.getAnnotation(ComponentScan.class);

        assertThat(componentScan).isNotNull();
        assertThat(componentScan.basePackages()).isEmpty();
        assertThat(componentScan.basePackageClasses()).containsExactly(
                WebMvcConfig.class,
                SwaggerConfig.class,
                GlobalExceptionHandler.class,
                PageResponseMapper.class
        );
    }
}
