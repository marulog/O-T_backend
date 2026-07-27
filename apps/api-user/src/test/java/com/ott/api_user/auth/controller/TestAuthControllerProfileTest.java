package com.ott.api_user.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.ott.api_user.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class TestAuthControllerProfileTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(AuthService.class, () -> mock(AuthService.class))
            .withUserConfiguration(TestAuthController.class);

    @Test
    void defaultProfileDoesNotLoadTestAuthController() {
        contextRunner.run(context ->
                assertThat(context).doesNotHaveBean(TestAuthController.class));
    }

    @Test
    void prodProfileDoesNotLoadTestAuthController() {
        contextRunner
                .withPropertyValues("spring.profiles.active=prod")
                .run(context -> assertThat(context).doesNotHaveBean(TestAuthController.class));
    }

    @Test
    void explicitLocalTestAuthProfileLoadsTestAuthController() {
        contextRunner
                .withPropertyValues("spring.profiles.active=local-test-auth")
                .run(context -> assertThat(context).hasSingleBean(TestAuthController.class));
    }
}
