package com.campus.agent.web;

import com.campus.agent.service.DailyReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ReportController {

    private final DailyReportService reportService;

    public ReportController(DailyReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/report/today")
    public Map<String, String> today() {
        return Map.of("date", LocalDate.now().toString(), "content", reportService.todayReport());
    }
}
