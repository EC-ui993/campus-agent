package com.campus.agent.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("reports")
public class Report {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String reportDate;
    private String content;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getReportDate() { return reportDate; }
    public void setReportDate(String reportDate) { this.reportDate = reportDate; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
