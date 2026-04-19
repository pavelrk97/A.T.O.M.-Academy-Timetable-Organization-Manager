package ru.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import ru.dto.ImportedInstructorSyncRequest;
import ru.model.Role;
import ru.model.User;
import ru.repository.LessonRepository;
import ru.repository.UserRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InstructorIdentitySyncService {

    private static final Logger log = LoggerFactory.getLogger(InstructorIdentitySyncService.class);
    private static final String IMPORTED_USERNAME_PREFIX = "imported-";
    private static final String PLACEHOLDER_INSTRUCTOR_NAME = "name";

    private final RestClient restClient;
    private final UserRepository userRepository;
    private final LessonRepository lessonRepository;

    public InstructorIdentitySyncService(@Value("${identity.service.url}") String identityServiceUrl,
                                         @Value("${identity.service.api-key}") String internalApiKey,
                                         UserRepository userRepository,
                                         LessonRepository lessonRepository) {
        this.restClient = RestClient.builder()
                .baseUrl(identityServiceUrl)
                .defaultHeader("X-Internal-Api-Key", internalApiKey)
                .build();
        this.userRepository = userRepository;
        this.lessonRepository = lessonRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncCurrentInstructorsOnStartup() {
        syncCurrentInstructors();
    }

    public void syncCurrentInstructors() {
        syncImportedInstructors(buildSyncInstructorNames());
    }

    public void syncImportedInstructors(List<String> fullNames) {
        if (fullNames == null || fullNames.isEmpty()) {
            log.info("Identity sync skipped: no instructor names available");
            return;
        }

        try {
            restClient.post()
                    .uri("/internal/users/sync-instructors")
                    .body(ImportedInstructorSyncRequest.builder().fullNames(fullNames).build())
                    .retrieve()
                    .toBodilessEntity();

            log.info("Instructor identities synced with identity-service: count={}", fullNames.size());
        } catch (Exception ex) {
            log.warn("Failed to sync instructor identities with identity-service: count={}", fullNames.size(), ex);
        }
    }

    List<String> buildSyncInstructorNames() {
        List<String> importedInstructorNames = userRepository
                .findAllByRoleAndCanTeachTrueAndUsernameStartingWithOrderByFullNameAsc(
                        Role.INSTRUCTOR,
                        IMPORTED_USERNAME_PREFIX
                ).stream()
                .map(User::getFullName)
                .filter(this::isMeaningfulInstructorName)
                .map(String::trim)
                .distinct()
                .toList();

        String demoInstructor = chooseDemoInstructor(importedInstructorNames);
        if (demoInstructor == null) {
            return importedInstructorNames;
        }

        return java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(demoInstructor),
                        importedInstructorNames.stream().filter(name -> !name.equalsIgnoreCase(demoInstructor))
                )
                .toList();
    }

    String chooseDemoInstructor(List<String> importedInstructorNames) {
        if (importedInstructorNames == null || importedInstructorNames.isEmpty()) {
            return null;
        }

        Map<String, Long> upcomingLessonCounts = lessonRepository
                .findForDateRange(LocalDate.now(), LocalDate.now().plusDays(30))
                .stream()
                .flatMap(lesson -> lesson.getAssignedInstructors().stream())
                .filter(this::isImportedScheduleInstructor)
                .map(User::getFullName)
                .filter(this::isMeaningfulInstructorName)
                .map(this::normalizeNameKey)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        String bestName = importedInstructorNames.get(0);
        long bestCount = upcomingLessonCounts.getOrDefault(normalizeNameKey(bestName), 0L);

        for (String fullName : importedInstructorNames) {
            long count = upcomingLessonCounts.getOrDefault(normalizeNameKey(fullName), 0L);
            if (count > bestCount) {
                bestName = fullName;
                bestCount = count;
            }
        }

        return bestName;
    }

    private boolean isImportedScheduleInstructor(User user) {
        return user != null
                && user.getUsername() != null
                && user.getUsername().startsWith(IMPORTED_USERNAME_PREFIX)
                && isMeaningfulInstructorName(user.getFullName());
    }

    private boolean isMeaningfulInstructorName(String name) {
        return name != null
                && !name.isBlank()
                && !PLACEHOLDER_INSTRUCTOR_NAME.equalsIgnoreCase(name.trim());
    }

    private String normalizeNameKey(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
