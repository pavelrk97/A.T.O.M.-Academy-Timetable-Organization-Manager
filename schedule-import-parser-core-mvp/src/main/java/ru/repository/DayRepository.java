package ru.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.model.Day;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface DayRepository extends JpaRepository<Day, UUID> {

    Optional<Day> findByGroupIdAndDate(UUID groupId, LocalDate date);
}
