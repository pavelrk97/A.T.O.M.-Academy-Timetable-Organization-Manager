package ru.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(ImportController.class);

    private final CsvImportService csvImportService;

    public ImportController(CsvImportService csvImportService) {
        this.csvImportService = csvImportService;
    }

    @PostMapping("/csv")
    public Map<String, Object> importCsv(@RequestParam MultipartFile file,
                                         @RequestHeader("X-Performed-By") String performedBy) throws Exception {
        long startedAt = System.nanoTime();
        log.info("Internal CSV import started: performedBy={}, filename={}, sizeBytes={}",
                performedBy, file.getOriginalFilename(), file.getSize());

        int imported = csvImportService.importFromCsv(file.getInputStream());
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;

        log.info("Internal CSV import finished: performedBy={}, filename={}, importedGroups={}, durationMs={}",
                performedBy, file.getOriginalFilename(), imported, durationMs);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");
        response.put("source", "csv");
        response.put("importedGroups", imported);
        response.put("performedBy", performedBy);
        return response;
    }
}
