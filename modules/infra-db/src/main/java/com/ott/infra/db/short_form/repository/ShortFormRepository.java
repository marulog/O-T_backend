package com.ott.infra.db.short_form.repository;

import com.ott.domain.short_form.domain.ShortForm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShortFormRepository extends JpaRepository<ShortForm, Long>, ShortFormRepositoryCustom  {

    Optional<ShortForm> findByMediaId(Long mediaId);
}
