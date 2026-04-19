package ru.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.client.ScheduleClient;
import ru.dto.WorkloadDto;
import ru.security.DownstreamAuthHeaderFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workload")
public class WorkloadController {

    private final ScheduleClient scheduleClient;
    private final DownstreamAuthHeaderFactory authHeaderFactory;

    public WorkloadController(ScheduleClient scheduleClient,
                              DownstreamAuthHeaderFactory authHeaderFactory) {
        this.scheduleClient = scheduleClient;
        this.authHeaderFactory = authHeaderFactory;
    }

    @GetMapping
    public List<WorkloadDto> getWorkload(Authentication authentication,
                                         @RequestParam(required = false) UUID instructorId,
                                         @RequestParam(required = false) LocalDate from,
                                         @RequestParam(required = false) LocalDate to) {
        return scheduleClient.getWorkload(authHeaderFactory.bearerHeader(authentication), instructorId, from, to);
    }
}
