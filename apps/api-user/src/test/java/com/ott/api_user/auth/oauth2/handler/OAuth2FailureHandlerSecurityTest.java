package com.ott.api_user.auth.oauth2.handler;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.test.util.ReflectionTestUtils;

class OAuth2FailureHandlerSecurityTest {

    @Test
    void authenticationFailureDoesNotExposeRawExceptionMessageInLogOrRedirect() throws Exception {
        String sensitiveMarker = "SYNTHETIC_BEARER_7F3A_private-key.pem";
        OAuth2FailureHandler handler = new OAuth2FailureHandler();
        ReflectionTestUtils.setField(handler, "frontedUrl", "https://frontend.invalid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ListAppender<ILoggingEvent> appender = attachAppender();

        try {
            handler.onAuthenticationFailure(
                    new MockHttpServletRequest(),
                    response,
                    new AuthenticationServiceException(sensitiveMarker)
            );

            assertThat(response.getStatus()).isEqualTo(302);
            assertThat(response.getRedirectedUrl())
                    .isEqualTo("https://frontend.invalid/auth/login?error=oauth_authentication_failed")
                    .doesNotContain(sensitiveMarker);
            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .noneMatch(message -> message.contains(sensitiveMarker));
        } finally {
            detachAppender(appender);
        }
    }

    private ListAppender<ILoggingEvent> attachAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(OAuth2FailureHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void detachAppender(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(OAuth2FailureHandler.class);
        logger.detachAppender(appender);
        appender.stop();
    }
}
