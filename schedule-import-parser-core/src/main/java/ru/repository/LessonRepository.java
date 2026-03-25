package ru.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.model.Lesson;

import java.util.UUID;

public interface LessonRepository extends JpaRepository<Lesson, UUID> {
}
