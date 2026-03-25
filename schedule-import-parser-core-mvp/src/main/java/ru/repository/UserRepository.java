package ru.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.model.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    Optional<User> findByFullNameIgnoreCase(String fullName);

    boolean existsByUsername(String username);
}
