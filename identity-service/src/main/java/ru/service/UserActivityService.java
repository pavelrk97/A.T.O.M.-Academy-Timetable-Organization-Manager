package ru.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.UserActivityDto;
import ru.model.User;
import ru.repository.UserLoginEventRepository;
import ru.repository.UserLoginEventRepository.UserActivityRow;
import ru.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UserActivityService {

    private static final int RECENT_WINDOW_DAYS = 30;

    private final UserRepository userRepository;
    private final UserLoginEventRepository loginEventRepository;

    public UserActivityService(UserRepository userRepository,
                               UserLoginEventRepository loginEventRepository) {
        this.userRepository = userRepository;
        this.loginEventRepository = loginEventRepository;
    }

    @Transactional(readOnly = true)
    public List<UserActivityDto> getActivityForAllUsers() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RECENT_WINDOW_DAYS);

        Map<UUID, UserActivityRow> aggregateByUser = new HashMap<>();
        for (UserActivityRow row : loginEventRepository.aggregateActivity(cutoff)) {
            aggregateByUser.put(row.getUserId(), row);
        }

        List<User> users = userRepository.findAll();
        return users.stream()
                .map(user -> mapToDto(user, aggregateByUser.get(user.getId())))
                .sorted(Comparator
                        .comparing(UserActivityDto::getLastLoginAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private UserActivityDto mapToDto(User user, UserActivityRow row) {
        return UserActivityDto.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(user.getRole())
                .active(user.isActive())
                .lastLoginAt(row != null ? row.getLastLoginAt() : null)
                .loginCount30d(row != null && row.getCountSinceCutoff() != null ? row.getCountSinceCutoff() : 0L)
                .loginCountTotal(row != null && row.getCountTotal() != null ? row.getCountTotal() : 0L)
                .build();
    }
}
