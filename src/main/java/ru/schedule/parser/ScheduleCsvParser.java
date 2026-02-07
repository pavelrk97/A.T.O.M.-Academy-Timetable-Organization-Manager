package ru.schedule.parser;

import com.opencsv.CSVReader;
import ru.schedule.model.*;

import java.io.*;
import java.util.*;
import java.util.regex.*;

public class ScheduleCsvParser {

    private static final Pattern DURATION = Pattern.compile("\\((\\d+)ч\\)");
    private static final Pattern COURSE_CODE = Pattern.compile("^[A-Z]{2,}\\d*$");

    public static List<Group> parse(InputStream is) throws Exception {
        CSVReader reader = new CSVReader(new InputStreamReader(is));
        List<String[]> rows = reader.readAll();

        // строка 0 — дни недели (игнор)
        String[] datesRow = rows.get(1); // строка с датами

        List<Group> result = new ArrayList<>();

        for (int r = 2; r < rows.size(); r++) {
            String[] row = rows.get(r);
            if (row.length == 0 || row[0].isBlank()) continue;

            Group group = parseGroupHeader(row[0]);
            result.add(group);

            for (int c = 1; c < row.length; c++) {
                if (row[c] == null || row[c].isBlank()) continue;

                Day day = new Day();
                day.date = datesRow[c];

                parseCell(row[c], day);
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

        boolean selfStudyMode = false;
        int order = 1;

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            // СП
            if (line.equals("СП")) {
                selfStudyMode = true;
                continue;
            }

            // Код курса
            if (COURSE_CODE.matcher(line).matches()) {
                day.meta.put("courseCode", line);
                continue;
            }

            Matcher m = DURATION.matcher(line);
            if (!m.find()) continue;

            Lesson lesson = new Lesson();
            lesson.order = order++;
            lesson.durationHours = Integer.parseInt(m.group(1));
            lesson.type = selfStudyMode ? LessonType.SELF_STUDY : LessonType.LECTURE;

            String withoutDuration = line.replace(m.group(0), "").trim();

            // Лектор может отсутствовать
            if (withoutDuration.contains(" ")) {
                String[] parts = withoutDuration.split(" ", 2);
                lesson.lecturer = parts[0];
                lesson.title = parts[1];
            } else {
                lesson.title = withoutDuration;
            }

            day.lessons.add(lesson);
        }
    }
}
