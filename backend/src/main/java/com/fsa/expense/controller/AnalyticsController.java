package com.fsa.expense.controller;

import com.fsa.expense.dto.AnalyticsSummaryDto;
import com.fsa.expense.dto.ErrorLogDto;
import com.fsa.expense.service.AnalyticsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@Slf4j
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    public ResponseEntity<AnalyticsSummaryDto> getSummary() {
        return ResponseEntity.ok(analyticsService.getDashboardSummary());
    }

    @GetMapping("/error-logs")
    public ResponseEntity<List<ErrorLogDto>> getErrorLogs() {
        return ResponseEntity.ok(analyticsService.getAllErrorLogs());
    }
}
