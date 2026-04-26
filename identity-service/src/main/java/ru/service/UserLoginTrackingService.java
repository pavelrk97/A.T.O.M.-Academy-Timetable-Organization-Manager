package ru.service;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.model.User;
import ru.model.UserLoginEvent;
import ru.repository.UserLoginEventRepository;

@Service
public class UserLoginTrackingService {

    private static final int MAX_USER_AGENT = 512;
    private static final int MAX_IP = 64;

    private static final Logger log = LoggerFactory.getLogger(UserLoginTrackingService.class);

    private final UserLoginEventRepository repository;

    public UserLoginTrackingService(UserLoginEventRepository repository) {
        this.repository = repository;
    }

    /**
     * Сохраняет факт входа. Не должен валить логин, если запись не получилась — поэтому
     * REQUIRES_NEW + try/catch.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLogin(User user, HttpServletRequest request) {
        try {
            UserLoginEvent event = new UserLoginEvent();
            event.setUserId(user.getId());
            event.setIpAddress(truncate(extractClientIp(request), MAX_IP));
            event.setUserAgent(truncate(request != null ? request.getHeader("User-Agent") : null, MAX_USER_AGENT));
            repository.save(event);
        } catch (Exception ex) {
            log.warn("Failed to record login event for user {}: {}", user.getUsername(), ex.getMessage());
        }
    }

    private static String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For может содержать список — берём самый левый (исходный клиент).
            int comma = forwarded.indexOf(',');
            String first = comma >= 0 ? forwarded.substring(0, comma) : forwarded;
            return first.trim();
        }
        return request.getRemoteAddr();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
