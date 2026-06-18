package com.segment.segmentmetricservice.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TrackExecutionTime {

    String metricName(); // 프로메테우스로 전달될 식별자 이름
    String[] tags() default {}; //동적 태그
}
