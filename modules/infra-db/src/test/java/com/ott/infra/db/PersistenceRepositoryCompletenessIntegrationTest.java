package com.ott.infra.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.ott.domain.click_event.domain.ClickType;
import com.ott.domain.common.MediaType;
import com.ott.domain.common.Status;
import com.ott.infra.db.bookmark.repository.BookmarkRepository;
import com.ott.infra.db.click_event.repository.ClickRepository;
import com.ott.infra.db.comment.repository.CommentRepository;
import com.ott.infra.db.config.InfraDbConfiguration;
import com.ott.infra.db.contents.repository.ContentsRepository;
import com.ott.infra.db.ingest_job.repository.IngestJobRepository;
import com.ott.infra.db.media.repository.MediaRepository;
import com.ott.infra.db.media_metrics.repository.MediaMetricsJdbcRepository;
import com.ott.infra.db.media_metrics.repository.MediaMetricsRepository;
import com.ott.infra.db.media_tag.repository.MediaTagRepository;
import com.ott.infra.db.member.repository.MemberRepository;
import com.ott.infra.db.playback.repository.PlaybackRepository;
import com.ott.infra.db.series.repository.SeriesRepository;
import com.ott.infra.db.short_form.repository.ShortFormRepository;
import com.ott.infra.db.watch_history.repository.WatchHistoryRepository;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.repository.Repository;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = InfraDbConfiguration.class)
class PersistenceRepositoryCompletenessIntegrationTest {

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(new DoNotIncludeTests())
            .importPath(resolveMainClasses());
    private static final Set<Class<?>> SPRING_DATA_REPOSITORIES = productionTypes(javaClass ->
            javaClass.isInterface() && javaClass.isAssignableTo(Repository.class));
    private static final Set<Class<?>> JDBC_REPOSITORIES = productionTypes(javaClass ->
            !javaClass.isInterface() && javaClass.getSimpleName().endsWith("JdbcRepository"));
    private static final Set<Class<?>> CUSTOM_IMPLEMENTATIONS = productionTypes(javaClass ->
            !javaClass.isInterface() && javaClass.getSimpleName().endsWith("RepositoryImpl"));

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private ApplicationContext context;

    @Test
    void resolvesEveryRelocatedRepositoryBean() {
        assertThat(SPRING_DATA_REPOSITORIES).hasSize(24);
        assertThat(JDBC_REPOSITORIES).hasSize(1);

        Set<Class<?>> discoveredRepositories = new LinkedHashSet<>(SPRING_DATA_REPOSITORIES);
        discoveredRepositories.addAll(JDBC_REPOSITORIES);
        assertThat(discoveredRepositories).hasSize(25);
        discoveredRepositories.forEach(type -> assertSingleBean(context, type));
    }

    @Test
    void invokesJdbcRepositoryAgainstMySql() {
        assertThat(context.getBean(MediaMetricsJdbcRepository.class).computePopularity()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("customFragmentInvocations")
    void invokesEveryCustomRepositoryFragmentAgainstMySql(
            CustomFragmentInvocation invocation
    ) {
        invocation.invoke(context);
    }

    @Test
    void customFragmentInvocationInventoryMatchesEveryProductionImplementation() {
        Set<Class<?>> invokedImplementations = customFragmentInvocations()
                .map(CustomFragmentInvocation::implementationType)
                .collect(Collectors.toSet());

        assertThat(CUSTOM_IMPLEMENTATIONS).hasSize(13);
        assertThat(invokedImplementations)
                .containsExactlyInAnyOrderElementsOf(CUSTOM_IMPLEMENTATIONS);
    }

    private static Stream<CustomFragmentInvocation> customFragmentInvocations() {
        PageRequest firstPage = PageRequest.of(0, 1);
        LocalDateTime now = LocalDateTime.now();
        return Stream.of(
                custom(BookmarkRepository.class,
                        repository -> repository.findBookmarkMediaList(-1L, firstPage)),
                custom(ClickRepository.class,
                        repository -> repository.countByMonthAndType(2000, 1, ClickType.SHORT_CLICK)),
                custom(CommentRepository.class,
                        repository -> repository.findMyComments(-1L, Status.ACTIVE, firstPage)),
                custom(ContentsRepository.class,
                        repository -> repository.findWithMediaById(-1L)),
                custom(IngestJobRepository.class,
                        repository -> repository.findIngestJobListWithMediaBySearchWordAndUploaderId(
                                firstPage, "missing", -1L)),
                custom(MediaRepository.class,
                        repository -> repository.findMediaListByMediaTypeAndSearchWord(
                                firstPage, MediaType.CONTENTS, "missing")),
                custom(MediaMetricsRepository.class,
                        repository -> repository.findTopByWeightedScore(1, 1, 1, 1, 1, -1L, 1)),
                custom(MediaTagRepository.class,
                        repository -> repository.findWithTagAndCategoryByMediaIds(List.of(-1L))),
                custom(MemberRepository.class,
                        repository -> repository.findMemberList(firstPage, "missing", null)),
                custom(PlaybackRepository.class,
                        repository -> repository.findLatestByMemberAndSeriesMediaIds(-1L, List.of(-1L))),
                custom(SeriesRepository.class,
                        repository -> repository.findWithMediaById(-1L)),
                custom(ShortFormRepository.class,
                        repository -> repository.findRecommendedShortForms(Map.of(), 1, 0)),
                custom(WatchHistoryRepository.class,
                        repository -> repository.countByMemberIdAndTagIdAndWatchedBetween(
                                -1L, -1L, now.minusDays(1), now))
        );
    }

    private static <T> CustomFragmentInvocation custom(Class<T> repositoryType, Consumer<T> invocation) {
        String implementationName = repositoryType.getSimpleName() + "Impl";
        Class<?> implementationType = CUSTOM_IMPLEMENTATIONS.stream()
                .filter(type -> type.getSimpleName().equals(implementationName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("missing custom implementation " + implementationName));
        return new CustomFragmentInvocation(
                implementationType,
                context -> invocation.accept(context.getBean(repositoryType))
        );
    }

    private static <T> void assertSingleBean(ApplicationContext context, Class<T> type) {
        Map<String, T> beans = context.getBeansOfType(type);
        assertThat(beans).as(type.getName()).hasSize(1);
        Map.Entry<String, T> bean = beans.entrySet().iterator().next();
        assertThat(context.getBean(bean.getKey(), type)).isSameAs(bean.getValue());
    }

    private static Set<Class<?>> productionTypes(java.util.function.Predicate<JavaClass> predicate) {
        return PRODUCTION_CLASSES.stream()
                .filter(predicate)
                .map(PersistenceRepositoryCompletenessIntegrationTest::loadClass)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Class<?> loadClass(JavaClass javaClass) {
        try {
            return Class.forName(javaClass.getName());
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("production class is not loadable: " + javaClass.getName(), exception);
        }
    }

    private static Path resolveMainClasses() {
        return Stream.of(
                        Path.of("modules/infra-db/build/classes/java/main"),
                        Path.of("build/classes/java/main")
                )
                .filter(Files::isDirectory)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("infra-db main class output is missing"));
    }

    private record CustomFragmentInvocation(
            Class<?> implementationType,
            Consumer<ApplicationContext> invocation
    ) {
        private void invoke(ApplicationContext context) {
            invocation.accept(context);
        }

        @Override
        public String toString() {
            return implementationType.getSimpleName();
        }
    }
}
