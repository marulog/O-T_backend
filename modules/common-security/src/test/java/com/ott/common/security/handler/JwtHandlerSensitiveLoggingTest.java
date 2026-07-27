package com.ott.common.security.handler;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

class JwtHandlerSensitiveLoggingTest {

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    @Test
    void authenticationEntryPointDoesNotLogRawAuthenticationExceptionMessage() throws Exception {
        String sensitiveMarker = "SYNTHETIC_BEARER_7F3A_private-key.pem";
        ListAppender<ILoggingEvent> appender = attachAppender(JwtAuthenticationEntryPoint.class);

        try {
            new JwtAuthenticationEntryPoint(objectMapper).commence(
                    new MockHttpServletRequest(),
                    new MockHttpServletResponse(),
                    new BadCredentialsException(sensitiveMarker)
            );

            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .noneMatch(message -> message.contains(sensitiveMarker));
        } finally {
            detachAppender(JwtAuthenticationEntryPoint.class, appender);
        }
    }

    @Test
    void accessDeniedHandlerDoesNotLogRawAccessDeniedExceptionMessage() throws Exception {
        String sensitiveMarker = "s3://synthetic-private-bucket/admin/object-key";
        ListAppender<ILoggingEvent> appender = attachAppender(JwtAccessDeniedHandler.class);

        try {
            new JwtAccessDeniedHandler(objectMapper).handle(
                    new MockHttpServletRequest(),
                    new MockHttpServletResponse(),
                    new AccessDeniedException(sensitiveMarker)
            );

            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .noneMatch(message -> message.contains(sensitiveMarker));
        } finally {
            detachAppender(JwtAccessDeniedHandler.class, appender);
        }
    }

    private ListAppender<ILoggingEvent> attachAppender(Class<?> handlerType) {
        Logger logger = (Logger) LoggerFactory.getLogger(handlerType);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void detachAppender(Class<?> handlerType, ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(handlerType);
        logger.detachAppender(appender);
        appender.stop();
    }
}
