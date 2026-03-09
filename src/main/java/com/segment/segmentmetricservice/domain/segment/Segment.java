package com.segment.segmentmetricservice.domain.segment;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Segment {
    @Id
    @Column(name = "segment_id") // 이제 ID가 곧 세그먼트 번호가 됩니다.
    private Long id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<SegmentCondition> conditions = new ArrayList<>();

    private LocalDate createdAt;
}