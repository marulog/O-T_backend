package com.ott.api_admin.member.controller;

import com.ott.api_admin.member.dto.request.ChangeRoleRequest;
import com.ott.api_admin.member.dto.response.MemberListResponse;
import com.ott.api_admin.member.service.BackOfficeMemberService;
import com.ott.common.web.response.PageResponse;
import com.ott.common.web.response.PageResponseMapper;
import com.ott.common.web.response.SuccessResponse;
import com.ott.domain.member.domain.Role;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/back-office/admin/members")
@RequiredArgsConstructor
public class BackOfficeMemberController implements BackOfficeMemberApi {

    private final BackOfficeMemberService backOfficeMemberService;

    @Override
    @GetMapping
    public ResponseEntity<SuccessResponse<PageResponse<MemberListResponse>>> getMemberList(
            @RequestParam(value = "page", defaultValue = "0") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size,
            @RequestParam(value = "searchWord", required = false) String searchWord,
            @RequestParam(value = "role", required = false) Role role
    ) {
        return ResponseEntity.ok(
                SuccessResponse.of(PageResponseMapper.from(backOfficeMemberService.getMemberList(page, size, searchWord, role)))
        );
    }

    @Override
    @PatchMapping("/{memberId}/role")
    public ResponseEntity<Void> changeRole(
            @PathVariable("memberId") Long memberId,
            @Valid @RequestBody ChangeRoleRequest changeRoleRequest
    ) {
        backOfficeMemberService.changeRole(memberId, changeRoleRequest);
        return ResponseEntity.noContent().build();
    }
}
