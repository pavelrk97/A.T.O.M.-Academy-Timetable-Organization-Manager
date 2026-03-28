package ru.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "days")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Day extends BaseEntity {

    private LocalDate date;

    @ElementCollection
    @CollectionTable(name = "day_meta", joinColumns = @JoinColumn(name = "day_id"))
    @MapKeyColumn(name = "meta_key")
    @Column(name = "meta_value")
    @Fetch(FetchMode.SUBSELECT)
    @BatchSize(size = 64)
    private Map<String, String> meta = new HashMap<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @OneToMany(mappedBy = "day", cascade = CascadeType.ALL, orphanRemoval = true)
    @Fetch(FetchMode.SUBSELECT)
    @BatchSize(size = 64)
    private List<Lesson> lessons = new ArrayList<>();

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public Map<String, String> getMeta() { return meta; }
    public void setMeta(Map<String, String> meta) { this.meta = meta != null ? meta : new HashMap<>(); }

    public Group getGroup() { return group; }
    public void setGroup(Group group) { this.group = group; }

    public List<Lesson> getLessons() { return lessons; }
    public void setLessons(List<Lesson> lessons) { this.lessons = lessons; }
}
