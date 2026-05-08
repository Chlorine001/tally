package tup.tally.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tup.tally.dto.DailyTrendData;
import tup.tally.dto.MonthlyTrendData;
import tup.tally.dto.TagStat;
import tup.tally.service.StatsService;

import java.util.List;

/**
 * @author chlorine
 * @version 1.0
 * @Date 2026-05-07
 * @Description
 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {
    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/monthly")
    public ResponseEntity<MonthlyTrendData> getMonthlyTrend(
            @RequestParam int year) {
        MonthlyTrendData data = statsService.getMonthlyTrend(year);
        return ResponseEntity.ok(data);
    }

    @GetMapping("/daily")
    public ResponseEntity<DailyTrendData> getDailyTrend(
            @RequestParam int year,
            @RequestParam int month) {
        DailyTrendData data = statsService.getDailyTrend(year, month);
        return ResponseEntity.ok(data);
    }


    @GetMapping("/tags")
    public ResponseEntity<List<TagStat>> getTags(
            @RequestParam int year,
            @RequestParam int month) {
        List<TagStat> tags = statsService.getTagsMonthlyTrend(year, month);
        return ResponseEntity.ok(tags);
    }
}