package com.segment.segmentmetricservice.domain.user.cube;

public record UserCubeProjection (
        Long id,
        String location,
        String gender,
        Integer age,
        Integer orderCount
){}
