package ru.schedule.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import ru.schedule.model.BaseEntity;
import ru.schedule.model.Day;
import ru.schedule.model.User;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "groups")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Group extends BaseEntity {

    @Column(nullable = false)
    private String code;

    private String location;

    private Integer course;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Day> days = new ArrayList<>();

    @ManyToMany(mappedBy = "groups")
    private List<User> users = new ArrayList<>();

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public Integer getCourse() { return course; }
    public void setCourse(Integer course) { this.course = course; }

    public List<Day> getDays() { return days; }
    public void setDays(List<Day> days) { this.days = days; }

    public List<User> getUsers() { return users; }
    public void setUsers(List<User> users) { this.users = users; }
}
