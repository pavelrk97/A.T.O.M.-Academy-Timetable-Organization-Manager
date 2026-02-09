package ru.schedule.parser;

import com.opencsv.CSVReader;
import ru.schedule.model.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

public class ScheduleCsvParser {

    // (4ч)
    private static final Pattern DURATION = Pattern.compile("\\((\\d+)\\s*ч\\)");

    // CS01, NS01, IC01, I&C01
    private static final Pattern COURSE_CODE = Pattern.compile("^[A-Z]{2,}\\d*$");

    // инструкторы
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
            "Examination"
    );

    public static List<Group> parse(InputStream is) throws Exception {
        CSVReader reader = new CSVReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        List<String[]> rows = reader.readAll();

        // 0 строка — дни недели (игнор)
        String[] datesRow = rows.get(1); // даты

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
                day.date = datesRow[c];

                if (activeCourseCode != null) {
                    day.meta.put("courseCode", activeCourseCode);
                }

                parseCell(row[c], day);

                // если в дне встретился новый курс — он активен дальше
                if (day.meta.containsKey("courseCode")) {
                    activeCourseCode = day.meta.get("courseCode");
                }

                group.days.add(day);
            }
        }

        return result;
    }

    private static Group parseGroupHeader(String cell) {
        Group g = new Group();
        String[] lines = cell.split("\\n");
        g.code = lines[0].trim();
        g.location = lines.length > 1 ? lines[1].trim() : null;
        return g;
    }

    private static void parseCell(String cell, Day day) {
        String[] lines = cell.split("\\n");

        int order = 1;
        boolean selfStudy = false;
        boolean inAssessment = false;
        Lesson currentAssessment = null;

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            // СП → дальше все занятия SELF_STUDY
            if (line.equals("СП")) {
                selfStudy = true;
                continue;
            }

            // код курса
            if (COURSE_CODE.matcher(line).find()) {
                day.meta.put("courseCode", line);
                continue;
            }

            // промежуточный контроль
            if (ASSESSMENT_TITLES.contains(line)) {
                Lesson l = new Lesson();
                l.order = order++;
                l.title = line;
                l.type = LessonType.ASSESSMENT;
                l.durationHours = 0;
                day.lessons.add(l);
                currentAssessment = l;
                inAssessment = true;
                continue;
            }

            Matcher m = DURATION.matcher(line);
            if (!m.find()) continue;

            int hours = Integer.parseInt(m.group(1));
            String text = line.replace(m.group(0), "").trim();

            Lesson lesson = new Lesson();
            lesson.order = order++;
            lesson.durationHours = hours;
            lesson.type = selfStudy ? LessonType.SELF_STUDY : LessonType.LECTURE;

            // лектор + название
            String lecturer = extractInstructor(text);
            if (lecturer != null) {
                lesson.lecturer = lecturer;
                lesson.title = text.replaceFirst(Pattern.quote(lecturer), "").trim();
            } else {
                lesson.title = text;
            }

            day.lessons.add(lesson);
            inAssessment = false;
        }
    }

    private static String extractInstructor(String text) {
        for (String i : INSTRUCTORS) {
            if (text.startsWith(i)) {
                return i;
            }
        }
        return null;
    }
}
