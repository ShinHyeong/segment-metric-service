package com.segment.segmentmetricservice.dto.analysis;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TrendResponseDto {

    /**
     * 지표 측정 날짜
     * JSON 응답 예시: "2026-01-01"
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "Asia/Seoul")
    private LocalDate date;

    /**
     * 해당 날짜의 세그먼트 유저 수
     * JSON 응답 예시: 500
     */
    private Long count;

    // 필요하다면 정적 팩토리 메서드 추가 가능
    public static TrendResponseDto of(LocalDate date, Long count) {
        return new TrendResponseDto(date, count);
    }
}