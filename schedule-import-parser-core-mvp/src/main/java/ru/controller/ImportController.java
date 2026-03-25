package ru.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.service.JsonImportService;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/import")
public class ImportController {

    private final JsonImportService jsonImportService;

    public ImportController(JsonImportService jsonImportService) {
        this.jsonImportService = jsonImportService;
    }

    @PostMapping("/json")
    public Map<String, Object> importJson(@RequestParam MultipartFile file, Authentication authentication) throws Exception {
        int imported = jsonImportService.importFromJson(file.getInputStream());
        return response("json", imported, authentication.getName());
    }

    @PostMapping("/csv")
    public Map<String, Object> importCsv(@RequestParam MultipartFile file, Authentication authentication) throws Exception {
        int imported = jsonImportService.importFromCsv(file.getInputStream());
        return response("csv", imported, authentication.getName());
    }

    private Map<String, Object> response(String source, int imported, String username) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");
        response.put("source", source);
        response.put("importedGroups", imported);
        response.put("performedBy", username);
        return response;
    }
}
