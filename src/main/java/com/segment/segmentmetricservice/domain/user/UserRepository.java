package com.segment.segmentmetricservice.domain.user;

import com.segment.segmentmetricservice.domain.user.cube.UserCubeProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    /**
     * 최하위 차원 조합의 order_count 집계할 때
     * OOM 및 DB I/O부하를 줄이기 위해 필요한 필드만 가져옴
     * @param lastId 마지막으로 조회된 유저의 ID (처음 조회 시 0)
     * @param pageSize 한 번에 가져올 데이터 개수
     */
    @Transactional(readOnly = true, transactionManager = "userTransactionManager")
    @Query("""
    SELECT u.id AS id, u.location AS location, u.gender AS gender, u.age AS age, u.orderCount AS orderCount
    FROM User u
    WHERE u.id > :lastId
    ORDER BY u.id ASC
    LIMIT :pageSize
    """)
    List<UserCubeProjection> findProjectionsAfterId(@Param("lastId") Long lastId, @Param("pageSize") int pageSize);
}