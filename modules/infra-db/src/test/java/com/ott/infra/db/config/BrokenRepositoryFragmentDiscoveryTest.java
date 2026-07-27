package com.ott.infra.db.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ott.domain.DomainEntityPackage;
import com.ott.fragmentfixture.BrokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

class BrokenRepositoryFragmentDiscoveryTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataSourceAutoConfiguration.class,
                    HibernateJpaAutoConfiguration.class
            ))
            .withUserConfiguration(BrokenFragmentConfiguration.class)
            .withPropertyValues(
                    "spring.datasource.url=jdbc:h2:mem:broken-fragment;MODE=MySQL;DB_CLOSE_DELAY=-1",
                    "spring.datasource.driver-class-name=org.h2.Driver",
                    "spring.jpa.hibernate.ddl-auto=create-drop",
                    "spring.sql.init.mode=never"
            );

    @Test
    void wrongImplementationPostfixMakesRepositoryContextFail() {
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasMessageContaining("mustResolveThroughCustomFragment");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = DomainEntityPackage.class)
    @EnableJpaRepositories(basePackageClasses = BrokenRepository.class, repositoryImplementationPostfix = "Impl")
    static class BrokenFragmentConfiguration {
    }
}
