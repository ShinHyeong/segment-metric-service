package com.segment.segmentmetricservice.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class DataSourceConfig {

    // 1. Master DataSource (쓰기 전용)
    @Bean(name = "masterDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.master")
    public DataSource masterDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    // 2. Slave DataSource (읽기 전용)
    @Bean(name = "slaveDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.slave")
    public DataSource slaveDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    // 3. Routing DataSource (분기 로직)
    @Bean(name = "routingDataSource")
    public DataSource routingDataSource(
            @Qualifier("masterDataSource") DataSource masterDataSource,
            @Qualifier("slaveDataSource") DataSource slaveDataSource) {

        ReplicationRoutingDataSource routingDataSource = new ReplicationRoutingDataSource();

        Map<Object, Object> dataSourceMap = new HashMap<>();
        dataSourceMap.put("master", masterDataSource);
        dataSourceMap.put("slave", slaveDataSource);

        routingDataSource.setTargetDataSources(dataSourceMap);
        routingDataSource.setDefaultTargetDataSource(masterDataSource); // 기본은 Master

        return routingDataSource;
    }

    // 4. Proxy DataSource (실제 빈으로 등록될 DataSource)
    @Primary // @Autowired 시 이것이 주입됨
    @Bean(name = "dataSource")
    public DataSource dataSource(@Qualifier("routingDataSource") DataSource routingDataSource) {
        // 중요: 트랜잭션 진입 시점이 아닌, 실제 쿼리 실행 시점에 커넥션을 가져오도록 지연시킴
        return new LazyConnectionDataSourceProxy(routingDataSource);
    }

    /**
     * 내부 클래스: 실제 라우팅 로직 구현
     */
    static class ReplicationRoutingDataSource extends AbstractRoutingDataSource {
        @Override
        protected Object determineCurrentLookupKey() {
            // 현재 트랜잭션이 Read Only면 "slave", 아니면 "master" 반환
            boolean isReadOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
            return isReadOnly ? "slave" : "master";
        }
    }
}