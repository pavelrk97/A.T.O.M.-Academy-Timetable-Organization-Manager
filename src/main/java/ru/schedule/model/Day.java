package ru.schedule.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import ru.schedule.model.BaseEntity;
import ru.schedule.model.Group;

import java.time.LocalDate;
import java.util.*;

@Entity
@Table(name = "days")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Day extends BaseEntity {

    private LocalDate date;

    @ElementCollection
    @CollectionTable(name = "day_meta", joinColumns = @JoinColumn(name = "day_id"))
    @MapKeyColumn(name = "meta_key")
    @Column(name = "meta_value")
    private Map<String, String> meta = new HashMap<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @OneToMany(mappedBy = "day", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Lesson> lessons = new ArrayList<>();

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public Map<String, String> getMeta() { return meta; }
    public void setMeta(Map<String, String> meta) { this.meta = meta; }

    public Group getGroup() { return group; }
    public void setGroup(Group group) { this.group = group; }

    public List<Lesson> getLessons() { return lessons; }
    public void setLessons(List<Lesson> lessons) { this.lessons = lessons; }
}
