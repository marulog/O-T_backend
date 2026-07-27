package com.ott.fragmentfixture;

import com.ott.domain.media.domain.Media;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrokenRepository extends JpaRepository<Media, Long>, BrokenRepositoryCustom {
}
