package ru.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.client.ScheduleClient;
import ru.dto.WorkloadDto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workload")
public class WorkloadController {

    private final ScheduleClient scheduleClient;

    public WorkloadController(ScheduleClient scheduleClient) {
        this.scheduleClient = scheduleClient;
    }

    @GetMapping
    public List<WorkloadDto> getWorkload(@RequestHeader("Authorization") String authorization,
                                         @RequestParam(required = false) UUID instructorId,
                                         @RequestParam(required = false) LocalDate from,
                                         @RequestParam(required = false) LocalDate to) {
        return scheduleClient.getWorkload(authorization, instructorId, from, to);
    }
}
