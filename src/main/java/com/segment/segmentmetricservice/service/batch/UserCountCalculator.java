package com.segment.segmentmetricservice.service.batch;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.segment.segmentmetricservice.domain.segment.Operator;
import com.segment.segmentmetricservice.domain.segment.Segment;
import com.segment.segmentmetricservice.domain.segment.SegmentCondition;
import com.segment.segmentmetricservice.domain.user.QUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = "userTransactionManager") // Slave DB 조회 (읽기 전용 트랜잭션)
public class UserCountCalculator {

    private final JPAQueryFactory queryFactory;

    // QUser 정적 인스턴스 (QueryDSL 생성 객체)
    private static final QUser user = QUser.user;

    /**
     * 특정 세그먼트의 조건에 맞는 유저 수를 계산합니다.
     *
     * @param segment 세그먼트 메타데이터 (조건 포함)
     * @return 조건에 부합하는 유저 수 (Long)
     */
    public Long countUsersBySegment(Segment segment) {
        // 1. 세그먼트 조건으로 동적 WHERE 절 생성
        BooleanBuilder predicate = createPredicate(segment);

        // 2. QueryDSL 카운트 쿼리 실행
        Long count = queryFactory
                .select(user.count()) // SELECT COUNT(*)
                .from(user)
                .where(predicate)     // WHERE condition...
                .fetchOne();

        // 3. 결과 반환 (null일 경우 0 반환)
        return count != null ? count : 0L;
    }

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