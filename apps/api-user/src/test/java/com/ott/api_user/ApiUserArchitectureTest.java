package com.ott.api_user;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaClass.Predicates;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

class ApiUserArchitectureTest {

	private final JavaClasses apiUserClasses = new ClassFileImporter()
			.withImportOption(new DoNotIncludeTests())
			.importPackages("com.ott.api_user");

	private final JavaClasses apiUserTestClasses = new ClassFileImporter()
			.importPackages("com.ott.api_user.architecture.fixture");

	private final JavaClasses domainClasses = new ClassFileImporter()
			.withImportOption(new DoNotIncludeTests())
			.importPackages("com.ott.domain");

	private final JavaClasses commonCoreClasses = new ClassFileImporter()
			.withImportOption(new DoNotIncludeTests())
			.importPackages("com.ott.common.core");

	@Test
	void userApiMustNotDependOnOtherDeployableApps() {
		noClasses()
				.that().resideInAPackage("com.ott.api_user..")
				.should().dependOnClassesThat().resideInAnyPackage(
						"com.ott.api_admin..",
						"com.ott.transcoder.."
				)
				.check(apiUserClasses);
	}

	@Test
	void serviceReaderWriterClassesMustNotUsePresentationTypes() {
		serviceReaderWriterPresentationBoundaryRule()
				.check(apiUserClasses);
	}

	@Test
	void controllersMustNotInjectJpaRepositoriesDirectly() {
		noFields()
				.that().areDeclaredInClassesThat().resideInAPackage("..controller..")
				.should().haveRawType(Predicates.assignableTo(JpaRepository.class))
				.check(apiUserClasses);
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
	void serviceReaderWriterRuleRejectsCommonWebFixture() {
		assertThatThrownBy(() -> serviceReaderWriterPresentationBoundaryRule().check(apiUserTestClasses))
				.isInstanceOf(AssertionError.class)
				.hasMessageContaining("PageResponse");
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

	private static ArchRule serviceReaderWriterPresentationBoundaryRule() {
		return noClasses()
				.that().resideInAnyPackage("..service..", "..reader..", "..writer..")
				.or().haveSimpleNameEndingWith("Service")
				.or().haveSimpleNameEndingWith("Reader")
				.or().haveSimpleNameEndingWith("Writer")
				.should().dependOnClassesThat().resideInAnyPackage(
						"org.springframework.web..",
						"com.ott.common.web.."
				)
				.orShould().dependOnClassesThat()
				.haveFullyQualifiedName("org.springframework.security.core.Authentication");
	}
}
