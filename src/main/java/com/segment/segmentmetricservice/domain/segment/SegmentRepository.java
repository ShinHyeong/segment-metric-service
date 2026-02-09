package com.segment.segmentmetricservice.domain.segment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SegmentRepository extends JpaRepository<Segment, Long> {
    // 기본 findAll() 메서드를 사용하여 모든 세그먼트를 조회합니다.
    // 필요시 페이징 처리를 위해 Page<Segment> findAll(Pageable pageable)을 사용할 수 있습니다.
}