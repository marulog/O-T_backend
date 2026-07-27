package com.ott.infra.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ott.domain.category.domain.Category;
import com.ott.domain.common.MediaType;
import com.ott.domain.common.PublicStatus;
import com.ott.domain.common.Status;
import com.ott.domain.media.domain.Media;
import com.ott.domain.media.domain.MediaStatus;
import com.ott.domain.media_tag.domain.MediaTag;
import com.ott.domain.member.domain.Member;
import com.ott.domain.member.domain.Provider;
import com.ott.domain.member.domain.Role;
import com.ott.domain.tag.domain.Tag;
import com.ott.infra.db.category.repository.CategoryRepository;
import com.ott.infra.db.config.InfraDbConfiguration;
import com.ott.infra.db.media_tag.repository.MediaTagRepository;
import com.ott.infra.db.member.repository.MemberRepository;
import com.ott.infra.db.tag.repository.TagRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
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
class TaxonomyPersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private MediaTagRepository mediaTagRepository;

    @Test
    void mediaTagRepositoryReadsTagAndCategoryGraph() {
        // Given
        Member uploader = memberRepository.saveAndFlush(member("taxonomy-uploader"));
        Category category = categoryRepository.saveAndFlush(Category.builder()
                .name("Drama")
                .build());
        Tag tag = tagRepository.saveAndFlush(Tag.builder()
                .category(category)
                .name("Warm")
                .build());
        Media media = persistMedia(uploader);
        MediaTag mediaTag = mediaTagRepository.saveAndFlush(MediaTag.builder()
                .media(media)
                .tag(tag)
                .build());
        entityManager.clear();

        // When
        List<MediaTag> mediaTags = mediaTagRepository.findWithTagAndCategoryByMediaId(media.getId());
        List<String> tagNames = tagRepository.findTagNamesByMediaId(media.getId(), Status.ACTIVE);
        List<String> categoryNames = categoryRepository.findCategoryNamesByMediaId(media.getId(), Status.ACTIVE);

        // Then
        assertThat(mediaTags).hasSize(1);
        assertThat(mediaTags.get(0).getId()).isEqualTo(mediaTag.getId());
        assertThat(mediaTags.get(0).getTag().getName()).isEqualTo("Warm");
        assertThat(mediaTags.get(0).getTag().getCategory().getName()).isEqualTo("Drama");
        assertThat(tagNames).containsExactly("Warm");
        assertThat(categoryNames).containsExactly("Drama");
    }

    @Test
    void tagRequiresCategory() {
        // Given
        Tag tagWithoutCategory = Tag.builder()
                .name("Orphan")
                .build();

        // When / Then
        assertThatThrownBy(() -> tagRepository.save(tagWithoutCategory))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Media persistMedia(Member uploader) {
        Media media = Media.builder()
                .uploader(uploader)
                .title("Autumn Episode")
                .description("A repository integration sample")
                .posterUrl("https://cdn.example.com/poster.jpg")
                .thumbnailUrl("https://cdn.example.com/thumb.jpg")
                .bookmarkCount(0L)
                .likesCount(0L)
                .mediaType(MediaType.CONTENTS)
                .publicStatus(PublicStatus.PUBLIC)
                .mediaStatus(MediaStatus.COMPLETED)
                .build();
        entityManager.persist(media);
        entityManager.flush();
        return media;
    }

    private static Member member(String providerId) {
        return Member.builder()
                .provider(Provider.KAKAO)
                .providerId(providerId)
                .email(providerId + "@example.com")
                .nickname(providerId)
                .role(Role.EDITOR)
                .build();
    }
}
