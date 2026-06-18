package com.segment.segmentmetricservice.domain.user.cube;

/**
 * 최하위 차원 조합의 user_count 집계를 위한 키
 * @param location 지역
 * @param gender 성별
 * @param ageStartVal 나이 버킷 GTE Value
 * @param ageEndVal 나이 버킷 LT Value
 * @param orderCountStartVal 주문수 버킷 GTE Value
 * @param orderCountEndVal 주문수 버킷 LT Value
 */
public record UserCubeKey(
        String location,
        String gender,
        int ageStartVal,
        int ageEndVal,
        int orderCountStartVal,
        int orderCountEndVal
){}