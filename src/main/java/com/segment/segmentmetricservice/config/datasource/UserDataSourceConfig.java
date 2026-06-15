package com.segment.segmentmetricservice.config.datasource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernateProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernateSettings;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Configuration
@EnableJpaRepositories(
        basePackages = "com.segment.segmentmetricservice.domain.user",
        entityManagerFactoryRef = "userEntityManagerFactory",
        transactionManagerRef = "userTransactionManager"
)
public class UserDataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.user-master")
    public DataSourceProperties userMasterDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.user-master.hikari")
    public DataSource userMasterDataSource(
            @Qualifier("userMasterDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.user-slave")
    public DataSourceProperties userSlaveDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.user-slave.hikari")
    public DataSource userSlaveDataSource(
            @Qualifier("userSlaveDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    public DataSource routingUserDataSource(
            @Qualifier("userMasterDataSource") DataSource masterDataSource,
            @Qualifier("userSlaveDataSource") DataSource slaveDataSource) {

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
    @Primary
    public DataSource userDataSource(@Qualifier("routingUserDataSource")  DataSource routingDataSource) {
        return new LazyConnectionDataSourceProxy(routingDataSource);
    }

    @Bean
    @Primary
    public JdbcTemplate userJdbcTemplate(
            @Qualifier("userDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean userEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("userDataSource") DataSource dataSource,
            JpaProperties jpaProperties,
            HibernateProperties hibernateProperties) {

        // application.yml의 spring.jpa.* 옵션을 Map으로 변환
        Map<String, Object> props = hibernateProperties.determineHibernateProperties(jpaProperties.getProperties(), new HibernateSettings());

        return builder
                .dataSource(dataSource)
                .packages("com.segment.segmentmetricservice.domain.user")
                .persistenceUnit("user")
                .properties(props)
                .build();
    }

    @Bean
    @Primary
    public PlatformTransactionManager userTransactionManager(
            @Qualifier("userEntityManagerFactory") LocalContainerEntityManagerFactoryBean userEntityManagerFactory) {
        return new JpaTransactionManager(userEntityManagerFactory.getObject());
    }

    @Bean
    @Primary
    public TransactionTemplate userTransactionTemplate(
            @Qualifier("userTransactionManager")  PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

}