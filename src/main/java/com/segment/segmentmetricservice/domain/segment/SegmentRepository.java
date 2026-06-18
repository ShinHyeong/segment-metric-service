package com.segment.segmentmetricservice.domain.segment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Repository
public interface SegmentRepository extends JpaRepository<Segment, Long> {

    @Transactional(readOnly = true, transactionManager = "segmentTransactionManager")
    @Query("""
    SELECT s
    FROM Segment s
    WHERE s.id > :lastId
    ORDER BY s.id ASC
    LIMIT :pageSize
    """)
    List<Segment> findSegmentsAfterId(@Param("lastId") Long lastId, @Param("pageSize") int pageSize);

    /**
     * 컷포인트 추출 : 특정 카테고리 조건(JSON)을 파싱해서 모든 구간의 절단점을 콤바로 구분된 문자열(csv)로 반환
     * [배타적 구간을 위한 cut-point 생성 규칙]
     *  - Value 그대로 절단점이 되는 Op. : GTE, LT, EQUALS, NOT_EQUALS
     *  - Value+1 해야 절단점이 되는 Op : GT, LTE, EQUALS, NOT_EQUALS
      */
    @Transactional(readOnly = true, transactionManager = "segmentTransactionManager")
    @Query(value = """
        WITH ExtractedData AS (
          SELECT DISTINCT
            UPPER(JSON_UNQUOTE(JSON_EXTRACT(jt.cond, '$.operator'))) AS operator,

            CAST(JSON_UNQUOTE(JSON_EXTRACT(jt.cond, '$.value')) AS UNSIGNED) AS val1

          FROM segment s,
          JSON_TABLE(s.conditions, '$[*]' COLUMNS (cond JSON PATH '$')) jt
          WHERE JSON_UNQUOTE(JSON_EXTRACT(jt.cond, '$.category')) = :category
        ),
        RawCutPoints AS (
          SELECT val1 AS cut_point
          FROM ExtractedData
          WHERE operator IN ('GTE', 'LT', 'EQUALS', 'NOT_EQUALS')

          UNION

          SELECT val1 + 1 AS cut_point
          FROM ExtractedData
          WHERE operator IN ('LTE', 'GT', 'EQUALS', 'NOT_EQUALS')

          UNION

          SELECT 0 AS cut_point
        )
        SELECT GROUP_CONCAT(cut_point ORDER BY cut_point ASC SEPARATOR ',')
        FROM RawCutPoints
        """, nativeQuery = true)
    String findCutPointsByCategory(@Param("category") String category);

    default int[] getAgeCutPoints(){ return parseCutPoints(findCutPointsByCategory("age")); };
    default int[] getOrderCountCutPoints(){ return parseCutPoints(findCutPointsByCategory("order_count")); };

    /**
     * @param csv 콤마로 구분된 숫자 문자열 (ex: "0,14,16,18")
     * @return 파싱된 정수(int) 배열. 데이터가 없을 경우 기본값 [0] 반환
     */
    private int[] parseCutPoints(String csv){
        if (csv==null || csv.isBlank()) { return new int[]{0}; }
        return Arrays.stream(csv.split(","))
                .mapToInt(Integer::parseInt)
                .toArray();
    }
}