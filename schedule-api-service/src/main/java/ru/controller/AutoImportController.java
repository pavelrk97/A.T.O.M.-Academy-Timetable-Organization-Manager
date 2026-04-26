package ru.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.client.ScheduleClient;
import ru.dto.AutoImportSettingsDto;
import ru.dto.AutoImportSettingsUpdateRequest;
import ru.security.DownstreamAuthHeaderFactory;

@RestController
@RequestMapping("/api/auto-import")
public class AutoImportController {

    private final ScheduleClient scheduleClient;
    private final DownstreamAuthHeaderFactory authHeaderFactory;

    public AutoImportController(ScheduleClient scheduleClient,
                                DownstreamAuthHeaderFactory authHeaderFactory) {
        this.scheduleClient = scheduleClient;
        this.authHeaderFactory = authHeaderFactory;
    }

    @GetMapping("/settings")
    public AutoImportSettingsDto getSettings(Authentication authentication) {
        return scheduleClient.getAutoImportSettings(authHeaderFactory.bearerHeader(authentication));
    }

    @PutMapping("/settings")
    public AutoImportSettingsDto updateSettings(Authentication authentication,
                                                @RequestBody AutoImportSettingsUpdateRequest request) {
        return scheduleClient.updateAutoImportSettings(
                authHeaderFactory.bearerHeader(authentication), request);
    }

    @PostMapping("/run")
    public AutoImportSettingsDto runNow(Authentication authentication) {
        return scheduleClient.runAutoImport(authHeaderFactory.bearerHeader(authentication));
    }
}
