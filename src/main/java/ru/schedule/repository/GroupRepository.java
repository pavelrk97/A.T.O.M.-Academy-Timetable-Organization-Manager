package ru.schedule.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.schedule.model.Group;

import java.util.Optional;
import java.util.UUID;

public interface GroupRepository extends JpaRepository<Group, UUID> {

    Optional<Group> findByCode(String code);
}
