package com.ott.fragmentfixture;

public class BrokenRepositoryWrongPostfix implements BrokenRepositoryCustom {

    @Override
    public long mustResolveThroughCustomFragment() {
        return 0L;
    }
}
