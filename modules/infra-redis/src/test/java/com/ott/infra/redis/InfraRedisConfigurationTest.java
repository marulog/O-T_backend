package com.ott.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.ott.infra.redis.config.RedisConfig;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

class InfraRedisConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
            .withUserConfiguration(InfraRedisConfiguration.class);

    @Test
    void exposesExactlyOneRedisTemplateAndCacheManagerWithoutAppBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(RedisConfig.class);
            assertThat(context).hasSingleBean(RedisTemplate.class);
            assertThat(context).hasSingleBean(RedisCacheManager.class);
            assertThat(context.getBeanNamesForType(RedisTemplate.class)).containsExactly("redisTemplate");
            assertThat(context.getBeanNamesForType(RedisCacheManager.class)).containsExactly("cacheManager");
            assertThat(context).doesNotHaveBean(LockProvider.class);
        });
    }

    @Test
    void importsOnlyTheTypedRedisConfiguration() {
        Import imported = InfraRedisConfiguration.class.getAnnotation(Import.class);

        assertThat(imported).isNotNull();
        assertThat(imported.value()).containsExactly(RedisConfig.class);
    }
}
