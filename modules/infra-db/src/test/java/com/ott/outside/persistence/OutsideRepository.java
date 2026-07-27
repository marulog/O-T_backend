package com.ott.outside.persistence;

import com.ott.domain.media.domain.Media;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutsideRepository extends JpaRepository<Media, Long> {
}
