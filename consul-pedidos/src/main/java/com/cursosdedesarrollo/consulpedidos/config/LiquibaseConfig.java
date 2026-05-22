package com.cursosdedesarrollo.consulpedidos.config;

import io.r2dbc.spi.ConnectionFactory;
import liquibase.integration.spring.SpringLiquibase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import java.sql.Driver;

/**
 * Configura Liquibase para un módulo R2DBC puro.
 *
 * Liquibase requiere JDBC. La URL JDBC se deriva automáticamente de la URL R2DBC
 * (spring.r2dbc.url), de forma que cambiar la URL en Consul KV actualiza ambas.
 *
 * ConnectionFactory se inyecta para garantizar que el pool R2DBC inicialice primero.
 */
@Configuration
public class LiquibaseConfig {

    @Value("${spring.r2dbc.url}")
    private String r2dbcUrl;

    @Bean
    @SuppressWarnings("unused")
    public SpringLiquibase liquibase(ConnectionFactory connectionFactory) throws ClassNotFoundException {
        String jdbcUrl = toJdbcUrl(r2dbcUrl);

        SimpleDriverDataSource dataSource = new SimpleDriverDataSource();
        dataSource.setDriverClass(detectDriver(jdbcUrl));
        dataSource.setUrl(jdbcUrl);
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        return liquibase;
    }

    static String toJdbcUrl(String r2dbcUrl) {
        return r2dbcUrl
                .replace("r2dbc:h2:mem:///", "jdbc:h2:mem:")
                .replaceFirst("^r2dbc:", "jdbc:");
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Driver> detectDriver(String jdbcUrl) throws ClassNotFoundException {
        if (jdbcUrl.startsWith("jdbc:h2:")) {
            return (Class<? extends Driver>) Class.forName("org.h2.Driver");
        }
        if (jdbcUrl.startsWith("jdbc:mysql:")) {
            return (Class<? extends Driver>) Class.forName("com.mysql.cj.jdbc.Driver");
        }
        throw new IllegalStateException("No hay driver JDBC configurado para: " + jdbcUrl);
    }
}
