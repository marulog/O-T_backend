package com.ott.infra.db.ingest_command.repository;

import com.ott.domain.ingest_command.domain.CommandStatus;
import com.ott.domain.ingest_command.domain.IngestCommand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IngestCommandRepository extends JpaRepository<IngestCommand, Long> {

    List<IngestCommand> findByIngestJobId(Long ingestJobId);

    List<IngestCommand> findByIngestJobIdAndCommandStatus(Long ingestJobId, CommandStatus commandStatus);

    boolean existsByIngestJobIdAndCommandStatusNot(Long ingestJobId, CommandStatus commandStatus);

    Optional<IngestCommand> findByIngestJobIdAndCommandKey(Long ingestJobId, String commandKey);
}
