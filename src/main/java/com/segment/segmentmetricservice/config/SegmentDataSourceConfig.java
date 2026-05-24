package com.segment.segmentmetricservice.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
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

    @Primary
    @Bean
    @ConfigurationProperties("spring.datasource.segment-master")
    public DataSourceProperties segmentDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean
    @ConfigurationProperties("spring.datasource.segment-master.hikari")  // ← 여기 핵심!
    public DataSource segmentDataSource(
            @Qualifier("segmentDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Primary
    @Bean
    public LocalContainerEntityManagerFactoryBean segmentEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("segmentDataSource") DataSource dataSource) {
        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
        props.put("hibernate.hbm2ddl.auto", "none");
        props.put("hibernate.jdbc.batch_size", "100");
        props.put("hibernate.order_inserts", "true");

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

    @Primary
    @Bean
    public PlatformTransactionManager segmentTransactionManager(
            @Qualifier("segmentEntityManagerFactory") LocalContainerEntityManagerFactoryBean segmentEntityManagerFactory) {
        return new JpaTransactionManager(segmentEntityManagerFactory.getObject());
    }
}