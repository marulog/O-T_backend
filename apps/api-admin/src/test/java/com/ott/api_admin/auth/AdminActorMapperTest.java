package com.ott.api_admin.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.ott.api_admin.common.application.AdminActor;
import com.ott.api_admin.common.application.AdminActorMapper;
import com.ott.domain.member.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

class AdminActorMapperTest {

    @Test
    void fromPreservesPrincipalMemberIdAndRoleKeys() {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                55L,
                null,
                List.of(
                        new SimpleGrantedAuthority(Role.ADMIN.getKey()),
                        new SimpleGrantedAuthority(Role.EDITOR.getKey())
                )
        );

        AdminActor actor = AdminActorMapper.from(authentication);

        assertThat(actor.memberId()).isEqualTo(55L);
        assertThat(actor.roleKeys()).containsExactlyInAnyOrder(Role.ADMIN.getKey(), Role.EDITOR.getKey());
    }
}
