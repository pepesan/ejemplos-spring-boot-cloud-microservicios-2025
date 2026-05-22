package com.cursosdedesarrollo.consulclient;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de conexión a la base de datos leídas desde Consul KV.
 *
 * La clave KV activa depende del perfil Spring:
 *   sin perfil  → config/consul-client/data
 *   desarrollo  → config/consul-client/data  +  config/consul-client,desarrollo/data
 *   produccion  → config/consul-client/data  +  config/consul-client,produccion/data
 *
 * La capa del perfil sobreescribe solo las propiedades que declara.
 * No lleva @RefreshScope: cambiar la URL de BD en caliente no es seguro.
 */
@Data
@ConfigurationProperties(prefix = "consulclient.datasource")
public class DatabaseProperties {

    private String url      = "r2dbc:h2:mem:///localdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE";
    private String username = "sa";
    private String password = "";
    private int    poolSize = 5;
}
