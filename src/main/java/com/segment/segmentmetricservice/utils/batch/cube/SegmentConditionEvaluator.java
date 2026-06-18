package com.segment.segmentmetricservice.utils.batch.cube;

import com.segment.segmentmetricservice.domain.segment.Operator;
import com.segment.segmentmetricservice.domain.segment.Segment;
import com.segment.segmentmetricservice.domain.user.cube.UserCubeKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.segment.segmentmetricservice.domain.segment.SegmentCondition;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

@Slf4j
@Component
public class SegmentConditionEvaluator {

    /**
     * UserCubeKey가 세그먼트 조건에 해당하는지 검사식을 만들어줌
     */
    public Predicate<UserCubeKey> getCondition(Segment segment) {
        List<SegmentCondition> conditions = segment.getConditions();

        if (conditions == null || conditions.isEmpty()) return key -> true;

        Predicate<UserCubeKey> combinePredicate = key -> true;

        for (SegmentCondition condition : conditions) {
            String category = condition.getCategory();
            Operator operator = condition.getOperator();
            String value = condition.getValue();

            if (!StringUtils.hasText(value)) continue;

            Predicate<UserCubeKey> cubeKeyPredicate = switch(category) {
                case "location"-> buildStringPredicate(UserCubeKey::location, operator, value);
                case "gender" -> buildStringPredicate(UserCubeKey::gender, operator, value);
                case "age" -> buildNumberBucketPredicate(UserCubeKey::ageStartVal, UserCubeKey::ageEndVal, operator, value);
                case "order_count" -> buildNumberBucketPredicate(UserCubeKey::orderCountStartVal, UserCubeKey::orderCountEndVal, operator, value);
                default -> {
                    log.warn("등록 되지않은 segment 조건 : {}", category);
                    yield key -> true;
                }
            };

            combinePredicate = combinePredicate.and(cubeKeyPredicate);
        }

        return combinePredicate;
    }

    private Predicate<UserCubeKey> buildStringPredicate(Function<UserCubeKey, String> extractor,
                                                        Operator operator, String value) {
        return key -> {
            String targetVal = extractor.apply(key);

            return switch (operator) {
                case EQUALS ->  targetVal.equals(value);
                case NOT_EQUALS ->  !(targetVal.equals(value));
                case CONTAINS ->  targetVal.contains(value);
                case STARTS_WITH ->  targetVal.startsWith(value);
                default -> throw new IllegalArgumentException("지원하지 않는 연산자입니다: "+operator);
            };
        };
    }

    private Predicate<UserCubeKey> buildNumberBucketPredicate(Function<UserCubeKey, Integer> startValExtractor,
                                                              Function<UserCubeKey, Integer> EndValExtractor,
                                                              Operator operator, String value) {
        int v = Integer.parseInt(value);

        return key -> {
            Integer startVal = startValExtractor.apply(key);
            Integer endVal = EndValExtractor.apply(key);
            if  (startVal == null || endVal == null) return false;

            return switch (operator){
                case EQUALS -> (startVal >= v) && (endVal <= (v+1));
                case NOT_EQUALS -> !((startVal >= v) && (endVal <= (v+1)));
                case GT -> startVal >= v+1;
                case GTE -> startVal >= v;
                case LT -> endVal <= v;
                case LTE -> endVal  <= v+1;
                default -> throw new IllegalArgumentException("지원하지 않는 연산자: "+operator);
            };
        };
    }
}
