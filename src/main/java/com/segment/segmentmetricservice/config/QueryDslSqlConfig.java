package com.segment.segmentmetricservice.config;

import com.querydsl.sql.MySQLTemplates;
import com.querydsl.sql.spring.SpringConnectionProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class QueryDslSqlConfig {

    @Bean
    public com.querydsl.sql.Configuration querydslSqlConfiguration() {
        return new com.querydsl.sql.Configuration(
                com.querydsl.sql.MySQLTemplates.builder().build()
        );
    }

    @Bean
    public com.querydsl.sql.mysql.MySQLQueryFactory mySQLQueryFactory(
            com.querydsl.sql.Configuration configuration,
            @Qualifier("userDataSource")DataSource userDataSource
            ){
        return new com.querydsl.sql.mysql.MySQLQueryFactory(
                configuration,
                new SpringConnectionProvider(userDataSource));
    }
}
