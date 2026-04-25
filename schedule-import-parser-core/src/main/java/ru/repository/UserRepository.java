package ru.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.model.Role;
import ru.model.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    Optional<User> findByFullNameIgnoreCase(String fullName);

    List<User> findAllByFullNameIgnoreCase(String fullName);

    List<User> findAllByCanTeachTrueOrderByFullNameAsc();

    List<User> findAllByRoleAndCanTeachTrueAndUsernameStartingWithOrderByFullNameAsc(Role role, String usernamePrefix);

    boolean existsByUsername(String username);
}
