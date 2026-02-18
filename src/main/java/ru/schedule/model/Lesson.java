package ru.schedule.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "lessons")
public class Lesson extends BaseEntity {

    private int orderNumber;
    private String title;
    private String lecturer;
    private int durationHours;
    private String note;

    @Enumerated(EnumType.STRING)
    private LessonType type;

    @ElementCollection
    @CollectionTable(name = "lesson_lecturers", joinColumns = @JoinColumn(name = "lesson_id"))
    @Column(name = "lecturer")
    private List<String> lecturers = new ArrayList<>();

    public int getOrderNumber() { return orderNumber; }
    public void setOrderNumber(int orderNumber) { this.orderNumber = orderNumber; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getLecturer() { return lecturer; }
    public void setLecturer(String lecturer) { this.lecturer = lecturer; }

    public List<String> getLecturers() { return lecturers; }
    public void setLecturers(List<String> lecturers) { this.lecturers = lecturers; }

    public int getDurationHours() { return durationHours; }
    public void setDurationHours(int durationHours) { this.durationHours = durationHours; }

    public LessonType getType() { return type; }
    public void setType(LessonType type) { this.type = type; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Lesson lesson)) return false;
        return Objects.equals(getId(), lesson.getId());
    }

    @Override
    public int hashCode() { return Objects.hash(getId()); }
}
