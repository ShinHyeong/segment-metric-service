package com.segment.segmentmetricservice.domain.metric;

import com.segment.segmentmetricservice.domain.segment.Segment;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class SegmentDailyMetricJdbcRepository {
    private final JdbcTemplate segmentJdbcTemplate;

    public void bulkUpsert(List<SegmentDailyMetric> metrics, int batchSize) {
        String sql = """
            INSERT INTO segment_daily_metric (segment_id, metric_date, user_count, status)
            VALUES (?, ?, ?, 'COUNTED')
            ON DUPLICATE KEY UPDATE
                user_count = VALUES(user_count),
                status = 'COUNTED'
            """;

        segmentJdbcTemplate.batchUpdate(sql, metrics, batchSize,
                (PreparedStatement ps, SegmentDailyMetric metric) -> {
                    ps.setLong(1, metric.getSegmentId());
                    ps.setObject(2, metric.getMetricDate());
                    ps.setLong(3, metric.getUserCount());
                }
            );
    }
}
