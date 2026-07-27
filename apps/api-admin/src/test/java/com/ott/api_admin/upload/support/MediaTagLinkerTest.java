package com.ott.api_admin.upload.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import com.ott.domain.category.domain.Category;
import com.ott.infra.db.category.repository.CategoryRepository;
import com.ott.domain.common.Status;
import com.ott.domain.media.domain.Media;
import com.ott.domain.media_tag.domain.MediaTag;
import com.ott.infra.db.media_tag.repository.MediaTagRepository;
import com.ott.domain.tag.domain.Tag;
import com.ott.infra.db.tag.repository.TagRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MediaTagLinkerTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private MediaTagRepository mediaTagRepository;

    @InjectMocks
    private MediaTagLinker mediaTagLinker;

    // 동일한 태그를 두 번 넘기면 중복 예외가 터지는지 검증
    @Test
    void linkTags_throwsOnDuplicateTag() {
        Media media = Media.builder().id(1L).build();
        Category category = Category.builder().id(10L).name("cat").build();
        when(categoryRepository.findByIdAndStatus(10L, Status.ACTIVE)).thenReturn(java.util.Optional.of(category));

        assertThatThrownBy(() -> mediaTagLinker.linkTags(media, 10L, List.of(1L, 1L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_TAG_IN_LIST);
    }

    // 다른 카테고리에 속한 태그가 포함될 경우 예외 발생
    @Test
    void linkTags_throwsWhenCategoryMismatch() {
        Media media = Media.builder().id(1L).build();
        Category category = Category.builder().id(10L).name("cat").build();
        Category otherCategory = Category.builder().id(20L).name("other").build();
        Tag invalidTag = Tag.builder().id(2L).category(otherCategory).name("wrong").build();
        when(categoryRepository.findByIdAndStatus(10L, Status.ACTIVE)).thenReturn(java.util.Optional.of(category));
        when(tagRepository.findAllByIdInAndStatus(List.of(2L), Status.ACTIVE)).thenReturn(List.of(invalidTag));

        assertThatThrownBy(() -> mediaTagLinker.linkTags(media, 10L, List.of(2L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TAG_SELECTION);
    }

    // 정상적인 태그 리스트라면 MediaTagRepository에 저장 호출
    @Test
    void linkTags_savesMediaTagsWhenValid() {
        Media media = Media.builder().id(1L).build();
        Category category = Category.builder().id(10L).name("cat").build();
        Tag tag = Tag.builder().id(1L).category(category).name("ok").build();

        when(categoryRepository.findByIdAndStatus(10L, Status.ACTIVE)).thenReturn(java.util.Optional.of(category));
        when(tagRepository.findAllByIdInAndStatus(List.of(1L), Status.ACTIVE)).thenReturn(List.of(tag));

        mediaTagLinker.linkTags(media, 10L, List.of(1L));

        ArgumentCaptor<List<MediaTag>> captor = ArgumentCaptor.forClass(List.class);
        verify(mediaTagRepository).saveAll(captor.capture());
        List<MediaTag> saved = captor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getTag()).isSameAs(tag);
        assertThat(saved.get(0).getMedia()).isSameAs(media);
    }
}
