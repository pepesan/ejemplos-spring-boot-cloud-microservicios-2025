package com.cursosdedesarrollo.consulclient.config;

import com.cursosdedesarrollo.consulclient.DatabaseProperties;
import io.r2dbc.spi.ConnectionFactory;
import liquibase.integration.spring.SpringLiquibase;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import java.sql.Driver;

/**
 * Configura Liquibase derivando la URL JDBC desde la URL R2DBC de DatabaseProperties.
 *
 * Al cambiar consulclient.datasource.url en Consul KV, Liquibase apuntará
 * automáticamente a la misma base de datos sin ningún cambio de código.
 *
 * ConnectionFactory se inyecta para garantizar que el pool R2DBC inicialice primero.
 */
@Configuration
@RequiredArgsConstructor
public class LiquibaseConfig {

    private final DatabaseProperties dbProps;

    @Bean
    @SuppressWarnings("unused")
    public SpringLiquibase liquibase(ConnectionFactory connectionFactory) throws ClassNotFoundException {
        String jdbcUrl = toJdbcUrl(dbProps.getUrl());

        SimpleDriverDataSource dataSource = new SimpleDriverDataSource();
        dataSource.setDriverClass(detectDriver(jdbcUrl));
        dataSource.setUrl(jdbcUrl);
        dataSource.setUsername(dbProps.getUsername());
        dataSource.setPassword(dbProps.getPassword());

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
