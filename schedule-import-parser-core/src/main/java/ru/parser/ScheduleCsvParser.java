package ru.parser;

import com.opencsv.CSVReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.exception.ScheduleParseException;
import ru.model.Day;
import ru.model.Group;
import ru.model.Lesson;
import ru.model.LessonType;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScheduleCsvParser {

    private static final Logger log = LoggerFactory.getLogger(ScheduleCsvParser.class);

    private static final Pattern DURATION = Pattern.compile("\\((\\d+)\\s*ч\\)");
    private static final Pattern COURSE_CODE = Pattern.compile("^[A-Z&]{1,5}\\d{2}$");

    /**
     * Fallback list of instructor full-names. Используется, если вызывающий код не передал
     * динамический список. Нужен для unit-тестов и обратной совместимости — production-код
     * (CsvImportService) сейчас передаёт сюда динамический список из users.canTeach=true,
     * объединённый с этим default'ом.
     */
    private static final Set<String> DEFAULT_INSTRUCTORS = Set.of(
            "Бращенко","Волкова","Майстренко","Мухамбеталин","Трушейкин","Брянский",
            "Коновалов","Костылев","Алексеева","Голубенко","Гонтов","Иванов",
            "Кадчик","Канищев","Ким","Иванов С","Смирнов","Климов","Павленко",
            "Алексеев","Виноградов","Гончаров","Корепанова","Меняйло","Расписенко",
            "Шорохов","Вакуров","Бунда","Вишняков","Егоров","Коваленко","Баринов",
            "Киблер","Левковицкая","Фарейтор","Чирков","Климова","Салимжанова",
            "Ивахно","Короткова","Меркель","Кузнецов Д","Харламова","Загузин",
            "Лошманов","Name"
    );

    private static final Set<String> ASSESSMENT_TITLES = Set.of(
            "Промежуточный контроль",
            "Intermediate Examination",
            "Entry Level Test",
            "Entermidiate examination",
            "Examination"
    );

    /** Default fallback instructor list (immutable view). */
    public static Set<String> defaultInstructors() {
        return DEFAULT_INSTRUCTORS;
    }

    /**
     * Parses CSV using {@link #DEFAULT_INSTRUCTORS} as the recognised instructor name list.
     * Prefer the overload that takes an explicit set when running with live data.
     */
    public static List<Group> parse(InputStream is) throws Exception {
        return parse(is, DEFAULT_INSTRUCTORS);
    }

    /**
     * Parses CSV using {@code instructors} as the recognised instructor name list.
     * Caller is expected to provide the canonical list (e.g. union of users.canTeach=true
     * and {@link #DEFAULT_INSTRUCTORS}). The set must contain {@link String#equals exact}
     * full-names as they appear in the CSV.
     */
    public static List<Group> parse(InputStream is, Set<String> instructors) throws Exception {
        if (instructors == null || instructors.isEmpty()) {
            instructors = DEFAULT_INSTRUCTORS;
        }
        CSVReader reader = new CSVReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        List<String[]> rows = reader.readAll();
        if (rows.size() < 2) return Collections.emptyList();

        String[] datesRow = rows.get(1);
        // Sanity-check: в правильном CSV расписания во второй строке лежат даты вида "1.янв.".
        // Если ни одна ячейка датного ряда не разбирается как дата — это сводная вкладка
        // (нагрузка по месяцам) или другая «не та» страница. Тихо импортировать пустоту нельзя:
        // пользователь забудет добавить gid в URL и думает, что всё ОК.
        if (!hasAnyParseableDate(datesRow)) {
            throw new ScheduleParseException(
                    "CSV не содержит ни одной валидной даты в строке заголовков. "
                            + "Проверь, что URL ведёт на вкладку с расписанием (нужен параметр gid в URL — "
                            + "скопируй ссылку из адресной строки браузера, ссылка из «Поделиться» указывает "
                            + "на первую вкладку).");
        }
        List<Group> result = new ArrayList<>();
        String activeCourseCode = null;

        for (int r = 2; r < rows.size(); r++) {
            String[] row = rows.get(r);
            if (row.length == 0 || row[0].isBlank()) continue;

            Group group = parseGroupHeader(row[0]);
            result.add(group);

            for (int c = 1; c < row.length; c++) {
                if (row[c] == null || row[c].trim().isEmpty()) continue;
                if (c >= datesRow.length || datesRow[c] == null || datesRow[c].isBlank()) {
                    log.warn("Skipping CSV cell without date header: group={}, column={}, preview={}",
                            group.getCode(),
                            c,
                            preview(row[c]));
                    continue;
                }

                LocalDate date;
                try {
                    date = parseDate(datesRow[c]);
                } catch (RuntimeException ex) {
                    // Если в шапке столбца не настоящая дата (напр. «янв.» — заголовок месяца
                    // из Google Sheets), пропускаем колонку, а не валим весь импорт. Иначе одна
                    // мусорная ячейка ломает скачивание всего расписания.
                    log.warn("Skipping CSV column with unparseable date header: group={}, column={}, header='{}', cell preview={}",
                            group.getCode(),
                            c,
                            datesRow[c],
                            preview(row[c]));
                    continue;
                }

                Day day = new Day();
                day.setDate(date);
                if (activeCourseCode != null) {
                    day.getMeta().put("courseCode", activeCourseCode);
                }

                parseCell(row[c], day, instructors);

                if (!day.getMeta().containsKey("courseCode")) continue;

                activeCourseCode = day.getMeta().get("courseCode");
                group.getDays().add(day);
            }
        }

        return result;
    }

    private static Group parseGroupHeader(String cell) {
        Group g = new Group();
        String[] lines = cell.split("\\n");
        g.setCode(lines[0].trim());
        g.setLocation(lines.length > 1 ? lines[1].trim() : null);
        return g;
    }

    private static void parseCell(String cell, Day day, Set<String> instructors) {
        String[] lines = cell.split("\\n");

        int order = 1;
        boolean selfStudy = false;
        boolean businessTrip = false;
        boolean inAssessment = false;
        Lesson currentAssessment = null;
        String pendingInstructor = null;
        StringBuilder pendingTitle = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.isEmpty()) continue;

            if (line.equals("СП")) {
                selfStudy = true;
                continue;
            }

            String maybeTrip = line.endsWith(":") ? line.substring(0, line.length() - 1).trim() : line;
            if (maybeTrip.equalsIgnoreCase("TRIP")) {
                businessTrip = true;
                continue;
            }

            // Course code (allow trailing colon, e.g. "CS01:")
            String maybeCode = line.endsWith(":") ? line.substring(0, line.length() - 1).trim() : line;
            if (COURSE_CODE.matcher(maybeCode).matches()) {
                day.getMeta().put("courseCode", maybeCode);
                continue;
            }

            if (instructors.contains(line)) {
                pendingInstructor = line;
                continue;
            }

            // Detect duration in line (if any) and compute the line text without that duration token
            Matcher dm = DURATION.matcher(line);
            boolean hasDuration = dm.find();
            String lineWithoutDuration = hasDuration
                    ? (line.substring(0, dm.start()) + line.substring(dm.end())).trim()
                    : line;

            // Assessment match by substring (covers "Intermediate Examination (пересдача)",
            // "Intermediate examination (8ч)", "Промежуточный контроль (8ч)" etc.)
            String assessmentTitle = matchesAssessment(lineWithoutDuration);
            if (assessmentTitle != null) {
                Lesson l = new Lesson();
                l.setOrderNumber(order++);
                l.setTitle(assessmentTitle);
                l.setType(LessonType.ASSESSMENT);
                l.setBusinessTrip(businessTrip);
                l.setDurationHours(hasDuration ? Integer.parseInt(dm.group(1)) : 0);
                l.setLecturers(new ArrayList<>());
                if (pendingInstructor != null) {
                    l.getLecturers().add(pendingInstructor);
                }
                day.getLessons().add(l);
                currentAssessment = l;
                inAssessment = true;
                pendingInstructor = null;
                pendingTitle.setLength(0);
                continue;
            }

            if (!hasDuration) {
                // Plain text line — accumulate as title for the next "(Nч)" line
                if (pendingTitle.length() > 0) pendingTitle.append(' ');
                pendingTitle.append(line);
                continue;
            }

            int hours = Integer.parseInt(dm.group(1));
            String text = lineWithoutDuration;

            if (inAssessment && currentAssessment != null) {
                List<String> found = findInstructors(text, instructors);
                if (found.isEmpty() && pendingInstructor != null) {
                    found = List.of(pendingInstructor);
                }
                for (String ins : found) {
                    if (!currentAssessment.getLecturers().contains(ins)) {
                        currentAssessment.getLecturers().add(ins);
                    }
                }
                if (currentAssessment.getDurationHours() == 0) {
                    currentAssessment.setDurationHours(hours);
                }
                pendingInstructor = null;
                continue;
            }

            Lesson lesson = new Lesson();
            lesson.setOrderNumber(order++);
            lesson.setDurationHours(hours);
            lesson.setType(selfStudy ? LessonType.SELF_STUDY : LessonType.LECTURE);
            lesson.setBusinessTrip(businessTrip);

            List<String> instructorsInText = findInstructors(text, instructors);
            String title;
            String lecturer = null;

            if (!instructorsInText.isEmpty()) {
                lecturer = instructorsInText.get(0);
                String titleFromText = removeInstructor(text, lecturer).trim();
                if (titleFromText.isEmpty()) {
                    // Format B: "Instructor (Nч)" with no title on this line.
                    // Title is either accumulated above (pendingTitle) or coming on the next plain-text lines.
                    if (pendingTitle.length() > 0) {
                        title = pendingTitle.toString();
                        pendingTitle.setLength(0);
                    } else {
                        title = consumeFollowingTitle(lines, i, instructors);
                    }
                } else {
                    title = pendingTitle.length() > 0
                            ? pendingTitle + " " + titleFromText
                            : titleFromText;
                    pendingTitle.setLength(0);
                }
            } else {
                // Format A: "<Title> (Nч)" with no instructor in text — lecturer was on prev line.
                title = pendingTitle.length() > 0
                        ? pendingTitle + " " + text
                        : text;
                pendingTitle.setLength(0);
                if (pendingInstructor != null) {
                    lecturer = pendingInstructor;
                }
            }

            if (businessTrip && title.isBlank()) {
                title = "TRIP";
            }
            lesson.setTitle(title);
            if (lecturer != null) lesson.setLecturer(lecturer);
            day.getLessons().add(lesson);
            pendingInstructor = null;
            inAssessment = false;
        }
    }

    private static String consumeFollowingTitle(String[] lines, int start, Set<String> instructors) {
        StringBuilder sb = new StringBuilder();
        for (int j = start + 1; j < lines.length; j++) {
            String n = lines[j] == null ? "" : lines[j].trim();
            if (n.isEmpty()) continue;
            if (n.equals("СП")) break;
            if (instructors.contains(n)) break;
            String maybe = n.endsWith(":") ? n.substring(0, n.length() - 1).trim() : n;
            if (COURSE_CODE.matcher(maybe).matches()) break;
            if (DURATION.matcher(n).find()) break;
            if (matchesAssessment(n) != null) break;
            if (sb.length() > 0) sb.append(' ');
            sb.append(n);
            lines[j] = ""; // mark consumed so the outer loop skips it
        }
        return sb.toString();
    }

    private static String matchesAssessment(String line) {
        if (line == null || line.isEmpty()) return null;
        String lower = line.toLowerCase();
        for (String key : ASSESSMENT_TITLES) {
            if (lower.contains(key.toLowerCase())) return line;
        }
        return null;
    }

    /**
     * Находит имена инструкторов как подстроки в тексте, разрешая коллизии префиксов
     * (например, "Иванов" является префиксом "Иванов С"): сортируем кандидатов по длине
     * по убыванию и пропускаем имя, если оно уже покрыто более длинным совпадением.
     */
    private static List<String> findInstructors(String text, Set<String> instructors) {
        List<String> sorted = instructors.stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        List<String> result = new ArrayList<>();
        for (String name : sorted) {
            if (name == null || name.isEmpty()) continue;
            if (!text.contains(name)) continue;
            boolean coveredByLonger = false;
            for (String existing : result) {
                if (existing.contains(name)) {
                    coveredByLonger = true;
                    break;
                }
            }
            if (!coveredByLonger) result.add(name);
        }
        return result;
    }

    private static String removeInstructor(String text, String instructor) {
        return text.replace(instructor, "").trim();
    }

    private static LocalDate parseDate(String s) {
        return DateParser.parse(s);
    }

    /**
     * True если в строке-заголовке дат есть хотя бы одна ячейка, которую
     * {@link DateParser#parse} распознаёт как дату. Используется как ранний
     * sanity-check, чтобы не допустить «успешный» импорт сводной вкладки или
     * любого другого CSV без дат.
     */
    private static boolean hasAnyParseableDate(String[] datesRow) {
        if (datesRow == null) return false;
        for (String cell : datesRow) {
            if (cell == null || cell.isBlank()) continue;
            try {
                DateParser.parse(cell);
                return true;
            } catch (RuntimeException ignored) {
                // ячейка не дата — пробуем следующую
            }
        }
        return false;
    }

    private static String preview(String cell) {
        String normalized = cell.replace("\r", " ").replace("\n", " ").trim();
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120);
    }
}
