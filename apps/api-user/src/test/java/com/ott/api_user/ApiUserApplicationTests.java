package com.ott.api_user;

import static org.assertj.core.api.Assertions.assertThat;

import com.ott.api_user.auth.oauth2.CustomOAuth2UserService;
import com.ott.api_user.auth.oauth2.handler.OAuth2FailureHandler;
import com.ott.api_user.auth.oauth2.handler.OAuth2SuccessHandler;
import com.ott.api_user.event.MoodRefreshEventListener;
import com.ott.api_user.media_metrics.batch.MediaMetricsBatchScheduler;
import com.ott.api_user.media_metrics.batch.MetricsCalculator;
import com.ott.api_user.playlist.service.TrendingCacheService;
import com.ott.api_user.search.controller.SearchController;
import com.ott.common.security.filter.JwtAuthenticationFilter;
import com.ott.common.security.jwt.JwtTokenProvider;
import com.ott.common.web.exception.GlobalExceptionHandler;
import com.ott.infra.db.member.repository.MemberRepository;
import java.util.List;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
class ApiUserApplicationTests {

	@Container
	static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4.0")
			.withDatabaseName("api_user_context")
			.withUsername("ott")
			.withPassword("ottpw");

	@Container
	static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
			.withExposedPorts(6379);

	@MockitoBean
	private MetricsCalculator metricsCalculator;

	@MockitoBean
	private LockProvider lockProvider;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private RedisConnectionFactory redisConnectionFactory;

	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private ScheduledAnnotationBeanPostProcessor scheduledPostProcessor;

	@DynamicPropertySource
	static void containerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", mysql::getJdbcUrl);
		registry.add("spring.datasource.username", mysql::getUsername);
		registry.add("spring.datasource.password", mysql::getPassword);
		registry.add("spring.data.redis.host", redis::getHost);
		registry.add("spring.data.redis.port", redis::getFirstMappedPort);
	}

	@Test
	void contextLoads() {
		assertThat(jdbcTemplate.queryForObject("select 1", Integer.class)).isEqualTo(1);

		try (RedisConnection connection = redisConnectionFactory.getConnection()) {
			assertThat(connection.ping()).isEqualTo("PONG");
		}
	}

	@Test
	void exposesRequiredUserApplicationCompositionWithoutOtherApplicationBeans() {
		assertSingleBean(SearchController.class);
		assertSingleBean(SecurityFilterChain.class);
		assertSingleBean(JwtAuthenticationFilter.class);
		assertSingleBean(JwtTokenProvider.class);
		assertSingleBean(CustomOAuth2UserService.class);
		assertSingleBean(OAuth2SuccessHandler.class);
		assertSingleBean(OAuth2FailureHandler.class);
		assertSingleBean(GlobalExceptionHandler.class);
		assertThat(applicationContext.getBean("redisTemplate")).isExactlyInstanceOf(RedisTemplate.class);
		assertSingleBean(CacheManager.class);
		assertSingleBean(MemberRepository.class);
		assertSingleBean(MediaMetricsBatchScheduler.class);
		assertSingleBean(TrendingCacheService.class);
		assertRealBean(MediaMetricsBatchScheduler.class);
		assertRealBean(TrendingCacheService.class);
		assertSingleBean(MoodRefreshEventListener.class);
		assertThat(applicationContext.containsBean("org.springframework.context.annotation.internalAsyncAnnotationProcessor"))
				.isTrue();
		assertThat(applicationContext.containsBean("org.springframework.context.annotation.internalScheduledAnnotationProcessor"))
				.isTrue();

		RequestMappingHandlerMapping mappings = applicationContext.getBean("requestMappingHandlerMapping",
				RequestMappingHandlerMapping.class);
		assertThat(mappings.getHandlerMethods().keySet())
				.anySatisfy(mapping -> assertThat(mapping.toString()).contains("/search"));

		assertThat(applicationContext.getBeanDefinitionNames())
				.filteredOn(name -> beanPackage(name).startsWith("com.ott.api_admin"))
				.isEmpty();
		assertThat(applicationContext.getBeanDefinitionNames())
				.filteredOn(name -> beanPackage(name).startsWith("com.ott.transcoder"))
				.isEmpty();
	}

	@Test
	void registersTheRealScheduledMethodsWithoutChangingTheirSchedules() throws NoSuchMethodException {
		List<String> registeredMethods = scheduledPostProcessor.getScheduledTasks().stream()
				.map(task -> task.getTask().toString())
				.toList();

		assertThat(registeredMethods)
				.anyMatch(task -> task.contains(TrendingCacheService.class.getName())
						&& task.contains("refreshTrending"))
				.anyMatch(task -> task.contains(MediaMetricsBatchScheduler.class.getName())
						&& task.contains("run"));

		Scheduled trending = TrendingCacheService.class.getDeclaredMethod("refreshTrending")
				.getAnnotation(Scheduled.class);
		assertThat(trending.fixedRate()).isEqualTo(45 * 60 * 1000);
		assertThat(trending.initialDelay()).isZero();

		Scheduled metrics = MediaMetricsBatchScheduler.class.getDeclaredMethod("run")
				.getAnnotation(Scheduled.class);
		assertThat(metrics.cron()).isEqualTo("0 0 */6 * * *");
	}

	private void assertSingleBean(Class<?> type) {
		assertThat(applicationContext.getBeanNamesForType(type)).hasSize(1);
	}

	private void assertRealBean(Class<?> type) {
		Object bean = applicationContext.getBean(type);
		assertThat(Mockito.mockingDetails(bean).isMock()).isFalse();
		assertThat(AopUtils.getTargetClass(bean)).isEqualTo(type);
	}

	private String beanPackage(String beanName) {
		Class<?> beanType = applicationContext.getType(beanName);
		return beanType == null ? "" : beanType.getPackageName();
	}

}
