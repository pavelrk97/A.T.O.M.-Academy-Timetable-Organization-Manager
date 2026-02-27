package ru.schedule.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import ru.schedule.model.Group;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ImportRequest {

    private int groupCount;
    private List<Group> groups;

    public int getGroupCount() {
        return groupCount;
    }

    public void setGroupCount(int groupCount) {
        this.groupCount = groupCount;
    }

    public List<Group> getGroups() {
        return groups;
    }

    public void setGroups(List<Group> groups) {
        this.groups = groups;
    }
}
