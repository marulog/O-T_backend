package com.ott.api_admin.member.service;

import com.ott.api_admin.member.dto.request.ChangeRoleRequest;
import com.ott.api_admin.member.dto.response.MemberListResponse;
import com.ott.api_admin.member.mapper.BackOfficeMemberMapper;
import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import com.ott.common.core.response.PageMetadata;
import com.ott.common.core.response.PageResult;
import com.ott.domain.member.domain.Member;
import com.ott.domain.member.domain.Role;
import com.ott.infra.db.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class BackOfficeMemberService {

    private final BackOfficeMemberMapper backOfficeMemberMapper;

    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public PageResult<MemberListResponse> getMemberList(int page, int size, String searchWord, Role role) {
        Pageable pageable = PageRequest.of(page, size);

        Page<Member> memberPage = memberRepository.findMemberList(pageable, searchWord, role);

        List<MemberListResponse> responseList = memberPage.getContent().stream()
                .map(backOfficeMemberMapper::toMemberListResponse)
                .toList();

        PageMetadata pageMetadata = PageMetadata.of(
                memberPage.getNumber(),
                memberPage.getTotalPages(),
                memberPage.getSize()
        );
        return PageResult.of(pageMetadata, responseList);
    }

    @Transactional
    public void changeRole(Long memberId, ChangeRoleRequest changeRoleRequest) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        member.changeRole(changeRoleRequest.role());
    }
}
