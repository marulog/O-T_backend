package com.ott.infra.s3.config;

import com.ott.infra.s3.service.S3PresignService;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import({
        S3ClientConfig.class,
        S3PresignerConfig.class
})
@ComponentScan(basePackageClasses = S3PresignService.class)
public class InfraS3Configuration {
}
