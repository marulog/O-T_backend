package com.ott.transcoder;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.ott.transcoder.ffmpeg.execution.FfmpegExecutor;
import com.ott.transcoder.inspection.probe.execution.FfprobeExecutor;
import com.ott.transcoder.queue.rabbit.DelayQueuePublisher;

@ActiveProfiles("test")
@SpringBootTest
public class TranscoderApplicationTests {

	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private Environment environment;

	@MockitoBean
	private FfmpegExecutor ffmpegExecutor;

	@MockitoBean
	private FfprobeExecutor ffprobeExecutor;

	@MockitoBean
	private DelayQueuePublisher delayQueuePublisher;

	@Test
	public void contextLoads() {
	}

	@Test
	void objectMapperKeepsBootBeanNamePropertiesAndJavaTimeSupport() throws Exception {
		assertThat(applicationContext.getBeanNamesForType(ObjectMapper.class))
				.containsExactly("jacksonObjectMapper");
		assertThat(applicationContext.getBean("jacksonObjectMapperBuilder"))
				.isInstanceOf(Jackson2ObjectMapperBuilder.class);
		assertThat(applicationContext.getBeanNamesForType(Jackson2ObjectMapperBuilder.class))
				.containsExactly("jacksonObjectMapperBuilder");
		assertThat(environment.getProperty(
				"spring.jackson.mapper.accept-case-insensitive-enums", Boolean.class))
				.isTrue();
		assertThat(objectMapper.isEnabled(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS))
				.isTrue();

		String json = "{\"occurredAt\":\"2026-07-15T11:00:00\",\"state\":\"ready\"}";
		JacksonContractPayload payload = objectMapper.readValue(json, JacksonContractPayload.class);

		assertThat(payload.occurredAt()).isEqualTo(LocalDateTime.of(2026, 7, 15, 11, 0));
		assertThat(payload.state()).isEqualTo(ContractState.READY);
		assertThat(objectMapper.writeValueAsString(payload))
				.contains("\"occurredAt\":\"2026-07-15T11:00:00\"");
	}

	private record JacksonContractPayload(LocalDateTime occurredAt, ContractState state) {
	}

	private enum ContractState {
		READY
	}

}
