package com.ott.api_admin.common.application;

import org.springframework.security.core.Authentication;

import java.util.stream.Collectors;

public final class AdminActorMapper {

    private AdminActorMapper() {
    }

    public static AdminActor from(Authentication authentication) {
        return new AdminActor(
                (Long) authentication.getPrincipal(),
                authentication.getAuthorities().stream()
                        .map(authority -> authority.getAuthority())
                        .collect(Collectors.toUnmodifiableSet())
        );
    }
}
