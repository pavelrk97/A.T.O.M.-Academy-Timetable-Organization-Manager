package ru.service;

import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.dto.AutoImportSettingsDto;
import ru.dto.AutoImportSettingsUpdateRequest;
import ru.model.AutoImportSettings;
import ru.repository.AutoImportSettingsRepository;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AutoImportService {

    public static final String STATUS_OK = "OK";
    public static final String STATUS_ERROR = "ERROR";
    public static final String STATUS_RUNNING = "RUNNING";

    private static final Logger log = LoggerFactory.getLogger(AutoImportService.class);
    private static final ZoneId MOSCOW_ZONE = ZoneId.of("Europe/Moscow");
    private static final List<LocalTime> RUN_TIMES = List.of(LocalTime.of(13, 0), LocalTime.of(23, 0));
    private static final String GOOGLE_SHEETS_HOST = "docs.google.com";
    private static final Pattern SHEET_PATH_PATTERN = Pattern.compile("^/spreadsheets/d/([a-zA-Z0-9_-]+)(?:/.*)?$");
    private static final Pattern GID_PATTERN = Pattern.compile("(?:^|&)gid=(\\d+)(?:&|$)");
    private static final Duration HTTP_CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration HTTP_READ_TIMEOUT = Duration.ofSeconds(60);
    private static final int MAX_REDIRECTS = 3;

    private final AutoImportSettingsRepository repository;
    private final CsvImportService csvImportService;
    private final HttpClient httpClient;
    private final String userAgent;

    public AutoImportService(AutoImportSettingsRepository repository,
                             CsvImportService csvImportService,
                             @Value("${atom.auto-import.user-agent:ATOM-AutoImport/1.0}") String userAgent) {
        this.repository = repository;
        this.csvImportService = csvImportService;
        this.userAgent = userAgent;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(HTTP_CONNECT_TIMEOUT)
                .build();
    }

    @Transactional
    public AutoImportSettingsDto getSettings() {
        return toDto(loadOrCreate());
    }

    @Transactional
    public AutoImportSettingsDto updateSettings(AutoImportSettingsUpdateRequest request, String username) {
        AutoImportSettings settings = loadOrCreate();
        settings.setEnabled(request.isEnabled());
        if (request.getSourceUrl() != null && !request.getSourceUrl().isBlank()) {
            String sourceUrl = request.getSourceUrl().trim();
            validateSourceUrl(sourceUrl);
            settings.setSourceUrl(sourceUrl);
        }
        settings.setUpdatedBy(username);
        settings.setUpdatedAt(LocalDateTime.now());
        AutoImportSettings saved = repository.save(settings);
        log.info("Auto-import settings updated: enabled={}, by={}", saved.isEnabled(), username);
        return toDto(saved);
    }

    /**
     * Запускает обновление расписания. Используется и из шедулера, и из ручной кнопки.
     * @param triggeredBy идентификатор инициатора ("scheduler" / username)
     */
    public AutoImportSettingsDto runImport(String triggeredBy) {
        AutoImportSettings settings = markRunning(triggeredBy);
        String url = settings.getSourceUrl();
        try {
            byte[] csvBytes = downloadCsv(url);
            int importedGroups;
            try (ByteArrayInputStream stream = new ByteArrayInputStream(csvBytes)) {
                importedGroups = csvImportService.importFromCsv(stream);
            }
            return markSuccess(triggeredBy, importedGroups);
        } catch (Exception ex) {
            log.error("Auto-import run failed: triggeredBy={}, url={}", triggeredBy, url, ex);
            return markFailure(triggeredBy, ex);
        }
    }

    public LocalDateTime computeNextRunAt(boolean enabled) {
        if (!enabled) {
            return null;
        }
        ZonedDateTime nowMsk = ZonedDateTime.now(MOSCOW_ZONE);
        LocalDate searchDate = nowMsk.toLocalDate();
        for (int i = 0; i < 2; i++) {
            for (LocalTime time : RUN_TIMES) {
                ZonedDateTime candidate = ZonedDateTime.of(searchDate, time, MOSCOW_ZONE);
                if (candidate.isAfter(nowMsk)) {
                    return candidate.toLocalDateTime();
                }
            }
            searchDate = searchDate.plusDays(1);
        }
        return null;
    }

    @Transactional
    protected AutoImportSettings markRunning(String triggeredBy) {
        AutoImportSettings settings = loadOrCreate();
        settings.setLastStatus(STATUS_RUNNING);
        settings.setLastError(null);
        settings.setLastRunAt(LocalDateTime.now());
        settings.setUpdatedBy(triggeredBy);
        return repository.save(settings);
    }

    @Transactional
    protected AutoImportSettingsDto markSuccess(String triggeredBy, int groupsCount) {
        AutoImportSettings settings = loadOrCreate();
        settings.setLastStatus(STATUS_OK);
        settings.setLastError(null);
        settings.setLastRunAt(LocalDateTime.now());
        settings.setLastImportedGroups(groupsCount);
        settings.setUpdatedBy(triggeredBy);
        AutoImportSettings saved = repository.save(settings);
        log.info("Auto-import success: groups={}, triggeredBy={}", groupsCount, triggeredBy);
        return toDto(saved);
    }

    @Transactional
    protected AutoImportSettingsDto markFailure(String triggeredBy, Exception cause) {
        AutoImportSettings settings = loadOrCreate();
        settings.setLastStatus(STATUS_ERROR);
        settings.setLastError(buildErrorMessage(cause));
        settings.setLastRunAt(LocalDateTime.now());
        settings.setUpdatedBy(triggeredBy);
        return toDto(repository.save(settings));
    }

    private AutoImportSettings loadOrCreate() {
        return repository.findById(AutoImportSettings.SINGLETON_ID).orElseGet(() -> {
            AutoImportSettings fresh = new AutoImportSettings();
            fresh.setId(AutoImportSettings.SINGLETON_ID);
            fresh.setEnabled(false);
            fresh.setSourceUrl("");
            fresh.setUpdatedAt(LocalDateTime.now());
            return repository.save(fresh);
        });
    }

    private byte[] downloadCsv(String sourceUrl) throws Exception {
        URI currentUri = buildExportUri(sourceUrl);
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            validateGoogleSheetsUri(currentUri);
            log.info("Auto-import: downloading CSV from {}", currentUri);
            HttpRequest request = HttpRequest.newBuilder(currentUri)
                    .timeout(HTTP_READ_TIMEOUT)
                    .header("User-Agent", userAgent)
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (isRedirect(response.statusCode())) {
                currentUri = resolveRedirect(currentUri, response);
                continue;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Source returned HTTP " + response.statusCode());
            }
            byte[] body = response.body();
            if (body == null || body.length == 0) {
                throw new IllegalStateException("Received empty CSV from source");
            }
            // Google login pages and permission screens are HTML, not CSV.
            String head = new String(body, 0, Math.min(body.length, 200), StandardCharsets.UTF_8).trim().toLowerCase();
            if (head.startsWith("<!doctype html") || head.startsWith("<html")) {
                throw new IllegalStateException("Source returned HTML instead of CSV. Check that the sheet is public by link");
            }
            return body;
        }
        throw new IllegalStateException("Too many redirects while downloading CSV");
    }

    static URI buildExportUri(String sourceUrl) throws URISyntaxException {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new IllegalArgumentException("Source URL is required");
        }
        URI sourceUri = new URI(sourceUrl.trim());
        String sheetId = validateGoogleSheetsUri(sourceUri);
        StringBuilder export = new StringBuilder("https://docs.google.com/spreadsheets/d/")
                .append(sheetId)
                .append("/export?format=csv");
        String gid = extractGid(sourceUri);
        if (gid != null) {
            export.append("&gid=").append(gid);
        }
        return new URI(export.toString());
    }

    private static void validateSourceUrl(String sourceUrl) {
        try {
            buildExportUri(sourceUrl);
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Auto-import source URL is invalid", ex);
        }
    }

    private static boolean isRedirect(int statusCode) {
        return statusCode == 301
                || statusCode == 302
                || statusCode == 303
                || statusCode == 307
                || statusCode == 308;
    }

    private static URI resolveRedirect(URI currentUri, HttpResponse<?> response) {
        String location = response.headers().firstValue("Location")
                .orElseThrow(() -> new IllegalStateException("Redirect response without Location header"));
        URI nextUri = currentUri.resolve(location);
        validateGoogleSheetsUri(nextUri);
        return nextUri;
    }

    private static String validateGoogleSheetsUri(URI uri) {
        if (uri == null) {
            throw new IllegalArgumentException("Source URL is required");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Auto-import source must use HTTPS");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Auto-import source must not contain user info");
        }
        if (uri.getPort() != -1 && uri.getPort() != 443) {
            throw new IllegalArgumentException("Auto-import source must use the default HTTPS port");
        }
        String host = uri.getHost();
        if (!GOOGLE_SHEETS_HOST.equalsIgnoreCase(host)) {
            throw new IllegalArgumentException("Auto-import source must be a docs.google.com Google Sheets URL");
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        Matcher idMatcher = SHEET_PATH_PATTERN.matcher(path);
        if (!idMatcher.matches()) {
            throw new IllegalArgumentException("Auto-import source must point to a Google Sheets spreadsheet");
        }
        return idMatcher.group(1);
    }

    private static String extractGid(URI sourceUri) {
        String queryGid = extractGid(sourceUri.getRawQuery());
        return queryGid != null ? queryGid : extractGid(sourceUri.getRawFragment());
    }

    private static String extractGid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Matcher gidMatcher = GID_PATTERN.matcher(value);
        return gidMatcher.find() ? gidMatcher.group(1) : null;
    }

    private static String buildErrorMessage(Exception cause) {
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            message = cause.getClass().getSimpleName();
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private AutoImportSettingsDto toDto(AutoImportSettings entity) {
        return AutoImportSettingsDto.builder()
                .enabled(entity.isEnabled())
                .sourceUrl(entity.getSourceUrl())
                .lastRunAt(entity.getLastRunAt())
                .lastStatus(entity.getLastStatus())
                .lastError(entity.getLastError())
                .lastImportedGroups(entity.getLastImportedGroups())
                .lastImportedLessons(entity.getLastImportedLessons())
                .updatedAt(entity.getUpdatedAt())
                .updatedBy(entity.getUpdatedBy())
                .nextRunAt(computeNextRunAt(entity.isEnabled()))
                .build();
    }
}
