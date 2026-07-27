package com.ott.infra.db.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ott.domain.DomainEntityPackage;
import com.ott.domain.member.domain.Member;
import com.ott.infra.db.InfraDbRepositoryPackage;
import com.ott.infra.db.media.repository.MediaRepository;
import com.ott.infra.db.media_metrics.repository.MediaMetricsJdbcRepository;
import com.ott.outside.persistence.OutsideRepository;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

class InfraDbConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataSourceAutoConfiguration.class,
                    JdbcTemplateAutoConfiguration.class,
                    HibernateJpaAutoConfiguration.class
            ))
            .withUserConfiguration(InfraDbConfiguration.class)
            .withPropertyValues(
                    "spring.datasource.url=jdbc:h2:mem:infra-db-config;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                    "spring.datasource.driver-class-name=org.h2.Driver",
                    "spring.jpa.hibernate.ddl-auto=create-drop",
                    "spring.sql.init.mode=never"
            );

    @Test
    void createsEntityManagerAndJpaQueryFactory() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(EntityManagerFactory.class);
            assertThat(context).hasSingleBean(JPAQueryFactory.class);
            assertThat(context).hasSingleBean(MediaMetricsJdbcRepository.class);

            EntityManagerFactory entityManagerFactory = context.getBean(EntityManagerFactory.class);
            assertThat(entityManagerFactory.getMetamodel().getEntities())
                    .filteredOn(entityType -> entityType.getJavaType().equals(Member.class))
                    .hasSize(1);

            EntityManager entityManager = entityManagerFactory.createEntityManager();
            try {
                assertThat(entityManager.isOpen()).isTrue();
            } finally {
                entityManager.close();
            }
        });
    }

    @Test
    void scansOnlyInfraDbRepositories() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MediaRepository.class);
            assertThat(context).doesNotHaveBean(OutsideRepository.class);

            EnableJpaRepositories repositoryScan = AnnotatedElementUtils.findMergedAnnotation(
                    InfraDbConfiguration.class,
                    EnableJpaRepositories.class
            );
            assertThat(repositoryScan).isNotNull();
            assertThat(repositoryScan.basePackages()).isEmpty();
            assertThat(repositoryScan.basePackageClasses()).containsExactly(InfraDbRepositoryPackage.class);

            EntityScan entityScan = AnnotatedElementUtils.findMergedAnnotation(
                    InfraDbConfiguration.class,
                    EntityScan.class
            );
            assertThat(entityScan).isNotNull();
            assertThat(entityScan.basePackages()).isEmpty();
            assertThat(entityScan.basePackageClasses()).containsExactly(DomainEntityPackage.class);
        });
    }

    @Test
    void repeatedImportDoesNotDuplicatePersistenceBeans() {
        contextRunner.withUserConfiguration(RepeatedImportConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(EntityManagerFactory.class);
            assertThat(context).hasSingleBean(MediaRepository.class);
            assertThat(context).hasSingleBean(MediaMetricsJdbcRepository.class);
            assertThat(context).hasSingleBean(JPAQueryFactory.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import(InfraDbConfiguration.class)
    static class RepeatedImportConfiguration {
    }
}
