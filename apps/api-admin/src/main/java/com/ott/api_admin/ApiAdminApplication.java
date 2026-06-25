package com.ott.api_admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@EnableScheduling
@EnableCaching
@ComponentScan(basePackages = "com.ott")
@EntityScan(basePackages = "com.ott.domain")
@EnableJpaRepositories(basePackages = "com.ott.domain")
@EnableJpaAuditing
@EnableAsync
public class ApiAdminApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiAdminApplication.class, args);
	}

}
