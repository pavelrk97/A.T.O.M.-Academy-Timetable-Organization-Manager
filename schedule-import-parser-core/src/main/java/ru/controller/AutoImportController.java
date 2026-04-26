package ru.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.dto.AutoImportSettingsDto;
import ru.dto.AutoImportSettingsUpdateRequest;
import ru.service.AutoImportService;

@RestController
@RequestMapping("/api/auto-import")
public class AutoImportController {

    private static final Logger log = LoggerFactory.getLogger(AutoImportController.class);

    private final AutoImportService autoImportService;

    public AutoImportController(AutoImportService autoImportService) {
        this.autoImportService = autoImportService;
    }

    @GetMapping("/settings")
    public AutoImportSettingsDto getSettings() {
        return autoImportService.getSettings();
    }

    @PutMapping("/settings")
    public AutoImportSettingsDto updateSettings(@RequestBody AutoImportSettingsUpdateRequest request,
                                                Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "anonymous";
        log.info("Auto-import settings update: enabled={}, by={}", request.isEnabled(), username);
        return autoImportService.updateSettings(request, username);
    }

    @PostMapping("/run")
    public AutoImportSettingsDto runNow(Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "anonymous";
        log.info("Auto-import manual run requested by={}", username);
        return autoImportService.runImport(username);
    }
}
