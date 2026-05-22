package com.cursosdedesarrollo.consulclient;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

/**
 * Propiedades leídas desde Consul KV en la ruta config/consul-client/data.
 *
 * El watcher de Spring Cloud Consul detecta cambios en el KV y refresca
 * automáticamente este bean sin reiniciar la aplicación gracias a @RefreshScope.
 *
 * Ejemplo de YAML a almacenar en Consul KV (config/consul-client/data):
 *
 *   consulclient:
 *     mensaje: "Hola desde Consul KV!"
 *     limite: 42
 *     entorno: "consul"
 */
@Data
@RefreshScope
@ConfigurationProperties(prefix = "consulclient")
public class ConsulConfigProperties {

    private String mensaje = "configuración local (Consul KV no disponible)";
    private int limite = 100;
    private String entorno = "local";
}
