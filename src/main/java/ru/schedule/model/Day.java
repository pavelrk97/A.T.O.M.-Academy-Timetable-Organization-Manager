package ru.schedule.model;

import java.time.LocalDate;
import java.util.*;

public class Day {

    public LocalDate date;

    public Map<String, String> meta = new HashMap<>();

    public List<Lesson> lessons = new ArrayList<>();
}
