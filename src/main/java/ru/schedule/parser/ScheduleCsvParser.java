package ru.schedule.parser;

import com.opencsv.CSVReader;
import ru.schedule.model.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import java.time.Month;
import ru.schedule.parser.DateParser;

public class ScheduleCsvParser {

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
            "Entermidiate examination",
            "Examination"
    );

    private static final Map<String, Month> MONTHS = Map.ofEntries(
            Map.entry("янв", Month.JANUARY),
            Map.entry("февр", Month.FEBRUARY),
            Map.entry("мар", Month.MARCH),
            Map.entry("апр", Month.APRIL),
            Map.entry("мая", Month.MAY),
            Map.entry("июн", Month.JUNE),
            Map.entry("июл", Month.JULY),
            Map.entry("авг", Month.AUGUST),
            Map.entry("сент", Month.SEPTEMBER),
            Map.entry("окт", Month.OCTOBER),
            Map.entry("нояб", Month.NOVEMBER),
            Map.entry("дек", Month.DECEMBER)
    );

    public static List<Group> parse(InputStream is) throws Exception {
        CSVReader reader = new CSVReader(
                new InputStreamReader(is, StandardCharsets.UTF_8)
        );
        List<String[]> rows = reader.readAll();

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

                Day day = new Day();
                day.setDate(DateParser.parse(datesRow[c]));

                if (activeCourseCode != null) {
                    day.getMeta().put("courseCode", activeCourseCode);
                }

                parseCell(row[c], day);

                if (!day.getMeta().containsKey("courseCode")) {
                    continue;
                }

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

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            if (line.equals("СП")) {
                selfStudy = true;
                continue;
            }

            if (COURSE_CODE.matcher(line).matches()) {
                day.getMeta().put("courseCode", line);
                continue;
            }

            if (isPureInstructor(line)) {
                pendingInstructor = line;
                continue;
            }

            if (ASSESSMENT_TITLES.contains(line)) {
                Lesson l = new Lesson();
                l.setOrderNumber(order++);
                l.setTitle(line);
                l.setType(LessonType.ASSESSMENT);
                l.setDurationHours(0);
                l.setLecturers(new ArrayList<>());

                day.getLessons().add(l);
                currentAssessment = l;
                inAssessment = true;
                pendingInstructor = null;
                continue;
            }

            Matcher m = DURATION.matcher(line);
            if (!m.find()) continue;

            int hours = Integer.parseInt(m.group(1));
            String text = line.replace(m.group(0), "").trim();

            if (inAssessment && currentAssessment != null) {
                List<String> found = findInstructors(text);
                if (found.isEmpty() && pendingInstructor != null) {
                    found = List.of(pendingInstructor);
                }
                currentAssessment.getLecturers().addAll(found);
                currentAssessment.setDurationHours(hours);
                pendingInstructor = null;
                continue;
            }

            Lesson lesson = new Lesson();
            lesson.setOrderNumber(order++);
            lesson.setDurationHours(hours);
            lesson.setType(selfStudy ? LessonType.SELF_STUDY : LessonType.LECTURE);

            List<String> instructors = findInstructors(text);

            if (!instructors.isEmpty()) {
                lesson.setLecturer(instructors.get(0));
                lesson.setTitle(removeInstructor(text, instructors.get(0)));
            } else if (pendingInstructor != null) {
                lesson.setLecturer(pendingInstructor);
                lesson.setTitle(text);
            } else {
                lesson.setTitle(text);
            }

            day.getLessons().add(lesson);
            pendingInstructor = null;
            inAssessment = false;
        }
    }

    private static boolean isPureInstructor(String line) {
        return INSTRUCTORS.contains(line);
    }

    private static List<String> findInstructors(String text) {
        List<String> result = new ArrayList<>();
        for (String instructor : INSTRUCTORS) {
            if (text.contains(instructor)) {
                result.add(instructor);
            }
        }
        return result;
    }

    private static String removeInstructor(String text, String instructor) {
        return text.replace(instructor, "").trim();
    }
}
