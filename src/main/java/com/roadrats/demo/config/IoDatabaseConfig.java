package com.roadrats.demo.config;

import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration
@EnableJpaRepositories(
    basePackages = "com.roadrats.demo.repository.io",
    entityManagerFactoryRef = "ioEntityManagerFactory",
    transactionManagerRef = "ioTransactionManager"
)
public class IoDatabaseConfig {
    
    @Bean(name = "ioEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean ioEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("ioDataSource") DataSource dataSource) {
        return builder
                .dataSource(dataSource)
                .packages("com.roadrats.demo.model.io")
                .persistenceUnit("io")
                .build();
    }

    @Bean(name = "ioTransactionManager")
    public PlatformTransactionManager ioTransactionManager(
            @Qualifier("ioEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}

