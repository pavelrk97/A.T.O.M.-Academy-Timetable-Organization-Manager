package ru.schedule.parser;

import com.opencsv.CSVReader;
import ru.schedule.model.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Month;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

            if (INSTRUCTORS.contains(line)) {
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
                if (found.isEmpty() && pendingInstructor != null) found = List.of(pendingInstructor);
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
}
