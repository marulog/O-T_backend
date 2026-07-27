package com.ott.api_admin;

import com.ott.common.security.CommonSecurityConfiguration;
import com.ott.common.web.CommonWebConfiguration;
import com.ott.infra.db.config.InfraDbConfiguration;
import com.ott.infra.mq.InfraMqConfiguration;
import com.ott.infra.redis.InfraRedisConfiguration;
import com.ott.infra.s3.config.InfraS3Configuration;
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
		CommonWebConfiguration.class,
		CommonSecurityConfiguration.class,
		InfraDbConfiguration.class,
		InfraS3Configuration.class,
		InfraMqConfiguration.class,
		InfraRedisConfiguration.class
})
@EnableAsync
public class ApiAdminApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiAdminApplication.class, args);
	}

}
