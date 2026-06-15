package com.segment.segmentmetricservice.service.batch.index;

import com.segment.segmentmetricservice.aspect.TrackExecutionTime;
import com.segment.segmentmetricservice.domain.metric.SegmentDailyMetricRepository;
import com.segment.segmentmetricservice.domain.segment.Segment;
import com.segment.segmentmetricservice.domain.segment.SegmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InitMetricService {

    private final SegmentRepository segmentRepository;
    private final SegmentDailyMetricRepository metricRepository;

    @TrackExecutionTime(metricName = "batch.init.duration",
                        tags = {"chunkSize", "#chunkSize"})
    public void initMetricTable(LocalDate date, int chunkSize) {
        int pageNumber = 0;
        Page<Segment> segmentPage;

        do {
            segmentPage = segmentRepository.findAll(PageRequest.of(pageNumber, chunkSize));

            List<Long> segmentIds = segmentPage.getContent().stream()
                    .map(Segment::getId)
                    .toList();

            if (!segmentIds.isEmpty()) {
                metricRepository.bulkInsertIfNotExists(date, segmentIds);
            }

            pageNumber++;
            log.info("초기화 중... Page: {}", pageNumber);
        } while (segmentPage.hasNext());
    }
}
