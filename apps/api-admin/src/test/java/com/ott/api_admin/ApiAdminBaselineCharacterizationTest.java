package com.ott.api_admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.ott.api_admin.outbox.poller.OutboxPoller;
import com.ott.api_admin.trending.event.TrendingCacheInvalidationEvent;
import com.ott.api_admin.trending.event.TrendingCacheInvalidationListener;
import com.ott.common.web.exception.GlobalExceptionHandler;
import com.ott.infra.redis.config.RedisConfig;
import com.ott.infra.s3.service.S3PresignService;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
class ApiAdminBaselineCharacterizationTest {

	private static final String RABBITMQ_USERNAME = "guest";
	private static final String RABBITMQ_PASSWORD = "guest";

	@Container
	static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4.0")
			.withDatabaseName("api_admin_baseline")
			.withUsername("ott")
			.withPassword("ottpw");

	@Container
	static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
			.withExposedPorts(6379);

	@Container
	static final RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management-alpine")
			.withUser(RABBITMQ_USERNAME, RABBITMQ_PASSWORD);

	@MockitoBean
	private OutboxPoller outboxPoller;

	@MockitoBean
	private S3PresignService s3PresignService;

	@Autowired
	private CacheManager cacheManager;

	@Autowired
	private RedisConnectionFactory redisConnectionFactory;

	@Autowired
	private TrendingCacheInvalidationListener cacheInvalidationListener;

	@Autowired
	private GlobalExceptionHandler globalExceptionHandler;

	@Autowired
	private ConnectionFactory rabbitConnectionFactory;

	@DynamicPropertySource
	static void containerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", mysql::getJdbcUrl);
		registry.add("spring.datasource.username", mysql::getUsername);
		registry.add("spring.datasource.password", mysql::getPassword);
		registry.add("spring.data.redis.host", redis::getHost);
		registry.add("spring.data.redis.port", redis::getFirstMappedPort);
		registry.add("spring.rabbitmq.host", rabbitmq::getHost);
		registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
		registry.add("spring.rabbitmq.username", () -> RABBITMQ_USERNAME);
		registry.add("spring.rabbitmq.password", () -> RABBITMQ_PASSWORD);
	}

	@Test
	void currentAdminContextProvidesRedisWebAndRabbitBoundaries() throws Exception {
		assertThat(cacheManager).isInstanceOf(RedisCacheManager.class);
		assertThat(globalExceptionHandler).isNotNull();
		try (var connection = rabbitConnectionFactory.createConnection()) {
			assertThat(connection.isOpen()).isTrue();
		}
	}

	@RepeatedTest(2)
	void currentTrendingInvalidationEvictsRedisBackedAllEntry(RepetitionInfo repetitionInfo) {
		RedisStandaloneConfiguration consumerConfiguration =
				new RedisStandaloneConfiguration(redis.getHost(), redis.getFirstMappedPort());
		LettuceConnectionFactory consumerConnectionFactory = new LettuceConnectionFactory(consumerConfiguration);
		consumerConnectionFactory.afterPropertiesSet();

		try {
			CacheManager consumerCacheManager = new RedisConfig().cacheManager(consumerConnectionFactory);
			Cache consumerCache = consumerCacheManager.getCache("trending");
			String seededValue = "consumer-value-" + repetitionInfo.getCurrentRepetition();

			assertThat(consumerConnectionFactory).isNotSameAs(redisConnectionFactory);
			assertThat(consumerCacheManager).isNotSameAs(cacheManager);
			assertThat(consumerCache).isNotNull();
			consumerCache.put("all", seededValue);
			assertThat(consumerCache.get("all", String.class)).isEqualTo(seededValue);

			cacheInvalidationListener.onTrendingCacheInvalidation(new TrendingCacheInvalidationEvent());

			assertThat(consumerCache.get("all")).isNull();
		} finally {
			consumerConnectionFactory.destroy();
		}
	}
}
