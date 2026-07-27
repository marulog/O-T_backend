package com.ott.api_user;

import com.ott.common.security.CommonSecurityConfiguration;
import com.ott.common.web.CommonWebConfiguration;
import com.ott.infra.db.config.InfraDbConfiguration;
import com.ott.infra.redis.InfraRedisConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableCaching
@Import({
		InfraDbConfiguration.class,
		InfraRedisConfiguration.class,
		CommonWebConfiguration.class,
		CommonSecurityConfiguration.class
})
@EnableAsync
public class ApiUserApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiUserApplication.class, args);

	}

}
