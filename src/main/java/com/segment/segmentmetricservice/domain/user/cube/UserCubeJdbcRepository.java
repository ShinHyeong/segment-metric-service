package com.segment.segmentmetricservice.domain.user.cube;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Repository
@RequiredArgsConstructor
public class UserCubeJdbcRepository{
    private final JdbcTemplate userJdbcTemplate;
    private final TransactionTemplate userTransactionTemplate;

    /**
     * metric테이블에 INSERT할 때, 유저(마케팅 팀)이 추세조회 API 호출할 수 있음 -> 로딩/에러 발생
     * 임시 테이블에 데이터를 먼저 채우고 원본 테이블과 이름 교체하는 식으로 INSERT
     */
    public void replaceCubeData(Map<UserCubeKey, Long> cube, int batchSize){
        try {
            userJdbcTemplate.execute("DROP TABLE IF EXISTS account_cube_new");
            userJdbcTemplate.execute("CREATE TABLE account_cube_new LIKE account_cube");

            //[프록시를 거치지 않은 메소드 -> AOP 적용 누락됨] 문제 방지
            userTransactionTemplate.executeWithoutResult(status -> {
                saveAllToStagingTable(cube, batchSize);
            });

            userJdbcTemplate.execute("DROP TABLE IF EXISTS account_cube_old");
            userJdbcTemplate.execute("RENAME TABLE account_cube TO account_cube_old, account_cube_new TO account_cube");
            userJdbcTemplate.execute("DROP TABLE account_cube_old");

        } catch (Exception e) {
            log.error("큐브 데이터 교체 중 예외 발생, 임시 테이블을 정리합니다.", e);
            userJdbcTemplate.execute("DROP TABLE IF EXISTS account_cube_new");
            throw e;
        }
    }


    private void saveAllToStagingTable(Map<UserCubeKey, Long> cube, int batchSize){
        String sql = "INSERT INTO account_cube_new (location, gender, age_start_val, age_end_val, order_count_start_val, order_count_end_val, user_count) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        List<Map.Entry<UserCubeKey,Long>> entries = new ArrayList<>(cube.entrySet());

        userJdbcTemplate.batchUpdate(sql, entries, batchSize,
                (PreparedStatement ps, Map.Entry<UserCubeKey, Long> entry) -> {
                    UserCubeKey key = entry.getKey();
                    ps.setString(1, key.location());
                    ps.setString(2, key.gender());
                    ps.setInt(3, key.ageStartVal());
                    ps.setInt(4, key.ageEndVal());
                    ps.setInt(5, key.orderCountStartVal());
                    ps.setInt(6, key.orderCountEndVal());

                    Long userCount = entry.getValue();
                    ps.setLong(7, userCount);
                }
        );
    }
}