package com.ott.api_admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.ott.api_admin.outbox.poller.OutboxPoller;
import com.ott.infra.s3.service.S3PresignService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
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
class ApiAdminApplicationTests {

	private static final String RABBITMQ_USERNAME = "guest";
	private static final String RABBITMQ_PASSWORD = "guest";

	@Container
	static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4.0")
			.withDatabaseName("api_admin_context")
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
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private RedisConnectionFactory redisConnectionFactory;

	@Autowired
	private ConnectionFactory rabbitConnectionFactory;

	@Autowired
	private CacheManager cacheManager;

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
	void contextLoads() {
		assertThat(jdbcTemplate.queryForObject("select 1", Integer.class)).isEqualTo(1);
		assertThat(cacheManager).isInstanceOf(RedisCacheManager.class);

		try (RedisConnection connection = redisConnectionFactory.getConnection()) {
			assertThat(connection.ping()).isEqualTo("PONG");
		}

		rabbitConnectionFactory.createConnection().close();
	}

}
