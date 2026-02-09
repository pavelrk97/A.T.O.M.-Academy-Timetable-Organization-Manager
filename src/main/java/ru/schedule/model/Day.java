package ru.schedule.model;

import java.util.*;

public class Day {

    public String date;

    /**
     * meta:
     * - courseCode (CS01, NS01, I&C02.01.01)
     * - любые расширения в будущем
     */
    public Map<String, String> meta = new HashMap<>();

    public List<Lesson> lessons = new ArrayList<>();
}
