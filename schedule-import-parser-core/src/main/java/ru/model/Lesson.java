package ru.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

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

    @Column(nullable = false)
    private int durationHours;

    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LessonType type;

    @Column(name = "business_trip", nullable = false)
    private boolean businessTrip;

    @ElementCollection
    @CollectionTable(name = "lesson_lecturers", joinColumns = @JoinColumn(name = "lesson_id"))
    @Column(name = "lecturer_name")
    @Fetch(FetchMode.SUBSELECT)
    @BatchSize(size = 128)
    private List<String> lecturers = new ArrayList<>();

    @ManyToMany
    @JoinTable(
            name = "lesson_instructors",
            joinColumns = @JoinColumn(name = "lesson_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @Fetch(FetchMode.SUBSELECT)
    @BatchSize(size = 128)
    private List<User> assignedInstructors = new ArrayList<>();

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

    public boolean isBusinessTrip() { return businessTrip; }
    public void setBusinessTrip(boolean businessTrip) { this.businessTrip = businessTrip; }

    public List<String> getLecturers() { return lecturers; }
    public void setLecturers(List<String> lecturers) { this.lecturers = lecturers; }

    public List<User> getAssignedInstructors() { return assignedInstructors; }
    public void setAssignedInstructors(List<User> assignedInstructors) { this.assignedInstructors = assignedInstructors; }

    public Day getDay() { return day; }
    public void setDay(Day day) { this.day = day; }
}
