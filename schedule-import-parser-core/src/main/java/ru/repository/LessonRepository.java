package ru.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.model.Lesson;

import java.time.LocalDate;
import java.util.UUID;
import java.util.List;

public interface LessonRepository extends JpaRepository<Lesson, UUID> {

    @Query("""
            select distinct l
            from Lesson l
            join fetch l.day d
            join fetch d.group g
            left join fetch l.assignedInstructors ai
            where (:groupCode is null or lower(g.code) = lower(:groupCode))
              and (:from is null or d.date >= :from)
              and (:to is null or d.date <= :to)
              and (:instructorId is null or exists (
                    select 1
                    from Lesson l2
                    join l2.assignedInstructors ai2
                    where l2.id = l.id and ai2.id = :instructorId
              ))
            order by d.date, l.orderNumber, l.id
            """)
    List<Lesson> findForSchedule(@Param("groupCode") String groupCode,
                                 @Param("instructorId") UUID instructorId,
                                 @Param("from") LocalDate from,
                                 @Param("to") LocalDate to);

    @Query("""
            select distinct l
            from Lesson l
            join fetch l.day d
            join fetch d.group g
            left join fetch l.assignedInstructors ai
            where (:from is null or d.date >= :from)
              and (:to is null or d.date <= :to)
            order by lower(g.code), d.date, l.orderNumber, l.id
            """)
    List<Lesson> findForDateRange(@Param("from") LocalDate from,
                                  @Param("to") LocalDate to);

    @Query("""
            select distinct l
            from Lesson l
            join fetch l.day d
            join fetch d.group g
            left join fetch l.assignedInstructors ai
            where (:from is null or d.date >= :from)
              and (:to is null or d.date <= :to)
              and (
                    exists (
                        select 1
                        from Lesson l2
                        join l2.assignedInstructors ai2
                        where l2.id = l.id and lower(ai2.fullName) = lower(:instructorName)
                    )
                    or lower(coalesce(l.lecturer, '')) = lower(:instructorName)
                    or exists (
                        select 1
                        from Lesson l3
                        join l3.lecturers lecturerName
                        where l3.id = l.id and lower(lecturerName) = lower(:instructorName)
                    )
              )
            order by lower(g.code), d.date, l.orderNumber, l.id
            """)
    List<Lesson> findForInstructorNameAndDateRange(@Param("instructorName") String instructorName,
                                                   @Param("from") LocalDate from,
                                                   @Param("to") LocalDate to);
}
