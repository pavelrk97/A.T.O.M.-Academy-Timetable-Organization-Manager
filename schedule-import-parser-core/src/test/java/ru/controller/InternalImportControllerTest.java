package ru.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import ru.config.SecurityConfig;
import ru.service.CsvImportService;

import java.io.InputStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ImportController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "internal.security.api-key=test-schedule-key")
class InternalImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CsvImportService csvImportService;

    @Test
    void importCsv_rejectsMissingInternalApiKey() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "schedule.csv", "text/csv", "a,b".getBytes());

        mockMvc.perform(multipart("/internal/import/csv")
                        .file(file)
                        .header("X-Performed-By", "admin"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void importCsv_rejectsWrongInternalApiKey() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "schedule.csv", "text/csv", "a,b".getBytes());

        mockMvc.perform(multipart("/internal/import/csv")
                        .file(file)
                        .header("X-Performed-By", "admin")
                        .header("X-Internal-Api-Key", "wrong-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void importCsv_acceptsValidInternalApiKey() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "schedule.csv", "text/csv", "a,b".getBytes());
        when(csvImportService.importFromCsv(any(InputStream.class))).thenReturn(2);

        mockMvc.perform(multipart("/internal/import/csv")
                        .file(file)
                        .header("X-Performed-By", "admin")
                        .header("X-Internal-Api-Key", "test-schedule-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.source").value("csv"))
                .andExpect(jsonPath("$.importedGroups").value(2))
                .andExpect(jsonPath("$.performedBy").value("admin"));

        verify(csvImportService).importFromCsv(any(InputStream.class));
    }
}
