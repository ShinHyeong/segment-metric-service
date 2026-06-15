package com.segment.segmentmetricservice.aspect;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class MetricTrackingAspect {

    private final MeterRegistry meterRegistry;
    private final ExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(annotation)")
    public Object trackTime(ProceedingJoinPoint joinPoint, TrackExecutionTime annotation) throws Throwable {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            return joinPoint.proceed();
        }finally{
            String metricName = annotation.metricName();
            Timer.Builder timerBuilder = Timer.builder(metricName);

            String[] tags = annotation.tags();
            if (isValidTags(tags)) {
                applySpelTags(timerBuilder, joinPoint, tags);
            }

            sample.stop(timerBuilder.register(meterRegistry));
            log.debug("Metric {} has been tracked", metricName);
        }
    }

    private boolean isValidTags(String[] tags) {
        return tags != null && tags.length > 0 && tags.length % 2 == 0;
    }

    /**
     * SpEL 태그 파싱 및 Timer.Builder에 추가
     * #chunkSize, #round 등으로 사용 가능
     */
    private void applySpelTags(Timer.Builder timerBuilder, ProceedingJoinPoint joinPoint, String[] tags){
        EvaluationContext context = createEvaluationContext(joinPoint);

        for (int i = 0; i < tags.length; i += 2) {
            String tagKey = tags[i];
            String tagExpression = tags[i + 1];

            try {
                String tagValue = parser.parseExpression(tagExpression).getValue(context, String.class);
                if (tagValue != null) timerBuilder.tag(tagKey, tagValue);
            } catch (Exception e) {
                log.warn("SpEL 표현식 파싱 실패 tag={}", tagKey, e);
            }
        }
    }

    /**
     * SpEL EvaluationContext 생성 및 메서드 파라미터 바인딩
     */
    private EvaluationContext createEvaluationContext(ProceedingJoinPoint joinPoint) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("target", joinPoint.getTarget());

        if (joinPoint.getSignature() instanceof MethodSignature signature) {
            String[] parameterNames = signature.getParameterNames();
            Object[] args = joinPoint.getArgs();

            if (parameterNames != null) {
                for (int i = 0; i < parameterNames.length; i++) {
                    context.setVariable(parameterNames[i], args[i]);
                }
            }
        }
        return context;
    }
}
