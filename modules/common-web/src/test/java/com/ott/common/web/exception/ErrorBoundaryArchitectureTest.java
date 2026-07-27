package com.ott.common.web.exception;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ErrorBoundaryArchitectureTest {

    @Test
    void coreErrorsAreOwnedByHttpIndependentErrorPackage() {
        assertThat(BusinessException.class.getPackageName()).isEqualTo("com.ott.common.core.error");
        assertThat(ErrorCode.class.getPackageName()).isEqualTo("com.ott.common.core.error");
    }

    @Test
    void commonCoreDoesNotDependOnWebFrameworkPackages() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.ott.common.core");

        noClasses()
                .that().resideInAPackage("com.ott.common.core..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "io.swagger..",
                        "jakarta.servlet.."
                )
                .check(classes);
    }

    @Test
    void serviceSourceDoesNotImportHttpErrorMappingTypes() throws IOException {
        Path root = repositoryRoot();

        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> offenders = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(ErrorBoundaryArchitectureTest::isServiceSource)
                    .filter(ErrorBoundaryArchitectureTest::importsHttpErrorMappingType)
                    .map(root::relativize)
                    .toList();

            assertThat(offenders).isEmpty();
        }
    }

    @Test
    void errorHttpStatusMapperCoversEveryErrorCode() {
        for (ErrorCode errorCode : ErrorCode.values()) {
            HttpStatus status = ErrorHttpStatusMapper.toHttpStatus(errorCode);

            assertThat(status)
                    .as(errorCode.name())
                    .isNotNull();
        }
    }

    private static Path repositoryRoot() {
        return Path.of("..", "..").toAbsolutePath().normalize();
    }

    private static boolean isServiceSource(Path path) {
        String normalized = path.toString().replace('\\', '/');
        return normalized.contains("/src/main/java/")
                && (normalized.endsWith("Service.java")
                || normalized.endsWith("Reader.java")
                || normalized.endsWith("Writer.java"));
    }

    private static boolean importsHttpErrorMappingType(Path path) {
        try {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            return source.contains("import org.springframework.http.HttpStatus")
                    || source.contains("import org.springframework.http.ResponseEntity")
                    || source.contains("import com.ott.common.web.exception.");
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read " + path, ex);
        }
    }
}
