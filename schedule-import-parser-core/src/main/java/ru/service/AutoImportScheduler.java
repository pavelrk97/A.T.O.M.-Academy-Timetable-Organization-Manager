package ru.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.dto.AutoImportSettingsDto;

@Component
public class AutoImportScheduler {

    public static final String SCHEDULER_TRIGGER = "scheduler";

    private static final Logger log = LoggerFactory.getLogger(AutoImportScheduler.class);

    private final AutoImportService autoImportService;

    public AutoImportScheduler(AutoImportService autoImportService) {
        this.autoImportService = autoImportService;
    }

    /**
     * Каждый день в 13:00 и 23:00 по Москве запускаем авто-импорт, если включён.
     */
    @Scheduled(cron = "0 0 13,23 * * *", zone = "Europe/Moscow")
    public void runScheduledImport() {
        AutoImportSettingsDto settings = autoImportService.getSettings();
        if (!settings.isEnabled()) {
            log.debug("Auto-import scheduler tick skipped: feature disabled");
            return;
        }
        log.info("Auto-import scheduler tick: starting run");
        autoImportService.runImport(SCHEDULER_TRIGGER);
    }
}
