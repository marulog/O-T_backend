package com.ott.infra.db.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ott.outside.persistence.OutsideRepository;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.lang.ArchRule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class InfraDbPersistenceArchitectureTest {

    private static final JavaClasses INFRA_DB_CLASSES = new ClassFileImporter()
            .withImportOption(new DoNotIncludeTests())
            .importPath(resolveMainClasses());

    @Test
    void everyInfraDbProductionClassStaysUnderItsModuleRootPackage() {
        classes()
                .should().resideInAPackage("com.ott.infra.db..")
                .check(INFRA_DB_CLASSES);
    }

    @Test
    void repositoriesAndCustomImplementationsStayInRepositoryPackages() {
        repositoryPackageBoundary().check(INFRA_DB_CLASSES);
    }

    @Test
    void repositoryPackageBoundaryRejectsWrongRootCounterexample() {
        JavaClasses counterexample = new ClassFileImporter().importClasses(OutsideRepository.class);

        assertThatThrownBy(() -> repositoryPackageBoundary().check(counterexample))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(OutsideRepository.class.getName());
    }

    private static ArchRule repositoryPackageBoundary() {
        return classes()
                .that().haveSimpleNameEndingWith("Repository")
                .or().haveSimpleNameEndingWith("RepositoryCustom")
                .or().haveSimpleNameEndingWith("RepositoryImpl")
                .should().resideInAPackage("com.ott.infra.db..repository");
    }

    @Test
    void infraDbDoesNotDependOnApplicationPackages() {
        noClasses()
                .that().resideInAPackage("com.ott.infra.db..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.ott.api_user..",
                        "com.ott.api_admin..",
                        "com.ott.transcoder.."
                )
                .check(INFRA_DB_CLASSES);
    }

    private static Path resolveMainClasses() {
        return List.of(
                        Path.of("modules/infra-db/build/classes/java/main"),
                        Path.of("build/classes/java/main")
                ).stream()
                .filter(Files::isDirectory)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("infra-db main class output is missing"));
    }
}
