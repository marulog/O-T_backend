package com.ott.api_admin;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static org.assertj.core.api.Assertions.assertThat;

import com.ott.common.security.CommonSecurityConfiguration;
import com.ott.common.web.CommonWebConfiguration;
import com.ott.api_admin.outbox.poller.OutboxPoller;
import com.ott.api_admin.tagging.service.AITaggingAsyncService;
import com.ott.api_admin.trending.event.TrendingCacheInvalidationListener;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaClass.Predicates;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.ott.infra.db.config.InfraDbConfiguration;
import com.ott.infra.mq.InfraMqConfiguration;
import com.ott.infra.redis.InfraRedisConfiguration;
import com.ott.infra.s3.config.InfraS3Configuration;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

class ApiAdminArchitectureTest {

	private final JavaClasses apiAdminClasses = new ClassFileImporter()
			.withImportOption(new DoNotIncludeTests())
			.importPackages("com.ott.api_admin");

	private final JavaClasses domainClasses = new ClassFileImporter()
			.withImportOption(new DoNotIncludeTests())
			.importPackages("com.ott.domain");

	private final JavaClasses commonCoreClasses = new ClassFileImporter()
			.withImportOption(new DoNotIncludeTests())
			.importPackages("com.ott.common.core");

	@Test
	void adminApiMustNotDependOnOtherDeployableApps() {
		noClasses()
				.that().resideInAPackage("com.ott.api_admin..")
				.should().dependOnClassesThat().resideInAnyPackage(
						"com.ott.api_user..",
						"com.ott.transcoder.."
				)
				.check(apiAdminClasses);
	}

	@Test
	void applicationMustComposeOnlyTheApprovedModuleRoots() {
		Import rootImports = ApiAdminApplication.class.getDeclaredAnnotation(Import.class);

		assertThat(rootImports).isNotNull();
		assertThat(Set.of(rootImports.value())).containsExactlyInAnyOrder(
				CommonWebConfiguration.class,
				CommonSecurityConfiguration.class,
				InfraDbConfiguration.class,
				InfraS3Configuration.class,
				InfraMqConfiguration.class,
				InfraRedisConfiguration.class
		);
	}

	@Test
	void applicationMustRetainInfraRedisDependency() throws IOException {
		Path projectDir = findProjectDir();
		String buildScript = Files.readString(projectDir.resolve("build.gradle"));

		assertThat(buildScript).contains("project(':modules:infra-redis')");
	}

	@Test
	void eventAndOutboxExecutionContractsMustRemainUnchanged() throws NoSuchMethodException {
		Method outboxPoll = OutboxPoller.class.getDeclaredMethod("pollAndPublish");
		Scheduled scheduled = outboxPoll.getDeclaredAnnotation(Scheduled.class);
		assertThat(scheduled.fixedDelay()).isEqualTo(10_000L);

		Method cacheInvalidation = TrendingCacheInvalidationListener.class.getDeclaredMethod(
				"onTrendingCacheInvalidation",
				com.ott.api_admin.trending.event.TrendingCacheInvalidationEvent.class
		);
		assertThat(cacheInvalidation.getDeclaredAnnotation(TransactionalEventListener.class).phase())
				.isEqualTo(TransactionPhase.AFTER_COMMIT);

		Method aiTagging = AITaggingAsyncService.class.getDeclaredMethod(
				"handleAiTagging",
				com.ott.api_admin.tagging.event.AiTaggingRequestedEvent.class
		);
		assertThat(aiTagging.getDeclaredAnnotation(Async.class)).isNotNull();
		assertThat(aiTagging.getDeclaredAnnotation(TransactionalEventListener.class).phase())
				.isEqualTo(TransactionPhase.AFTER_COMMIT);
	}

	@Test
	void serviceReaderWriterClassesMustNotUsePresentationTypes() {
		noClasses()
			.that().resideInAnyPackage("..service..", "..reader..", "..writer..")
			.or().haveSimpleNameEndingWith("Service")
			.or().haveSimpleNameEndingWith("Reader")
			.or().haveSimpleNameEndingWith("Writer")
			.should().dependOnClassesThat().resideInAnyPackage(
					"org.springframework.web..",
					"com.ott.common.web.."
				)
				.orShould().dependOnClassesThat()
				.haveFullyQualifiedName("org.springframework.security.core.Authentication")
				.check(apiAdminClasses);
	}

	@Test
	void controllersMustNotInjectJpaRepositoriesDirectly() {
		noFields()
				.that().areDeclaredInClassesThat().resideInAPackage("..controller..")
				.should().haveRawType(Predicates.assignableTo(JpaRepository.class))
				.check(apiAdminClasses);
	}

	@Test
	void commonCoreMustNotDependOnSpringOrSwagger() {
		noClasses()
				.that().resideInAPackage("com.ott.common.core..")
				.should().dependOnClassesThat().resideInAnyPackage(
						"org.springframework..",
						"io.swagger..",
						"jakarta.servlet.."
				)
				.check(commonCoreClasses);
	}

	@Test
	void domainMustNotDependOnDeployableApps() {
		noClasses()
				.that().resideInAPackage("com.ott.domain..")
				.should().dependOnClassesThat().resideInAnyPackage(
						"com.ott.api_user..",
						"com.ott.api_admin..",
						"com.ott.transcoder.."
				)
				.check(domainClasses);
	}

	private static Path findProjectDir() {
		Path workingDirectory = Path.of(System.getProperty("user.dir"));
		Path moduleDirectory = workingDirectory.resolve("apps/api-admin");
		return Files.exists(moduleDirectory.resolve("build.gradle")) ? moduleDirectory : workingDirectory;
	}
}
