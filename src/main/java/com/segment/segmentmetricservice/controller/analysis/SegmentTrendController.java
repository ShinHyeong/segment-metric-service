package com.segment.segmentmetricservice.controller.analysis;

import com.segment.segmentmetricservice.dto.analysis.TrendResponseDto;
import com.segment.segmentmetricservice.service.analysis.SegmentTrendService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/segments")
public class SegmentTrendController {

    private final SegmentTrendService trendService;

    @GetMapping("/{segmentId}/trend")
    public ResponseEntity<List<TrendResponseDto>> getSegmentTrend(
            @PathVariable Long segmentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        return ResponseEntity.ok(trendService.getTrend(segmentId, startDate, endDate));
    }
}