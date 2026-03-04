package ru.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.service.JsonImportService;

import java.util.*;

@RestController
public class ImportController {

    private final JsonImportService jsonImportService;

    public ImportController(JsonImportService jsonImportService) {
        this.jsonImportService = jsonImportService;
    }

    @PostMapping("/import")
    public Map<String, Object> importJson(@RequestParam MultipartFile file) throws Exception {
        jsonImportService.importFromJson(file.getInputStream());

        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        return response;
    }
}

