package com.roadrats.demo.config;

import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration
@EnableJpaRepositories(
    basePackages = "com.roadrats.demo.repository.cls",
    entityManagerFactoryRef = "clsEntityManagerFactory",
    transactionManagerRef = "clsTransactionManager"
)
public class ClsDatabaseConfig {
    
    @Primary
    @Bean(name = "clsEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean clsEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("clsDataSource") DataSource dataSource) {
        return builder
                .dataSource(dataSource)
                .packages("com.roadrats.demo.model.cls")
                .persistenceUnit("cls")
                .build();
    }

    @Primary
    @Bean(name = "clsTransactionManager")
    public PlatformTransactionManager clsTransactionManager(
            @Qualifier("clsEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}

