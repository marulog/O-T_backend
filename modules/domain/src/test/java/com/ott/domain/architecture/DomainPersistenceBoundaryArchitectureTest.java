package com.ott.domain.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ott.domain.architecture.fixture.PersistenceLeakingEntity;
import com.ott.domain.common.BaseEntity;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class DomainPersistenceBoundaryArchitectureTest {

    private static final JavaClasses DOMAIN_CLASSES = new ClassFileImporter()
            .withImportOption(new DoNotIncludeTests())
            .importPackages("com.ott.domain");

    @Test
    void domainDoesNotDependOnPersistenceRuntimeOutsideAuditingBaseEntity() {
        persistenceRuntimeBoundary().check(DOMAIN_CLASSES);
    }

    @Test
    void persistenceRuntimeBoundaryRejectsBaseEntitySubclassCounterexample() {
        JavaClasses counterexample = new ClassFileImporter().importClasses(PersistenceLeakingEntity.class);

        assertThatThrownBy(() -> persistenceRuntimeBoundary().check(counterexample))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(PersistenceLeakingEntity.class.getName());
    }

    private static ArchRule persistenceRuntimeBoundary() {
        return noClasses()
                .that().resideInAPackage("com.ott.domain..")
                .and().doNotHaveFullyQualifiedName(BaseEntity.class.getName())
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.data..",
                        "org.springframework.jdbc..",
                        "com.querydsl.jpa.."
                )
                .because("persistence runtime APIs belong to infra-db; BaseEntity keeps the Task 11 auditing allowlist");
    }

    @Test
    void domainDoesNotDeclareRepositoryPackagesOrRepositoryStereotypes() {
        noClasses()
                .that().resideInAPackage("com.ott.domain..repository..")
                .should().resideInAPackage("com.ott.domain..")
                .because("all repository packages must be relocated to infra-db")
                .allowEmptyShould(true)
                .check(DOMAIN_CLASSES);

        noClasses()
                .that().resideInAPackage("com.ott.domain..")
                .should().beAnnotatedWith("org.springframework.stereotype.Repository")
                .because("domain must not expose persistence components")
                .check(DOMAIN_CLASSES);
    }
}
