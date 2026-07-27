package com.ott.api_user.comment.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ott.api_user.comment.dto.request.UpdateCommentRequest;
import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import com.ott.domain.comment.domain.Comment;
import com.ott.domain.common.Status;
import com.ott.domain.member.domain.Member;
import com.ott.infra.db.comment.repository.CommentRepository;
import com.ott.infra.db.contents.repository.ContentsRepository;
import com.ott.infra.db.member.repository.MemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private ContentsRepository contentsRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private CommentService commentService;

    @Test
    void updateCommentRejectsNonOwner() {
        Member owner = Member.builder().id(11L).build();
        Comment comment = Comment.builder()
                .id(31L)
                .member(owner)
                .content("original")
                .isSpoiler(false)
                .build();
        UpdateCommentRequest request = new UpdateCommentRequest();
        when(commentRepository.findByIdAndStatus(31L, Status.ACTIVE)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.updateComment(12L, 31L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMENT_FORBIDDEN);

        verify(commentRepository).findByIdAndStatus(31L, Status.ACTIVE);
        verify(commentRepository, never()).save(comment);
    }
}
