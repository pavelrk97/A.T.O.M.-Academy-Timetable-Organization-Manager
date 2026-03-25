package ru.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.model.ChangeLog;

import java.util.List;
import java.util.UUID;

public interface ChangeLogRepository extends JpaRepository<ChangeLog, UUID> {

    List<ChangeLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, UUID entityId);
}
