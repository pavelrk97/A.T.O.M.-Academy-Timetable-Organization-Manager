package ru.schedule.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "groups")
public class Group extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    private String location;
    private Integer course;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "group_id")
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Group group)) return false;
        return Objects.equals(getId(), group.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }
}
