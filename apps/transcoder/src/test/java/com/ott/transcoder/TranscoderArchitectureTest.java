package com.ott.transcoder;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaClass.Predicates;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

class TranscoderArchitectureTest {

    private final JavaClasses transcoderClasses = new ClassFileImporter()
            .withImportOption(new DoNotIncludeTests())
            .importPackages("com.ott.transcoder");

    private final JavaClasses commonCoreClasses = new ClassFileImporter()
            .withImportOption(new DoNotIncludeTests())
            .importPackages("com.ott.common.core");

    @Test
    void transcoderMustNotDependOnOtherDeployableApps() {
        noClasses()
                .that().resideInAPackage("com.ott.transcoder..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.ott.api_user..",
                        "com.ott.api_admin.."
                )
                .check(transcoderClasses);
    }

    @Test
    void applicationClassesMustNotDependOnPresentationTypes() {
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
				.check(transcoderClasses);
	}

    @Test
    void controllersMustNotInjectJpaRepositoriesDirectly() {
        noFields()
                .that().areDeclaredInClassesThat().resideInAPackage("..controller..")
                .should().haveRawType(Predicates.assignableTo(JpaRepository.class))
                .allowEmptyShould(true)
                .check(transcoderClasses);
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
}
