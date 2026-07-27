package com.ott.infra.redis.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.test.util.ReflectionTestUtils;

class RedisConfigBaselineTest {

    private final RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
    private final RedisConfig redisConfig = new RedisConfig();

    @Test
    void preservesRedisTemplateSerializers() {
        RedisTemplate<String, Object> template = redisConfig.redisTemplate(connectionFactory);

        assertThat(template.getConnectionFactory()).isSameAs(connectionFactory);
        assertThat(template.getKeySerializer()).isInstanceOf(StringRedisSerializer.class);
        assertThat(template.getHashKeySerializer()).isInstanceOf(StringRedisSerializer.class);
        assertThat(template.getValueSerializer()).isInstanceOf(GenericJackson2JsonRedisSerializer.class);
        assertThat(template.getHashValueSerializer()).isInstanceOf(GenericJackson2JsonRedisSerializer.class);
    }

    @Test
    void preservesCacheDefaults() {
        RedisCacheManager cacheManager = redisConfig.cacheManager(connectionFactory);
        RedisCacheConfiguration defaults = ReflectionTestUtils.invokeMethod(
                cacheManager,
                "getDefaultCacheConfiguration"
        );

        assertThat(defaults).isNotNull();

        assertThat(defaults.getTtlFunction().getTimeToLive("sample", new Object()))
                .isEqualTo(Duration.ofMinutes(60));
        assertThat(defaults.getAllowCacheNullValues()).isFalse();
        assertThat(defaults.getKeySerializationPair().write("cache-key"))
                .isEqualTo(ByteBuffer.wrap(new StringRedisSerializer().serialize("cache-key")));

        Map<String, Object> value = Map.of("id", 42, "name", "sample");
        assertThat(defaults.getValueSerializationPair().write(value))
                .isEqualTo(ByteBuffer.wrap(new GenericJackson2JsonRedisSerializer().serialize(value)));
    }
}
