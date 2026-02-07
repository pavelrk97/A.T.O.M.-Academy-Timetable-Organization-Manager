package ru.schedule.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.schedule.model.Group;
import ru.schedule.parser.ScheduleCsvParser;

import java.util.*;

@RestController
public class ImportController {

    @PostMapping("/import")
    public Map<String, Object> importSchedule(@RequestParam MultipartFile file) throws Exception {
        List<Group> groups = ScheduleCsvParser.parse(file.getInputStream());

        Map<String, Object> response = new HashMap<>();
        response.put("groups", groups);
        response.put("groupCount", groups.size());

        return response;
    }
}
