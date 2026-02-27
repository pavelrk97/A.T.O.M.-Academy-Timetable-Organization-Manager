package ru.schedule.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "lessons")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Lesson extends BaseEntity {

    private int orderNumber;

    @Column(nullable = false)
    private String title;

    private String lecturer;

    private int durationHours;

    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LessonType type;

    @ElementCollection
    @CollectionTable(name = "lesson_lecturers", joinColumns = @JoinColumn(name = "lesson_id"))
    @Column(name = "lecturer_name")
    private List<String> lecturers = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "day_id", nullable = false)
    private Day day;

    public int getOrderNumber() { return orderNumber; }
    public void setOrderNumber(int orderNumber) { this.orderNumber = orderNumber; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getLecturer() { return lecturer; }
    public void setLecturer(String lecturer) { this.lecturer = lecturer; }

    public int getDurationHours() { return durationHours; }
    public void setDurationHours(int durationHours) { this.durationHours = durationHours; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LessonType getType() { return type; }
    public void setType(LessonType type) { this.type = type; }

    public List<String> getLecturers() { return lecturers; }
    public void setLecturers(List<String> lecturers) { this.lecturers = lecturers; }

    public Day getDay() { return day; }
    public void setDay(Day day) { this.day = day; }
}
