package com.ott.infra.mq;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ott.domain.common.MediaType;
import com.ott.infra.mq.config.MqMessageConfig;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class InfraMqConfigurationTest {

    @Test
    void exposesOnlyMqContractBeansThroughNarrowTypedImport() throws ClassNotFoundException {
        Class<?> configuration = Class.forName("com.ott.infra.mq.InfraMqConfiguration");
        Configuration configurationAnnotation = configuration.getAnnotation(Configuration.class);
        Import importAnnotation = configuration.getAnnotation(Import.class);

        assertThat(configurationAnnotation).isNotNull();
        assertThat(configurationAnnotation.proxyBeanMethods()).isFalse();
        assertThat(importAnnotation).isNotNull();
        assertThat(importAnnotation.value()).containsExactly(MqMessageConfig.class);

        new ApplicationContextRunner()
                .withUserConfiguration(configuration)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(DefaultClassMapper.class);
                    assertThat(context).hasSingleBean(MessageConverter.class);
                    assertThat(context.getBeanNamesForType(DefaultClassMapper.class)).containsExactly("classMapper");
                    assertThat(context.getBeanNamesForType(MessageConverter.class))
                            .containsExactly("jacksonMessageConverter");
                    assertThat(context).doesNotHaveBean(ConnectionFactory.class);
                    assertThat(context).doesNotHaveBean(RabbitTemplate.class);

                    TranscodeMessage expected = new TranscodeMessage(
                            10L,
                            20L,
                            "contents/10/origin/origin.mp4",
                            3072L,
                            MediaType.CONTENTS
                    );
                    MessageConverter converter = context.getBean(MessageConverter.class);
                    Message encoded = converter.toMessage(expected, new MessageProperties());

                    assertThat(new String(encoded.getBody(), StandardCharsets.UTF_8)).isEqualTo(
                            "{\"mediaId\":10,\"ingestJobId\":20,\"originUrl\":"
                                    + "\"contents/10/origin/origin.mp4\",\"fileSize\":3072,"
                                    + "\"mediaType\":\"CONTENTS\"}"
                    );
                    assertThat(converter.fromMessage(encoded)).isEqualTo(expected);

                    encoded.getMessageProperties().getHeaders().remove("__TypeId__");
                    assertThat(converter.fromMessage(encoded)).isEqualTo(expected);
                    System.out.println("INFRA_MQ_CONTEXT classMapper=1 jacksonMessageConverter=1 "
                            + "connectionFactory=0 rabbitTemplate=0 payloadRoundTrip=PASS defaultType=PASS");
                });
    }

    @Test
    void doesNotDuplicateBeansWhenLegacyConfigurationIsAlsoImported() throws ClassNotFoundException {
        Class<?> configuration = Class.forName("com.ott.infra.mq.InfraMqConfiguration");

        new ApplicationContextRunner()
                .withUserConfiguration(configuration, MqMessageConfig.class)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(DefaultClassMapper.class);
                    assertThat(context).hasSingleBean(MessageConverter.class);
                });
    }
}
