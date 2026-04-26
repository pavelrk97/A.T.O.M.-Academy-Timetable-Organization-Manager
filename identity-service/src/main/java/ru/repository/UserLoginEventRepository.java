package ru.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.model.UserLoginEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface UserLoginEventRepository extends JpaRepository<UserLoginEvent, UUID> {

    interface UserActivityRow {
        UUID getUserId();
        LocalDateTime getLastLoginAt();
        Long getCountSinceCutoff();
        Long getCountTotal();
    }

    @Query(value = """
            select e.user_id                                                as userId,
                   max(e.logged_at)                                         as lastLoginAt,
                   sum(case when e.logged_at >= :cutoff then 1 else 0 end)  as countSinceCutoff,
                   count(*)                                                 as countTotal
              from user_login_events e
             group by e.user_id
            """, nativeQuery = true)
    List<UserActivityRow> aggregateActivity(LocalDateTime cutoff);
}
