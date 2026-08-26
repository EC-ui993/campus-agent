package com.campus.agent.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("course_overrides")
public class CourseOverride {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long courseId;
    private String courseTitle;
    private String overrideDate;
    private String kind;
    private String newStartTime;
    private String newEndTime;
    private String newLocation;
    private String note;
    private Long sourceMessageId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getCourseTitle() { return courseTitle; }
    public void setCourseTitle(String courseTitle) { this.courseTitle = courseTitle; }
    public String getOverrideDate() { return overrideDate; }
    public void setOverrideDate(String overrideDate) { this.overrideDate = overrideDate; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getNewStartTime() { return newStartTime; }
    public void setNewStartTime(String newStartTime) { this.newStartTime = newStartTime; }
    public String getNewEndTime() { return newEndTime; }
    public void setNewEndTime(String newEndTime) { this.newEndTime = newEndTime; }
    public String getNewLocation() { return newLocation; }
    public void setNewLocation(String newLocation) { this.newLocation = newLocation; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Long getSourceMessageId() { return sourceMessageId; }
    public void setSourceMessageId(Long sourceMessageId) { this.sourceMessageId = sourceMessageId; }
}
