package com.ott.api_user.member.service;

import com.ott.api_user.auth.client.KakaoUnlinkClient;
import com.ott.api_user.member.dto.request.SetPreferredTagRequest;
import com.ott.api_user.member.dto.request.UpdateMemberRequest;
import com.ott.api_user.member.dto.response.*;
import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import com.ott.infra.db.bookmark.repository.BookmarkRepository;
import com.ott.infra.db.click_event.repository.ClickRepository;
import com.ott.infra.db.comment.repository.CommentRepository;
import com.ott.domain.common.Status;
import com.ott.infra.db.likes.repository.LikesRepository;
import com.ott.infra.db.media.repository.MediaRepository;
import com.ott.domain.member.domain.Member;
import com.ott.domain.member.domain.Provider;
import com.ott.infra.db.member.repository.MemberRepository;
import com.ott.infra.db.member_radar_preference.repository.MemberRadarPreferenceRepository;
import com.ott.infra.db.playback.repository.PlaybackRepository;
import com.ott.domain.preferred_tag.domain.PreferredTag;
import com.ott.infra.db.preferred_tag.repository.PreferredTagRepository;
import com.ott.domain.tag.domain.Tag;
import com.ott.infra.db.tag.repository.TagRepository;
import com.ott.infra.db.watch_history.repository.WatchHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PreferredTagRepository preferredTagRepository;
    private final TagRepository tagRepository;
    private final WatchHistoryRepository watchHistoryRepository;
    private final MediaRepository mediaRepository;

    // 회원 탈퇴 시 soft delete
    private final KakaoUnlinkClient kakaoUnlinkClient;
    private final BookmarkRepository bookmarkRepository;
    private final LikesRepository likesRepository;
    private final PlaybackRepository playbackRepository;
    private final CommentRepository commentRepository;
    private final ClickRepository clickRepository;
    private final MemberRadarPreferenceRepository memberRadarPreferenceRepository;

    /**
     * 마이 페이지 조회 : 닉네임, 선호태그 List 반환
     */
    public MyPageResponse getMyPage(Long memberId) {
        Member findMember = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 건너뛰기한 유저는 빈리스트를 반환
        List<PreferredTag> preferredTags = preferredTagRepository.
                findAllWithTagAndCategoryByMemberIdAndStatus(memberId, Status.ACTIVE);

        return MyPageResponse.from(findMember, preferredTags);
    }


    /**
     * 마이페이지 내 정보 수정 : 닉네임, 선호태그 변경 후 반환
     */
    @Transactional
    public MyPageResponse updateMyInfo(Long memberId, UpdateMemberRequest request) {
        Member findMember = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 닉네임 변경
        if (request.getNickname() != null) {
            if (request.getNickname().isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "공백은 입력할 수 없습니다");
            }
            findMember.updateNickname(request.getNickname());
        }


        // 선호 태그 변경 -> 태그가 널인 경우? -> 일단 만들고 추가 예정
        // 1. 모든 태그 삭제
        if (request.getTagIds() != null) {
            preferredTagRepository.deleteAllByMember(findMember);

            // 새 태그 저장
            List<Tag> tags = tagRepository.findAllByIdInAndStatus(request.getTagIds(), Status.ACTIVE);
            if (tags.size() != request.getTagIds().size()) {
                throw new BusinessException(ErrorCode.TAG_NOT_FOUND);
            }

            List<PreferredTag> newTags = tags.stream()
                    .map(tag -> PreferredTag.builder()
                            .member(findMember)
                            .tag(tag)
                            .build())
                    .toList();
            preferredTagRepository.saveAll(newTags);
        }

        // 변경 하고 최신 상태 유지
        List<PreferredTag> preferredTags = preferredTagRepository
                .findAllWithTagAndCategoryByMemberIdAndStatus(memberId, Status.ACTIVE);

        return MyPageResponse.from(findMember, preferredTags);
    }

    /**
     * 온보딩 화면 : 초기 1회만 노출됨
     */
    @Transactional
    public void setPreferredTags(Long memberId, SetPreferredTagRequest request) {
        Member findMember = memberRepository.findByIdAndStatus(memberId, Status.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        //재 호출 시 중복 방지 코드
        preferredTagRepository.deleteAllByMember(findMember);

        List<Tag> tags = tagRepository.findAllByIdInAndStatus(request.getTagsId(), Status.ACTIVE);
        if (tags.size() != request.getTagsId().size()) {
            throw new BusinessException(ErrorCode.TAG_NOT_FOUND);
        }

        List<PreferredTag> preferredTags = tags.stream()
                .map(tag -> PreferredTag.builder()
                        .member(findMember)
                        .tag(tag)
                        .build())
                .toList();

        preferredTagRepository.saveAll(preferredTags);
        findMember.completeOnboarding();
    }


    /**
     * 회원 탈퇴
     * 1. 카카오 회원인 경우 카카오 연결 끊기
     * 2. 연관 데이터 Soft Delete
     * 3. 회원 Soft Delete + refreshToken 초기화
     */
    @Transactional
    public void withdraw(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 1. 카카오 연결 끊기
        if (member.getProvider() == Provider.KAKAO) {
            kakaoUnlinkClient.unlink(member.getProviderId());
        }

        // 벌크 쿼리 이후 영속성 컨텍스트가 초기화 되기 때문에 member.withdraw 먼저 수행
        // JPA가 변경 감지를 못해서 순서 변경
        // 2. 회원 Soft Delete ->
//        member.withdraw();
        memberRepository.softDeleteByMemberId(memberId);

        // 탈퇴 회원의 ACTIVE한 북마크, 좋아요 수 차감 x -> 통계에 사용되는 데이터임
        /**
         * 해당 쿼리문은 JPQL bulk update 연산으로 DB로 직접 접근하여 실행함
         * 그 다음 EntityNamger.clear()를 호출하는데 이때 영속성 컨텍스트가 비워짐
         * 그래서 해당 트랜잭션이 끝나고 영속성 컨텍스트와 변경점을 찾아서 쿼리를 날리는데,
         * 영속성 컨텍스트가 비워졌기 때문에 member.withdraw() 쿼리가 안날라감
         *
         * flushAutomatically : 반영 안된 변경 먼저 DB에 업데이트
         * clearAutomatically : 해당 쿼리 실행 후 캐시된 엔티티 상태를 비워서 DB와 일치시킴
         */
        bookmarkRepository.softDeleteAllByMemberId(memberId);
        likesRepository.softDeleteAllByMemberId(memberId);

        // 3. 연관 데이터 Soft Delete
        preferredTagRepository.softDeleteAllByMemberId(memberId);
        watchHistoryRepository.softDeleteAllByMemberId(memberId);
        playbackRepository.softDeleteAllByMemberId(memberId);
        commentRepository.softDeleteAllByMemberId(memberId);
        memberRadarPreferenceRepository.softDeleteByMemberId(memberId);
    }

    /**
     * 온보딩 건너뛰기 - onboardingCompleted = true 처리
     */
    @Transactional
    public void skipOnboarding(Long memberId) {
        Member findMember = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        findMember.completeOnboarding();
    }
}
