package ru.service;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import ru.dto.WorkloadCalendarDayDto;
import ru.dto.WorkloadCalendarDto;
import ru.dto.WorkloadCalendarLessonDto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class WorkloadExcelExportService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d.MMM", new Locale("ru", "RU"));
    private static final DateTimeFormatter WEEKDAY_FORMAT = DateTimeFormatter.ofPattern("EEE", new Locale("ru", "RU"));
    // Каждый день диапазона — отдельная колонка листа. В xlsx их максимум 16384, а без фильтра дат
    // вызывающий код подставляет заглушку 1900..3000 (~401000 дней) и POI падает. Держим разумный потолок.
    private static final int MAX_DAY_COLUMNS = 1000;

    public byte[] exportCalendars(List<WorkloadCalendarDto> calendars, LocalDate from, LocalDate to) {
        LocalDate[] window = resolveDateWindow(calendars, from, to);
        List<LocalDate> dates = enumerateDates(window[0], window[1]);
        List<WorkloadCalendarDto> sortedCalendars = calendars.stream()
                .sorted(Comparator.comparing(WorkloadCalendarDto::getInstructorName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet(resolveSheetName(sortedCalendars));
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dateStyle = createDateHeaderStyle(workbook);
            CellStyle instructorStyle = createInstructorStyle(workbook);
            CellStyle dayStyle = createDayStyle(workbook);
            CellStyle totalStyle = createTotalStyle(workbook);

            writeHeader(sheet, dates, headerStyle, dateStyle, totalStyle);
            writeInstructorRows(sheet, sortedCalendars, dates, instructorStyle, dayStyle, totalStyle);
            tuneSheet(sheet, dates.size());

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to build workload workbook", exception);
        }
    }

    private void writeHeader(XSSFSheet sheet,
                             List<LocalDate> dates,
                             CellStyle headerStyle,
                             CellStyle dateStyle,
                             CellStyle totalStyle) {
        Row weekdayRow = sheet.createRow(0);
        Row dateRow = sheet.createRow(1);

        createCell(weekdayRow, 0, "Имя Фамилия", headerStyle);
        createCell(dateRow, 0, "", dateStyle);

        for (int index = 0; index < dates.size(); index++) {
            LocalDate date = dates.get(index);
            createCell(weekdayRow, index + 1, capitalize(date.format(WEEKDAY_FORMAT)), headerStyle);
            createCell(dateRow, index + 1, date.format(DATE_FORMAT), dateStyle);
        }

        createCell(weekdayRow, dates.size() + 1, "ИТОГО", totalStyle);
        createCell(dateRow, dates.size() + 1, "", totalStyle);
    }

    private void writeInstructorRows(XSSFSheet sheet,
                                     List<WorkloadCalendarDto> calendars,
                                     List<LocalDate> dates,
                                     CellStyle instructorStyle,
                                     CellStyle dayStyle,
                                     CellStyle totalStyle) {
        int rowIndex = 2;
        for (WorkloadCalendarDto calendar : calendars) {
            Row row = sheet.createRow(rowIndex++);
            row.setHeightInPoints(58);

            createCell(row, 0, calendar.getInstructorName(), instructorStyle);

            Map<LocalDate, WorkloadCalendarDayDto> daysByDate = new LinkedHashMap<>();
            if (calendar.getDays() != null) {
                for (WorkloadCalendarDayDto day : calendar.getDays()) {
                    daysByDate.put(day.getDate(), day);
                }
            }

            for (int index = 0; index < dates.size(); index++) {
                LocalDate date = dates.get(index);
                createCell(row, index + 1, formatDay(daysByDate.get(date)), dayStyle);
            }

            createCell(row, dates.size() + 1, Integer.toString(calendar.getTotalHours()), totalStyle);
        }
    }

    private void tuneSheet(XSSFSheet sheet, int dateColumns) {
        sheet.createFreezePane(1, 2);
        sheet.setColumnWidth(0, 28 * 256);
        for (int index = 0; index < dateColumns; index++) {
            sheet.setColumnWidth(index + 1, 18 * 256);
        }
        sheet.setColumnWidth(dateColumns + 1, 10 * 256);
    }

    private String resolveSheetName(List<WorkloadCalendarDto> calendars) {
        if (calendars.size() == 1 && calendars.get(0).getInstructorName() != null && !calendars.get(0).getInstructorName().isBlank()) {
            return WorkbookUtil.createSafeSheetName(calendars.get(0).getInstructorName());
        }
        return WorkbookUtil.createSafeSheetName("Нагрузка");
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = baseBorderedStyle(workbook);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        return style;
    }

    private CellStyle createDateHeaderStyle(Workbook workbook) {
        CellStyle style = baseBorderedStyle(workbook);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createInstructorStyle(Workbook workbook) {
        CellStyle style = baseBorderedStyle(workbook);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(true);
        return style;
    }

    private CellStyle createDayStyle(Workbook workbook) {
        CellStyle style = baseBorderedStyle(workbook);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(true);
        return style;
    }

    private CellStyle createTotalStyle(Workbook workbook) {
        CellStyle style = baseBorderedStyle(workbook);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle baseBorderedStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private void createCell(Row row, int columnIndex, String value, CellStyle style) {
        Cell cell = row.createCell(columnIndex);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    private String formatDay(WorkloadCalendarDayDto day) {
        if (day == null || day.getLessons() == null || day.getLessons().isEmpty()) {
            return "";
        }

        return day.getLessons().stream()
                .sorted(Comparator.comparing(WorkloadCalendarLessonDto::getGroupCode, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(WorkloadCalendarLessonDto::getTitle, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(WorkloadCalendarLessonDto::getLessonId))
                .map(this::formatLesson)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String formatLesson(WorkloadCalendarLessonDto lesson) {
        return "%s: %s (%s ч)".formatted(
                defaultString(lesson.getGroupCode()),
                defaultString(lesson.getTitle()),
                lesson.getDurationHours()
        );
    }

    /**
     * Подбирает диапазон колонок листа. Если from/to не заданы (или охватывают заглушечные 1900..3000),
     * сужаем окно до реальных дат занятий; сверху ограничиваем {@link #MAX_DAY_COLUMNS}, чтобы не упереться
     * в лимит колонок xlsx.
     */
    private LocalDate[] resolveDateWindow(List<WorkloadCalendarDto> calendars, LocalDate from, LocalDate to) {
        LocalDate dataMin = null;
        LocalDate dataMax = null;
        for (WorkloadCalendarDto calendar : calendars) {
            if (calendar.getDays() == null) {
                continue;
            }
            for (WorkloadCalendarDayDto day : calendar.getDays()) {
                LocalDate date = day.getDate();
                if (date == null) {
                    continue;
                }
                if (dataMin == null || date.isBefore(dataMin)) {
                    dataMin = date;
                }
                if (dataMax == null || date.isAfter(dataMax)) {
                    dataMax = date;
                }
            }
        }

        LocalDate effectiveFrom = from;
        LocalDate effectiveTo = to;

        boolean spanTooWide = effectiveFrom == null || effectiveTo == null
                || ChronoUnit.DAYS.between(effectiveFrom, effectiveTo) > MAX_DAY_COLUMNS;
        if (spanTooWide) {
            effectiveFrom = dataMin != null ? dataMin : (effectiveFrom != null ? effectiveFrom : LocalDate.now());
            effectiveTo = dataMax != null ? dataMax : effectiveFrom;
        }

        if (effectiveFrom == null) {
            effectiveFrom = LocalDate.now();
        }
        if (effectiveTo == null || effectiveTo.isBefore(effectiveFrom)) {
            effectiveTo = effectiveFrom;
        }
        if (ChronoUnit.DAYS.between(effectiveFrom, effectiveTo) > MAX_DAY_COLUMNS) {
            effectiveTo = effectiveFrom.plusDays(MAX_DAY_COLUMNS);
        }
        return new LocalDate[]{effectiveFrom, effectiveTo};
    }

    private List<LocalDate> enumerateDates(LocalDate from, LocalDate to) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            dates.add(cursor);
            cursor = cursor.plusDays(1);
        }
        return dates;
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
