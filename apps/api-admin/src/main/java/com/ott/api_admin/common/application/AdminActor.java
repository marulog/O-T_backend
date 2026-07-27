package com.ott.api_admin.common.application;

import java.util.Set;

public record AdminActor(Long memberId, Set<String> roleKeys) {

    public AdminActor {
        roleKeys = Set.copyOf(roleKeys);
    }
}
