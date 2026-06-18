package com.segment.segmentmetricservice.config.datasource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.hibernate.autoconfigure.HibernateProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernateSettings;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jpa.EntityManagerFactoryBuilder;
import org.springframework.boot.jpa.autoconfigure.JpaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableJpaRepositories(
        basePackages = {
                "com.segment.segmentmetricservice.domain.segment",
                "com.segment.segmentmetricservice.domain.metric"
        },
        entityManagerFactoryRef = "segmentEntityManagerFactory",
        transactionManagerRef = "segmentTransactionManager"
)
public class SegmentDataSourceConfig {

    @Bean
    @ConfigurationProperties("spring.datasource.segment-master")
    public DataSourceProperties segmentMasterDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.segment-master.hikari")  // ← 여기 핵심!
    public DataSource segmentMasterDataSource(
            @Qualifier("segmentMasterDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.segment-slave")
    public DataSourceProperties segmentSlaveDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.segment-slave.hikari")  // ← 여기 핵심!
    public DataSource segmentSlaveDataSource(
            @Qualifier("segmentSlaveDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    public DataSource routingSegmentDataSource(
            @Qualifier("segmentMasterDataSource") DataSource masterDataSource,
            @Qualifier("segmentSlaveDataSource") DataSource slaveDataSource) {

        ReplicationRoutingDataSource routingDataSource = new ReplicationRoutingDataSource();

        Map<Object, Object> dataSourceMap = new HashMap<>();
        dataSourceMap.put("master", masterDataSource);
        dataSourceMap.put("slave", slaveDataSource);

        routingDataSource.setTargetDataSources(dataSourceMap);
        routingDataSource.setDefaultTargetDataSource(masterDataSource);

        routingDataSource.afterPropertiesSet();

        return routingDataSource;
    }

    /**
     * 프록시 데이터소스
     * readOnly 트랜잭션인지 먼저 파악한 후 알맞은 데이터소스 연결함
     */
    @Bean
    public DataSource segmentDataSource(@Qualifier("routingSegmentDataSource")  DataSource routingDataSource) {
        return new LazyConnectionDataSourceProxy(routingDataSource);
    }

    @Bean
    public JdbcTemplate segmentJdbcTemplate(
            @Qualifier("segmentDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean segmentEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("segmentDataSource") DataSource dataSource,
            JpaProperties jpaProperties,
            HibernateProperties hibernateProperties) {

        // application.yml의 spring.jpa.* 옵션을 Map으로 변환
        Map<String, Object> props = hibernateProperties.determineHibernateProperties(jpaProperties.getProperties(), new HibernateSettings());

        return builder
                .dataSource(dataSource)
                .packages(
                        "com.segment.segmentmetricservice.domain.segment",
                        "com.segment.segmentmetricservice.domain.metric"
                )
                .persistenceUnit("segment")
                .properties(props)
                .build();
    }

    @Bean
    public PlatformTransactionManager segmentTransactionManager(
            @Qualifier("segmentEntityManagerFactory") LocalContainerEntityManagerFactoryBean segmentEntityManagerFactory) {
        return new JpaTransactionManager(segmentEntityManagerFactory.getObject());
    }
}