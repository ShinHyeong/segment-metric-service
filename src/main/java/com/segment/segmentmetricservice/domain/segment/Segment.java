package com.segment.segmentmetricservice.domain.segment;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import org.springframework.data.annotation.Id;

@Entity
@Getter
public class Segment {
    @Id
    private Long id; // segment_id
    private String category; // 필터 컬럼 (ex: location, order_count)

    @Enumerated(EnumType.STRING)
    private Operator operator;
    private String value; // 비교 값
}