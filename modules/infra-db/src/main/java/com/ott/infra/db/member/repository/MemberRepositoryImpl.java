package com.ott.infra.db.member.repository;

import com.ott.domain.common.Status;
import com.ott.domain.member.domain.Member;
import com.ott.domain.member.domain.Role;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.ott.domain.member.domain.QMember.member;

@RequiredArgsConstructor
public class MemberRepositoryImpl implements MemberRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Member> findMemberList(Pageable pageable, String searchWord, Role role) {
        List<Member> memberList = queryFactory
                .selectFrom(member)
                .where(
                        member.status.eq(Status.ACTIVE),
                        nicknameContains(searchWord),
                        roleEq(role)
                )
                .orderBy(member.createdDate.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(member.count())
                .from(member)
                .where(
                        member.status.eq(Status.ACTIVE),
                        nicknameContains(searchWord),
                        roleEq(role)
                );

        return PageableExecutionUtils.getPage(memberList, pageable, countQuery::fetchOne);
    }

    private BooleanExpression nicknameContains(String searchWord) {
        if (StringUtils.hasText(searchWord))
            return member.nickname.contains(searchWord);
        return null;
    }

    private BooleanExpression roleEq(Role role) {
        if (role != null)
            return member.role.eq(role);
        return null;
    }
}
