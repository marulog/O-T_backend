package com.ott.infra.s3.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ott.infra.s3.service.S3PresignService;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class InfraS3ConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withSystemProperties(
                    "aws.accessKeyId=test-access-key",
                    "aws.secretAccessKey=test-secret-key"
            )
            .withPropertyValues(
                    "aws.region=us-east-1",
                    "aws.s3.bucket=test-bucket",
                    "aws.s3.public-base-url=https://cdn.example.invalid/base/",
                    "aws.s3.presign-expire-seconds=120"
            )
            .withUserConfiguration(InfraS3Configuration.class);

    @Test
    void importsOnlyS3ModuleBeansOnce() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(S3Client.class);
            assertThat(context).hasSingleBean(S3Presigner.class);
            assertThat(context).hasSingleBean(S3PresignService.class);
            assertThat(context.getBeanNamesForType(S3Client.class)).containsExactly("s3Client");
            assertThat(context.getBeanNamesForType(S3Presigner.class)).containsExactly("s3Presigner");
            assertThat(context.getBeanNamesForType(S3PresignService.class)).containsExactly("s3PresignService");
            assertThat(context).doesNotHaveBean("apiUserApplication");
            assertThat(context).doesNotHaveBean("apiAdminApplication");
            assertThat(context).doesNotHaveBean("transcoderApplication");
            System.out.println("INFRA_S3_CONTEXT s3Client=1 s3Presigner=1 s3PresignService=1 appBeans=0");
        });
    }

    @Test
    void usesNarrowTypeSafeImports() {
        Import moduleImports = InfraS3Configuration.class.getAnnotation(Import.class);

        assertThat(moduleImports).isNotNull();
        assertThat(moduleImports.value()).containsExactly(
                S3ClientConfig.class,
                S3PresignerConfig.class
        );

        ComponentScan serviceScan = InfraS3Configuration.class.getAnnotation(ComponentScan.class);
        assertThat(serviceScan).isNotNull();
        assertThat(serviceScan.basePackages()).isEmpty();
        assertThat(serviceScan.basePackageClasses()).containsExactly(S3PresignService.class);
    }

    @Test
    void signsLocallyAndPreservesConfiguredProperties() {
        contextRunner.run(context -> {
            S3PresignService service = context.getBean(S3PresignService.class);

            URI signedUri = URI.create(service.createPutPresignedUrl("videos/sample.mp4", "video/mp4"));
            assertThat(signedUri.getHost()).isEqualTo("test-bucket.s3.amazonaws.com");
            assertThat(signedUri.getRawQuery()).contains("X-Amz-Expires=120");
            assertThat(service.toObjectUrl("videos/sample clip.mp4"))
                    .isEqualTo("https://cdn.example.invalid/base/videos/sample%20clip.mp4");
            System.out.println("INFRA_S3_MANUAL_QA localSigning=1 expires=120 networkCalls=0");
        });
    }

    @Test
    void rejectsBlankExplicitRegionDuringContextStartup() {
        contextRunner
                .withPropertyValues("aws.region= ")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalArgumentException.class)
                            .hasRootCauseMessage("region must not be blank or empty.");
                    System.out.println("INFRA_S3_INVALID_REGION rejectedAt=Region.of networkCalls=0");
                });
    }
}
