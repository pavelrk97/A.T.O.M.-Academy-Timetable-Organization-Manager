package ru.schedule.model;

import java.util.*;

public class Day {
    public String date;
    public Map<String, String> meta = new HashMap<>();
    public List<Lesson> lessons = new ArrayList<>();
}
