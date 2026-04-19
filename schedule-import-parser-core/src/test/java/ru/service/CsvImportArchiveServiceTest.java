package ru.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CsvImportArchiveServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void stageUpload_createsArchiveDirectoryAndStoresIncomingCsv() throws Exception {
        CsvImportArchiveService service = new CsvImportArchiveService(tempDir.resolve("archive").toString());

        Path staged = service.stageUpload(new ByteArrayInputStream("group,lesson".getBytes()));

        assertThat(Files.exists(staged)).isTrue();
        assertThat(staged.getFileName().toString()).isEqualTo("incoming-schedule.csv.tmp");
        assertThat(Files.readString(staged)).isEqualTo("group,lesson");
    }

    @Test
    void backupAndPromote_manageCurrentAndPreviousFiles() throws Exception {
        CsvImportArchiveService service = new CsvImportArchiveService(tempDir.resolve("archive").toString());
        Files.createDirectories(service.getCurrentFile().getParent());
        Files.writeString(service.getCurrentFile(), "old-schedule");

        service.backupCurrentSource();
        Path staged = service.stageUpload(new ByteArrayInputStream("new-schedule".getBytes()));
        service.promoteToCurrent(staged);

        assertThat(Files.readString(service.getPreviousFile())).isEqualTo("old-schedule");
        assertThat(Files.readString(service.getCurrentFile())).isEqualTo("new-schedule");
        assertThat(Files.exists(staged)).isFalse();
    }

    @Test
    void cleanupStaged_deletesFileAndIgnoresNull() throws Exception {
        CsvImportArchiveService service = new CsvImportArchiveService(tempDir.resolve("archive").toString());
        Path staged = service.stageUpload(new ByteArrayInputStream("tmp".getBytes()));

        service.cleanupStaged(staged);
        service.cleanupStaged(null);

        assertThat(Files.exists(staged)).isFalse();
    }
}
