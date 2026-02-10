package ru.schedule.model;

import java.util.*;

public class Lesson {

    public int order;

    /** Название темы / предмета */
    public String title;

    /** Основной преподаватель (если один) */
    public String lecturer;

    /** Дополнительные преподаватели (для контроля и кейсов) */
    public List<String> lecturers = new ArrayList<>();

    /** Длительность в часах */
    public int durationHours;

    /** Тип занятия */
    public LessonType type;

    /** Примечание (например: СП, Intermediate Examination) */
    public String note;
}
