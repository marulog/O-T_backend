package com.ott.infra.mq.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ott.domain.common.MediaType;
import com.ott.infra.mq.TranscodeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MqMessageConfigBaselineTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MqMessageConfig.class)
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void preservesExistingBeanNamesAndPayloadContractWithoutRabbitConnection() {
        contextRunner.run(context -> {
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

            assertThat(new String(encoded.getBody(), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(
                    "{\"mediaId\":10,\"ingestJobId\":20,\"originUrl\":"
                            + "\"contents/10/origin/origin.mp4\",\"fileSize\":3072,\"mediaType\":\"CONTENTS\"}"
            );
            assertThat(converter.fromMessage(encoded)).isEqualTo(expected);

            encoded.getMessageProperties().getHeaders().remove("__TypeId__");
            assertThat(converter.fromMessage(encoded)).isEqualTo(expected);
        });
    }
}
