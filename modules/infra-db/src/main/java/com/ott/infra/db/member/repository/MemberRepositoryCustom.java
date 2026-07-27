package com.ott.infra.db.member.repository;

import com.ott.domain.member.domain.Member;
import com.ott.domain.member.domain.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MemberRepositoryCustom {

    Page<Member> findMemberList(Pageable pageable, String searchWord, Role role);
}
