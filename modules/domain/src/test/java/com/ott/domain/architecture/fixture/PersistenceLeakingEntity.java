package com.ott.domain.architecture.fixture;

import com.ott.domain.common.BaseEntity;
import org.springframework.data.repository.Repository;

public class PersistenceLeakingEntity extends BaseEntity {

    private Repository<?, ?> leakedRepository;
}
