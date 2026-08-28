package com.campus.agent.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("semester")
public class Semester {
    @TableId(type = IdType.INPUT)
    private Long id;
    private String startDate;
    private Integer totalWeeks;
    private String note;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public Integer getTotalWeeks() { return totalWeeks; }
    public void setTotalWeeks(Integer totalWeeks) { this.totalWeeks = totalWeeks; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
