package com.ott.api_user;

import static org.assertj.core.api.Assertions.assertThat;

import com.ott.common.security.CommonSecurityConfiguration;
import com.ott.common.web.CommonWebConfiguration;
import com.ott.infra.db.config.InfraDbConfiguration;
import com.ott.infra.db.member.repository.MemberRepository;
import com.ott.infra.redis.InfraRedisConfiguration;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

class ApiUserCompositionTest {

	@Test
	void applicationUsesDefaultLocalScanAndImportsOnlyModuleRootConfigurations() {
		Import imported = ApiUserApplication.class.getAnnotation(Import.class);

		assertThat(imported).isNotNull();
		assertThat(Set.copyOf(Arrays.asList(imported.value()))).containsExactlyInAnyOrder(
				CommonWebConfiguration.class,
				CommonSecurityConfiguration.class,
				InfraDbConfiguration.class,
				InfraRedisConfiguration.class);
		assertThat(ApiUserApplication.class.getDeclaredAnnotation(ComponentScan.class)).isNull();
		assertThat(ApiUserApplication.class.getDeclaredAnnotation(EntityScan.class)).isNull();
		assertThat(ApiUserApplication.class.getDeclaredAnnotation(EnableJpaRepositories.class)).isNull();
	}

	@Test
	void contextFailsWhenInfraDbConfigurationIsMissing() {
		new ApplicationContextRunner()
				.withUserConfiguration(MissingInfraDbConfiguration.class)
				.run(context -> {
					assertThat(context).hasFailed();
					assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(
							org.springframework.beans.factory.NoSuchBeanDefinitionException.class);
					assertThat(context.getStartupFailure()).hasMessageContaining(MemberRepository.class.getName());
				});
	}

	@Configuration(proxyBeanMethods = false)
	static class MissingInfraDbConfiguration {

		@Bean
		Object repositoryConsumer(MemberRepository memberRepository) {
			return new Object();
		}
	}
}
