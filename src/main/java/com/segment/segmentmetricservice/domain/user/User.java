package com.segment.segmentmetricservice.domain.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "account",
        indexes = {
                // 배치 성능 최적화를 위한 커버링 인덱스 (카디널리티가 높은 순서 고려)
                // 실제 인덱스 순서는 데이터 분포에 따라 조정 필요 (예: location -> gender -> age)
                @Index(name = "idx_user_covering", columnList = "location, gender, age, order_count")
        }
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 10)
    private String gender;

    @Column(nullable = false)
    private Integer age;

    @Column(nullable = false, length = 100)
    private String location; // "서울", "부산" ...

    @Column(name = "order_count", nullable = false)
    private Integer orderCount; // 누적 주문 수

    // 테스트 및 데이터 생성을 위한 생성자
    public User(String name, String gender, Integer age, String location, Integer orderCount) {
        this.name = name;
        this.gender = gender;
        this.age = age;
        this.location = location;
        this.orderCount = orderCount;
    }
}