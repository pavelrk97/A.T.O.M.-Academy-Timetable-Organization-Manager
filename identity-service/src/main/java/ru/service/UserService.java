package ru.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.dto.ChangePasswordRequest;
import ru.dto.InternalUserDetailsDto;
import ru.dto.MyProfileUpdateRequest;
import ru.dto.UserDto;
import ru.dto.UserUpsertRequest;
import ru.model.Role;
import ru.model.User;
import ru.repository.UserRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final String IMPORTED_INSTRUCTOR_DEFAULT_PASSWORD = "12345";
    private static final String DEMO_INSTRUCTOR_USERNAME = "instructor";
    private static final String DEMO_INSTRUCTOR_PASSWORD = "123";
    private static final String INTERNAL_IMPORTED_USERNAME_PREFIX = "imported-";
    private static final String PLACEHOLDER_INSTRUCTOR_NAME = "name";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserDto> getAll() {
        return userRepository.findAll().stream()
                .filter(this::isVisibleInCatalog)
                .sorted(java.util.Comparator.comparing(
                        user -> resolveDisplayName(user).toLowerCase(Locale.ROOT)
                ))
                .map(this::toDto)
                .toList();
    }

    public UserDto getCurrent(Authentication authentication) {
        return toDto(getCurrentUser(authentication));
    }

    public User getCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Authenticated user not found");
        }

        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Authenticated user not found"));
    }

    public InternalUserDetailsDto getInternalByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + username));
        log.debug("Internal user lookup completed: username={}, userId={}", username, user.getId());

        return InternalUserDetailsDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .password(user.getPassword())
                .fullName(user.getFullName())
                .role(user.getRole())
                .active(user.isActive())
                .build();
    }

    @Transactional
    public void syncImportedInstructors(List<String> fullNames) {
        List<String> normalizedNames = normalizeInstructorNames(fullNames);

        deactivateInvalidInstructorAccounts();

        if (normalizedNames.isEmpty()) {
            log.info("Imported instructor sync skipped: no names provided");
            return;
        }

        normalizedNames.forEach(this::upsertImportedInstructorAccount);
        deactivateStaleImportedInstructorAccounts(normalizedNames);
        upsertDemoInstructorAccount(normalizedNames.get(0));

        log.info("Imported instructors synced into identity-service: importedAccounts={}, demoInstructor={}",
                normalizedNames.size(), normalizedNames.get(0));
    }

    @Transactional
    public UserDto updateCurrentProfile(Authentication authentication, MyProfileUpdateRequest request) {
        User user = getCurrentUser(authentication);
        if (request.getDisplayName() != null) {
            user.setDisplayName(normalizeDisplayName(request.getDisplayName(), user.getFullName()));
        }
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setPosition(request.getPosition());
        user.setDepartment(request.getDepartment());
        UserDto updated = toDto(userRepository.save(user));
        log.info("Profile updated: username={}", updated.getUsername());
        return updated;
    }

    @Transactional
    public void changeCurrentPassword(Authentication authentication, ChangePasswordRequest request) {
        User user = getCurrentUser(authentication);
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("Password changed: username={}", user.getUsername());
    }

    @Transactional
    public UserDto create(UserUpsertRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists: " + request.getUsername());
        }

        User user = new User();
        apply(user, request);
        UserDto created = toDto(userRepository.save(user));
        log.info("User created: username={}, role={}", created.getUsername(), created.getRole());
        return created;
    }

    @Transactional
    public UserDto update(UUID id, UserUpsertRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id));
        apply(user, request);
        UserDto updated = toDto(userRepository.save(user));
        log.info("User updated: userId={}, username={}, role={}", updated.getId(), updated.getUsername(), updated.getRole());
        return updated;
    }

    /**
     * Удаляет пользователя. Защищаемся от двух вариантов «выстрела в ногу»:
     * — нельзя удалить себя (иначе токен админа становится невалидным сразу),
     * — нельзя удалить последнего активного админа (потеряем доступ к управлению).
     */
    @Transactional
    public void delete(UUID id, Authentication authentication) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id));

        if (authentication != null && user.getUsername().equals(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot delete the currently authenticated user");
        }

        if (user.getRole() == Role.ADMIN) {
            long otherActiveAdmins = userRepository.findAll().stream()
                    .filter(other -> !other.getId().equals(user.getId()))
                    .filter(other -> other.getRole() == Role.ADMIN)
                    .filter(User::isActive)
                    .count();
            if (otherActiveAdmins == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Cannot delete the last active administrator");
            }
        }

        userRepository.delete(user);
        log.info("User deleted: userId={}, username={}, role={}", user.getId(), user.getUsername(), user.getRole());
    }

    private void apply(User user, UserUpsertRequest request) {
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setDisplayName(normalizeDisplayName(request.getDisplayName(), request.getFullName()));
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setPosition(request.getPosition());
        user.setDepartment(request.getDepartment());
        user.setRole(request.getRole());
        user.setEditorAccess(request.isEditorAccess());
        user.setActive(request.isActive());
        user.setCanTeach(request.isCanTeach());
    }

    private UserDto toDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .displayName(resolveDisplayName(user))
                .email(user.getEmail())
                .phone(user.getPhone())
                .position(user.getPosition())
                .department(user.getDepartment())
                .role(user.getRole())
                .editorAccess(user.isEditorAccess())
                .active(user.isActive())
                .canTeach(user.isCanTeach())
                .build();
    }

    private String resolveDisplayName(User user) {
        return normalizeDisplayName(user.getDisplayName(), user.getFullName());
    }

    private String normalizeDisplayName(String displayName, String fallbackFullName) {
        if (displayName != null && !displayName.isBlank()) {
            return displayName.trim();
        }
        return fallbackFullName;
    }

    private List<String> normalizeInstructorNames(List<String> fullNames) {
        if (fullNames == null) {
            return List.of();
        }

        LinkedHashMap<String, String> uniqueNames = new LinkedHashMap<>();
        for (String fullName : fullNames) {
            if (!isMeaningfulInstructorName(fullName)) {
                continue;
            }
            uniqueNames.putIfAbsent(normalizeNameKey(fullName), fullName.trim());
        }
        return List.copyOf(uniqueNames.values());
    }

    private void upsertImportedInstructorAccount(String fullName) {
        User user = userRepository.findByUsername(fullName).orElseGet(User::new);
        boolean isNew = user.getId() == null;

        if (isNew) {
            // Полностью новый аккаунт: дефолты выставляем один раз и больше не трогаем
            // (иначе при каждом импорте слетают роль/editorAccess/пароль, выставленные админом вручную).
            user.setUsername(fullName);
            user.setPassword(passwordEncoder.encode(IMPORTED_INSTRUCTOR_DEFAULT_PASSWORD));
            user.setRole(ru.model.Role.INSTRUCTOR);
            user.setEditorAccess(false);
        }

        // Эти поля синхронизируются при каждом импорте — это технические данные,
        // которые ведутся со стороны расписания, а не админом руками.
        user.setFullName(fullName);
        user.setDisplayName(normalizeDisplayName(user.getDisplayName(), fullName));
        user.setActive(true);
        user.setCanTeach(true);
        userRepository.save(user);
    }

    private void upsertDemoInstructorAccount(String fullName) {
        User user = userRepository.findByUsername(DEMO_INSTRUCTOR_USERNAME).orElseGet(User::new);
        boolean isNew = user.getId() == null;

        if (isNew) {
            // Демо-аккаунт «instructor» создаётся один раз с дефолтным паролем.
            // Дальше пароль/роль/editorAccess не трогаем — иначе при каждом импорте
            // обнуляются настройки, выставленные администратором.
            user.setUsername(DEMO_INSTRUCTOR_USERNAME);
            user.setPassword(passwordEncoder.encode(DEMO_INSTRUCTOR_PASSWORD));
            user.setRole(ru.model.Role.INSTRUCTOR);
            user.setEditorAccess(true);
        }

        user.setFullName(fullName);
        user.setDisplayName(fullName);
        user.setActive(true);
        user.setCanTeach(true);
        userRepository.save(user);
    }

    private void deactivateStaleImportedInstructorAccounts(List<String> activeInstructorNames) {
        java.util.Set<String> activeKeys = activeInstructorNames.stream()
                .map(this::normalizeNameKey)
                .collect(Collectors.toSet());

        userRepository.findAll().stream()
                .filter(this::isSyncManagedImportedLoginAccount)
                .filter(user -> !activeKeys.contains(normalizeNameKey(user.getFullName())))
                .forEach(this::deactivateImportedInstructorAccount);
    }

    private void deactivateInvalidInstructorAccounts() {
        userRepository.findAll().stream()
                .filter(user -> user.getRole() == Role.INSTRUCTOR)
                .filter(user -> !user.isEditorAccess())
                .filter(user -> !isMeaningfulInstructorName(user.getFullName()))
                .forEach(this::deactivateImportedInstructorAccount);
    }

    private void deactivateImportedInstructorAccount(User user) {
        user.setActive(false);
        user.setCanTeach(false);
        userRepository.save(user);
    }

    private boolean isVisibleInCatalog(User user) {
        if (isInternalImportedScheduleUser(user)) {
            return false;
        }
        if (user.getRole() == Role.INSTRUCTOR && !isMeaningfulInstructorName(user.getFullName())) {
            return false;
        }
        return !isSyncManagedImportedLoginAccount(user) || user.isActive();
    }

    private boolean isInternalImportedScheduleUser(User user) {
        return user.getUsername() != null && user.getUsername().startsWith(INTERNAL_IMPORTED_USERNAME_PREFIX);
    }

    private boolean isSyncManagedImportedLoginAccount(User user) {
        return user.getRole() == Role.INSTRUCTOR
                && !user.isEditorAccess()
                && user.getUsername() != null
                && user.getFullName() != null
                && !DEMO_INSTRUCTOR_USERNAME.equalsIgnoreCase(user.getUsername())
                && user.getUsername().trim().equals(user.getFullName().trim());
    }

    private boolean isMeaningfulInstructorName(String fullName) {
        return fullName != null
                && !fullName.isBlank()
                && !PLACEHOLDER_INSTRUCTOR_NAME.equalsIgnoreCase(fullName.trim());
    }

    private String normalizeNameKey(String fullName) {
        return fullName.trim().toLowerCase(Locale.ROOT);
    }
}
