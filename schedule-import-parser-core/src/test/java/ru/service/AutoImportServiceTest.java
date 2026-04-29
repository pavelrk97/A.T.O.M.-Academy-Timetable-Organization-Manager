package ru.service;

import org.junit.jupiter.api.Test;
import ru.dto.AutoImportSettingsUpdateRequest;
import ru.model.AutoImportSettings;
import ru.repository.AutoImportSettingsRepository;

import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutoImportServiceTest {

    @Test
    void buildExportUri_acceptsGoogleSheetsUrlAndKeepsGid() throws Exception {
        URI exportUri = AutoImportService.buildExportUri(
                "https://docs.google.com/spreadsheets/d/sheet_123-ABC/edit?usp=sharing&gid=987#gid=987");

        assertThat(exportUri.toString())
                .isEqualTo("https://docs.google.com/spreadsheets/d/sheet_123-ABC/export?format=csv&gid=987");
    }

    @Test
    void buildExportUri_acceptsGoogleSheetsUrlWithGidInFragment() throws Exception {
        URI exportUri = AutoImportService.buildExportUri(
                "https://docs.google.com/spreadsheets/d/sheet_123-ABC/edit#gid=42");

        assertThat(exportUri.toString())
                .isEqualTo("https://docs.google.com/spreadsheets/d/sheet_123-ABC/export?format=csv&gid=42");
    }

    @Test
    void buildExportUri_rejectsHttpUrl() {
        assertThatThrownBy(() -> AutoImportService.buildExportUri(
                "http://docs.google.com/spreadsheets/d/sheet_123-ABC/edit"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    void buildExportUri_rejectsLocalhostUrl() {
        assertThatThrownBy(() -> AutoImportService.buildExportUri(
                "https://localhost/spreadsheets/d/sheet_123-ABC/edit"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("docs.google.com");
    }

    @Test
    void buildExportUri_rejectsPrivateIpUrl() {
        assertThatThrownBy(() -> AutoImportService.buildExportUri(
                "https://10.0.0.5/spreadsheets/d/sheet_123-ABC/edit"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("docs.google.com");
    }

    @Test
    void buildExportUri_rejectsDockerHostname() {
        assertThatThrownBy(() -> AutoImportService.buildExportUri(
                "https://schedule-service/spreadsheets/d/sheet_123-ABC/edit"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("docs.google.com");
    }

    @Test
    void buildExportUri_rejectsLookalikeHost() {
        assertThatThrownBy(() -> AutoImportService.buildExportUri(
                "https://docs.google.com.evil.test/spreadsheets/d/sheet_123-ABC/edit"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("docs.google.com");
    }

    @Test
    void buildExportUri_rejectsNonSpreadsheetPath() {
        assertThatThrownBy(() -> AutoImportService.buildExportUri(
                "https://docs.google.com/document/d/sheet_123-ABC/edit"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("spreadsheet");
    }

    @Test
    void updateSettings_rejectsUnsafeSourceUrlBeforeSave() {
        AutoImportSettingsRepository repository = mock(AutoImportSettingsRepository.class);
        CsvImportService csvImportService = mock(CsvImportService.class);
        AutoImportSettings settings = new AutoImportSettings();
        settings.setId(AutoImportSettings.SINGLETON_ID);
        settings.setSourceUrl("");

        when(repository.findById(AutoImportSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));
        AutoImportService service = new AutoImportService(repository, csvImportService, "test-agent");

        assertThatThrownBy(() -> service.updateSettings(
                new AutoImportSettingsUpdateRequest(true, "http://localhost/internal.csv"), "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");

        verify(repository, never()).save(any(AutoImportSettings.class));
    }
}
