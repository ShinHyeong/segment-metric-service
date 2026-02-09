package com.segment.segmentmetricservice.domain.metric;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SegmentDailyMetricProgressRepository extends JpaRepository<SegmentDailyMetricProgress, Long> {

    /**
     * 특정 날짜와 상태에 해당하는 작업 목록을 조회합니다.
     * * 사용처 1: 배치 Step 2 시작 시 'PENDING' 상태인 작업 조회 (재시도 포함)
     * 사용처 2: 배치 Step 3 시작 시 'COUNTED' 상태인 작업 조회 (Bulk Insert 대상)
     */
    List<SegmentDailyMetricProgress> findAllByMetricDateAndStatus(LocalDate metricDate, ProcessStatus status);

    /**
     * 해당 날짜에 대한 배치 작업이 이미 초기화되었는지 확인합니다.
     * 중복 초기화 방지용
     */
    boolean existsByMetricDate(LocalDate metricDate);

    /**
     * (선택) 배치가 완전히 끝난 후 해당 날짜의 임시 데이터를 정리할 때 사용
     */
    void deleteByMetricDate(LocalDate metricDate);
}