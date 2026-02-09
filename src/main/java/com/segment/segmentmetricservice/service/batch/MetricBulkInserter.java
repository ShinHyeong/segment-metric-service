package com.segment.segmentmetricservice.service.batch;

import com.segment.segmentmetricservice.domain.metric.SegmentDailyMetricProgress;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class MetricBulkInserter {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void bulkInsertMetrics(List<SegmentDailyMetricProgress> countedItems) {
        String sql = "INSERT INTO segment_daily_metric (segment_id, metric_date, user_count) VALUES (?, ?, ?)";

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                SegmentDailyMetricProgress item = countedItems.get(i);
                ps.setLong(1, item.getSegmentId());
                ps.setDate(2, java.sql.Date.valueOf(item.getMetricDate()));
                ps.setLong(3, item.getUserCount());
            }

            @Override
            public int getBatchSize() {
                return countedItems.size();
            }
        });
    }
}