package ru.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.model.Group;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupRepository extends JpaRepository<Group, UUID> {

    List<Group> findAllByOrderByCodeAsc();

    Optional<Group> findByCode(String code);
}
