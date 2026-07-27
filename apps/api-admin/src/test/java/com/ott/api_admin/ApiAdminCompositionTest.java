package com.ott.api_admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.ott.api_admin.config.SecurityConfig;
import com.ott.api_admin.content.controller.BackOfficeContentsController;
import com.ott.api_admin.outbox.poller.OutboxPoller;
import com.ott.api_admin.publish.RabbitTranscodePublisher;
import com.ott.api_admin.tagging.service.AITaggingAsyncService;
import com.ott.api_admin.trending.event.TrendingCacheInvalidationListener;
import com.ott.common.web.exception.GlobalExceptionHandler;
import com.ott.infra.db.member.repository.MemberRepository;
import com.ott.infra.s3.service.S3PresignService;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
class ApiAdminCompositionTest {

	@Container
	static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4.0")
			.withDatabaseName("api_admin_composition")
			.withUsername("ott")
			.withPassword("ottpw");

	@MockitoBean
	private OutboxPoller outboxPoller;

	@MockitoBean
	private S3PresignService s3PresignService;

	@Autowired
	private ApplicationContext context;

	@DynamicPropertySource
	static void containerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", mysql::getJdbcUrl);
		registry.add("spring.datasource.username", mysql::getUsername);
		registry.add("spring.datasource.password", mysql::getPassword);
	}

	@Test
	void contextContainsEachAdminBoundaryExactlyOnce() {
		assertSingleBean(BackOfficeContentsController.class);
		assertSingleBean(SecurityConfig.class);
		assertSingleBean(GlobalExceptionHandler.class);
		assertSingleBean(MemberRepository.class);
		assertSingleBean(S3PresignService.class);
		assertSingleBean(MessageConverter.class);
		assertSingleBean(RabbitTranscodePublisher.class);
		assertSingleBean(OutboxPoller.class);
		assertSingleBean(AITaggingAsyncService.class);
		assertSingleBean(TrendingCacheInvalidationListener.class);
	}

	@Test
	void contextDoesNotContainOtherDeployableApplicationBeans() {
		assertThat(Arrays.stream(context.getBeanDefinitionNames())
				.map(context::getType)
				.filter(type -> type != null)
				.toList())
				.noneMatch(type -> type.getPackageName().startsWith("com.ott.api_user")
						|| type.getPackageName().startsWith("com.ott.transcoder"));
	}

	private void assertSingleBean(Class<?> type) {
		assertThat(context.getBeansOfType(type)).hasSize(1);
	}
}
