package com.ott.infra.db.config;

import com.ott.domain.DomainEntityPackage;
import com.ott.infra.db.InfraDbRepositoryPackage;
import com.ott.infra.db.media_metrics.repository.MediaMetricsJdbcRepository;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration(proxyBeanMethods = false)
@EntityScan(basePackageClasses = DomainEntityPackage.class)
@EnableJpaRepositories(basePackageClasses = InfraDbRepositoryPackage.class)
@EnableJpaAuditing
@Import(MediaMetricsJdbcRepository.class)
public class InfraDbConfiguration {

    @Bean
    public JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
        return new JPAQueryFactory(entityManager);
    }
}
