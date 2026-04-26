package ru.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.model.AutoImportSettings;

public interface AutoImportSettingsRepository extends JpaRepository<AutoImportSettings, Long> {
}
