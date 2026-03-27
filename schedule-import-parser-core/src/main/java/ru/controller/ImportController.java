package ru.controller;

import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.service.CsvImportService;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/internal/import")
public class ImportController {

    private final CsvImportService csvImportService;

    public ImportController(CsvImportService csvImportService) {
        this.csvImportService = csvImportService;
    }

    @PostMapping("/csv")
    public Map<String, Object> importCsv(@RequestParam MultipartFile file,
                                         @RequestHeader("X-Performed-By") String performedBy) throws Exception {
        int imported = csvImportService.importFromCsv(file.getInputStream());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");
        response.put("source", "csv");
        response.put("importedGroups", imported);
        response.put("performedBy", performedBy);
        return response;
    }
}
