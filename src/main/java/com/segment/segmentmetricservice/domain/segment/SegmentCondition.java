package com.segment.segmentmetricservice.domain.segment;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SegmentCondition {
    private String category;

    private Operator operator;

    private String value;
}