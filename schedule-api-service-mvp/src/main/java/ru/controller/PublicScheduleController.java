package ru.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.client.CoreClient;
import ru.dto.ScheduleEntryDto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/public")
public class PublicScheduleController {

    private final CoreClient coreClient;

    public PublicScheduleController(CoreClient coreClient) {
        this.coreClient = coreClient;
    }

    @GetMapping("/schedule")
    public List<ScheduleEntryDto> getSchedule(
            @RequestParam(required = false) String groupCode,
            @RequestParam(required = false) UUID instructorId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to
    ) {
        return coreClient.getPublicSchedule(groupCode, instructorId, from, to);
    }
}
