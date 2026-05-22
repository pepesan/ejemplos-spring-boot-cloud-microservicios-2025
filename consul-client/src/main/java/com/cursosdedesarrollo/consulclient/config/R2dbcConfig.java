package com.cursosdedesarrollo.consulclient.config;

import com.cursosdedesarrollo.consulclient.DatabaseProperties;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@RequiredArgsConstructor
public class R2dbcConfig {

    private final DatabaseProperties dbProps;

    /**
     * ConnectionFactory construida a partir de la URL almacenada en Consul KV.
     * El perfil activo determina qué clave KV se fusiona, lo que permite apuntar
     * a H2 en desarrollo y a MySQL en producción sin cambiar código.
     */
    @Bean
    @Primary
    public ConnectionFactory connectionFactory() {
        ConnectionFactoryOptions.Builder builder = ConnectionFactoryOptions
                .parse(dbProps.getUrl())
                .mutate();

        if (!dbProps.getUsername().isBlank()) {
            builder.option(ConnectionFactoryOptions.USER, dbProps.getUsername());
        }
        if (!dbProps.getPassword().isBlank()) {
            builder.option(ConnectionFactoryOptions.PASSWORD, dbProps.getPassword());
        }

        return ConnectionFactories.get(builder.build());
    }
}
