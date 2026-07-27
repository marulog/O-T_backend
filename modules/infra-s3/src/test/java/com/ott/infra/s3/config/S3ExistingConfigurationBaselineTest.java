package com.ott.infra.s3.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ott.infra.s3.service.S3PresignService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3ExistingConfigurationBaselineTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withSystemProperties(
                    "aws.accessKeyId=test-access-key",
                    "aws.secretAccessKey=test-secret-key"
            )
            .withUserConfiguration(
                    S3ClientConfig.class,
                    S3PresignerConfig.class,
                    BaselineServiceScan.class
            );

    @Test
    void preservesExistingBeanNamesAndDefaultProperties() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(S3Client.class);
            assertThat(context).hasSingleBean(S3Presigner.class);
            assertThat(context).hasSingleBean(S3PresignService.class);
            assertThat(context.getBeanNamesForType(S3Client.class)).containsExactly("s3Client");
            assertThat(context.getBeanNamesForType(S3Presigner.class)).containsExactly("s3Presigner");
            assertThat(context.getBeanNamesForType(S3PresignService.class)).containsExactly("s3PresignService");

            S3PresignService service = context.getBean(S3PresignService.class);
            assertThat(ReflectionTestUtils.getField(service, "region")).isEqualTo("ap-northeast-2");
            assertThat(ReflectionTestUtils.getField(service, "bucket")).isEqualTo("local-bucket");
            assertThat(ReflectionTestUtils.getField(service, "publicBaseUrl")).isEqualTo("");
            assertThat(ReflectionTestUtils.getField(service, "expireSeconds")).isEqualTo(600L);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = S3PresignService.class)
    static class BaselineServiceScan {
    }
}
