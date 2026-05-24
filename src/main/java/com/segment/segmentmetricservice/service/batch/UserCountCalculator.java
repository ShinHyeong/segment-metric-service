package com.segment.segmentmetricservice.service.batch;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.core.types.dsl.StringPath;
import com.segment.segmentmetricservice.domain.segment.Operator;
import com.segment.segmentmetricservice.domain.segment.Segment;
import com.segment.segmentmetricservice.domain.segment.SegmentCondition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import com.querydsl.sql.mysql.MySQLQueryFactory;
import com.segment.segmentmetricservice.domain.user.sql.SAccount;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = "userTransactionManager") // Slave DB 조회 (읽기 전용 트랜잭션)
public class UserCountCalculator {

    private final MySQLQueryFactory mySQLQueryFactory;

    // QueryDSL-SQL용 생성 객체
    private static final SAccount user= new SAccount("account");

    /**
     * 특정 세그먼트의 조건에 맞는 유저 수를 계산함
     * @param segment 세그먼트 메타데이터 (조건 포함)
     * @return 조건에 부합하는 유저 수 (Long)
     */
    public Long countUsersBySegment(Segment segment) {
        // 세그먼트 조건으로 동적 WHERE 절 생성
        BooleanBuilder predicate = createPredicate(segment);
        // 힌트를 강제할 인덱스명
        String idxHintName = matchIndexHintName(segment).orElse(null);

        var query = mySQLQueryFactory
                .select(user.count())
                .from(user);

        //힌트를 강제해야할 때만 forceIndex를 쓴다
        if (idxHintName != null) {
            query.forceIndex(idxHintName);
        }

        Long count = query.where(predicate)
                .fetchOne();

        // 결과 반환 (null일 경우 0 반환)
        return count != null ? count : 0L;
    }

    /**
     * 힌트를 강제할 인덱스 이름 반환하는 함수
     **/
    private Optional<String> matchIndexHintName(Segment segment){

        //세그먼트 엔티티가져와서 condition 조건 가져옴
        List<SegmentCondition> conditions = segment.getConditions();

        //예외 처리 : condition 조건이 없는 경우
        if (conditions == null || conditions.isEmpty()) {
            log.warn("Segment ID {} has no conditions.", segment.getId());
            return Optional.empty();
        }

        //conditions에서 칼럼 정보 뽑아서 해당 칼럼 조합 set 만들기
        Set<String> categories = conditions.stream()
                .filter(condition -> StringUtils.hasText(condition.getValue()))
                .map(SegmentCondition::getCategory)
                .collect(Collectors.toUnmodifiableSet());

        //해당 칼럼 조합이 힌트를 강제할 케이스인지 확인 - 맞다면 힌트 강제할 인덱스 이름 반환
        return Optional.ofNullable(INDEX_HINT_MAP.get(categories));
    }

    /* 힌트를 강제할 케이스와 인덱스 */
    private static final Map<Set<String>,String> INDEX_HINT_MAP = Map.of(
            Set.of("location", "order_count"), "idx_location_order_count",
            Set.of("location", "age"), "idx_location_gender_age"
    );


    /**
     * Segment 내부의 List<SegmentCondition>을 순회하며
     * 모든 조건을 AND로 결합한 동적 WHERE 절 생성합니다.
     */
    private BooleanBuilder createPredicate(Segment segment) {
        BooleanBuilder builder = new BooleanBuilder();

        List<SegmentCondition> conditions = segment.getConditions();

        if (conditions == null || conditions.isEmpty()) {
            log.warn("Segment ID {} has no conditions.", segment.getId());
            return builder;
        }

        for (SegmentCondition condition : conditions) {
            String category = condition.getCategory();
            Operator operator = condition.getOperator();
            String value = condition.getValue();

            if (!StringUtils.hasText(value)) continue;

            // 기존의 switch-case 로직을 각 condition에 대해 실행
            switch (category) {
                case "location":
                    builder.and(buildStringPredicate(user.location, operator, value));
                    break;
                case "gender":
                    builder.and(buildStringPredicate(user.gender, operator, value));
                    break;
                case "age":
                    builder.and(buildNumberPredicate(user.age, operator, value));
                    break;
                case "order_count":
                    builder.and(buildNumberPredicate(user.orderCount, operator, value));
                    break;
                default:
                    log.warn("Unknown category: {}", category);
            }
        }

        return builder;
    }

    /**
     * 문자열 타입 컬럼(StringPath)에 대한 동적 조건 생성
     * 지원 연산자: EQUALS, NOT_EQUALS, CONTAINS, STARTS_WITH
     */
    private BooleanExpression buildStringPredicate(StringPath path, Operator operator, String value) {
        switch (operator) {
            case EQUALS:
                return path.eq(value);
            case NOT_EQUALS:
                return path.ne(value);
            case CONTAINS: // LIKE '%value%'
                return path.contains(value);
            case STARTS_WITH: // LIKE 'value%'
                return path.startsWith(value);
            default:
                throw new IllegalArgumentException("Unsupported operator for String type: " + operator);
        }
    }

    /**
     * 숫자 타입 컬럼(NumberPath)에 대한 동적 조건 생성
     * 지원 연산자: EQUALS, NOT_EQUALS, GT, GTE, LT, LTE
     */
    private BooleanExpression buildNumberPredicate(NumberPath<Integer> path, Operator operator, String value) {
        int intValue;
        try {
            intValue = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number format for value: " + value);
        }

        switch (operator) {
            case EQUALS:
                return path.eq(intValue);
            case NOT_EQUALS:
                return path.ne(intValue);
            case GT:  // >
                return path.gt(intValue);
            case GTE: // >=
                return path.goe(intValue);
            case LT:  // <
                return path.lt(intValue);
            case LTE: // <=
                return path.loe(intValue);
            default:
                throw new IllegalArgumentException("Unsupported operator for Number type: " + operator);
        }
    }
}