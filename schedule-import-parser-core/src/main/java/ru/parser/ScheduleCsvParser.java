package ru.parser;

import com.opencsv.CSVReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Set<String> INSTRUCTORS = Set.of(
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

    public static List<Group> parse(InputStream is) throws Exception {
        CSVReader reader = new CSVReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        List<String[]> rows = reader.readAll();
        if (rows.size() < 2) return Collections.emptyList();

        String[] datesRow = rows.get(1);
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

                Day day = new Day();
                day.setDate(parseDate(datesRow[c]));
                if (activeCourseCode != null) {
                    day.getMeta().put("courseCode", activeCourseCode);
                }

                parseCell(row[c], day);

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

    private static void parseCell(String cell, Day day) {
        String[] lines = cell.split("\\n");

        int order = 1;
        boolean selfStudy = false;
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

            // Course code (allow trailing colon, e.g. "CS01:")
            String maybeCode = line.endsWith(":") ? line.substring(0, line.length() - 1).trim() : line;
            if (COURSE_CODE.matcher(maybeCode).matches()) {
                day.getMeta().put("courseCode", maybeCode);
                continue;
            }

            if (INSTRUCTORS.contains(line)) {
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
                List<String> found = findInstructors(text);
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

            List<String> instructorsInText = findInstructors(text);
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
                        title = consumeFollowingTitle(lines, i);
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

            lesson.setTitle(title);
            if (lecturer != null) lesson.setLecturer(lecturer);
            day.getLessons().add(lesson);
            pendingInstructor = null;
            inAssessment = false;
        }
    }

    private static String consumeFollowingTitle(String[] lines, int start) {
        StringBuilder sb = new StringBuilder();
        for (int j = start + 1; j < lines.length; j++) {
            String n = lines[j] == null ? "" : lines[j].trim();
            if (n.isEmpty()) continue;
            if (n.equals("СП")) break;
            if (INSTRUCTORS.contains(n)) break;
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

    private static List<String> findInstructors(String text) {
        List<String> result = new ArrayList<>();
        for (String instructor : INSTRUCTORS) {
            if (text.contains(instructor)) result.add(instructor);
        }
        return result;
    }

    private static String removeInstructor(String text, String instructor) {
        return text.replace(instructor, "").trim();
    }

    private static LocalDate parseDate(String s) {
        return DateParser.parse(s);
    }

    private static String preview(String cell) {
        String normalized = cell.replace("\r", " ").replace("\n", " ").trim();
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120);
    }
}
