package ru.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
public class CsvImportArchiveService {

    private final Path archiveDirectory;

    public CsvImportArchiveService(@Value("${atom.import.archive-dir:${user.home}/atom-import-archive}") String archiveDirectory) {
        this.archiveDirectory = Path.of(archiveDirectory);
    }

    public Path stageUpload(InputStream csvStream) throws IOException {
        Files.createDirectories(archiveDirectory);
        Path stagedFile = archiveDirectory.resolve("incoming-schedule.csv.tmp");
        Files.copy(csvStream, stagedFile, StandardCopyOption.REPLACE_EXISTING);
        return stagedFile;
    }

    public void backupCurrentSource() throws IOException {
        Files.createDirectories(archiveDirectory);
        Path currentFile = getCurrentFile();
        if (!Files.exists(currentFile)) {
            return;
        }

        Files.copy(currentFile, getPreviousFile(), StandardCopyOption.REPLACE_EXISTING);
    }

    public void promoteToCurrent(Path stagedFile) throws IOException {
        Files.createDirectories(archiveDirectory);
        Files.move(stagedFile, getCurrentFile(), StandardCopyOption.REPLACE_EXISTING);
    }

    public void cleanupStaged(Path stagedFile) {
        if (stagedFile == null) {
            return;
        }

        try {
            Files.deleteIfExists(stagedFile);
        } catch (IOException ignored) {
            // nothing critical here, next import will overwrite the staged file
        }
    }

    public Path getCurrentFile() {
        return archiveDirectory.resolve("current-schedule.csv");
    }

    public Path getPreviousFile() {
        return archiveDirectory.resolve("previous-schedule.csv");
    }
}
