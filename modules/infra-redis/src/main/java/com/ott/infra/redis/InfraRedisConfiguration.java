package com.ott.infra.redis;

import com.ott.infra.redis.config.RedisConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import(RedisConfig.class)
public class InfraRedisConfiguration {
}
