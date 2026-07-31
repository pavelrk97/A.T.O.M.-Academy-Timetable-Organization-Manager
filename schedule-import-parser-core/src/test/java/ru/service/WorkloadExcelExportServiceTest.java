package ru.service;

import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import ru.dto.WorkloadCalendarDayDto;
import ru.dto.WorkloadCalendarDto;
import ru.dto.WorkloadCalendarLessonDto;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WorkloadExcelExportServiceTest {

    @Test
    void exportCalendars_buildsWorkbookWithWeekdayAndDateHeaders() throws Exception {
        WorkloadExcelExportService service = new WorkloadExcelExportService();
        WorkloadCalendarDto calendar = WorkloadCalendarDto.builder()
                .instructorId(UUID.randomUUID())
                .instructorName("Меняйло Илья Евгеньевич")
                .from(LocalDate.of(2026, 1, 12))
                .to(LocalDate.of(2026, 1, 13))
                .totalHours(6)
                .days(List.of(
                        WorkloadCalendarDayDto.builder()
                                .dayId(UUID.randomUUID())
                                .date(LocalDate.of(2026, 1, 12))
                                .totalHours(4)
                                .lessons(List.of(WorkloadCalendarLessonDto.builder()
                                        .lessonId(UUID.randomUUID())
                                        .groupCode("гр.16")
                                        .title("Лекция")
                                        .durationHours(4)
                                        .build()))
                                .build(),
                        WorkloadCalendarDayDto.builder()
                                .dayId(UUID.randomUUID())
                                .date(LocalDate.of(2026, 1, 13))
                                .totalHours(2)
                                .lessons(List.of(WorkloadCalendarLessonDto.builder()
                                        .lessonId(UUID.randomUUID())
                                        .groupCode("гр.18")
                                        .title("Практика")
                                        .durationHours(2)
                                        .build()))
                                .build()
                ))
                .build();

        byte[] workbookBytes = service.exportCalendars(
                List.of(calendar),
                LocalDate.of(2026, 1, 12),
                LocalDate.of(2026, 1, 13)
        );

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(workbookBytes))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("Меняйло Илья Евгеньевич");
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Имя Фамилия");
            assertThat(sheet.getRow(0).getCell(3).getStringCellValue()).isEqualTo("ИТОГО");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("12.янв.");
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("Меняйло Илья Евгеньевич");
            assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).contains("гр.16: Лекция (4 ч)");
            assertThat(sheet.getRow(2).getCell(2).getStringCellValue()).contains("гр.18: Практика (2 ч)");
            assertThat(sheet.getRow(2).getCell(3).getStringCellValue()).isEqualTo("6");
        }
    }

    @Test
    void exportCalendars_withoutDateFilter_shrinksToDataInsteadOfExceedingColumnLimit() throws Exception {
        WorkloadExcelExportService service = new WorkloadExcelExportService();
        WorkloadCalendarDto calendar = WorkloadCalendarDto.builder()
                .instructorId(UUID.randomUUID())
                .instructorName("Иванов Иван")
                .totalHours(4)
                .days(List.of(WorkloadCalendarDayDto.builder()
                        .dayId(UUID.randomUUID())
                        .date(LocalDate.of(2026, 8, 3))
                        .totalHours(4)
                        .lessons(List.of(WorkloadCalendarLessonDto.builder()
                                .lessonId(UUID.randomUUID())
                                .groupCode("гр.1")
                                .title("Лекция")
                                .durationHours(4)
                                .build()))
                        .build()))
                .build();

        // Заглушечный диапазон, который раньше давал ~401000 колонок и падение POI (Invalid column index).
        byte[] workbookBytes = service.exportCalendars(
                List.of(calendar),
                LocalDate.of(1900, 1, 1),
                LocalDate.of(3000, 12, 31)
        );

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(workbookBytes))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("3.авг.");
            assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).contains("гр.1: Лекция (4 ч)");
            assertThat(sheet.getRow(2).getCell(2).getStringCellValue()).isEqualTo("4");
        }
    }
}
