package com.cursosdedesarrollo.configclient;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de la aplicación leídas desde el Config Server bajo el prefijo {@code app}.
 *
 * <p>Cada perfil ({@code desarrollo}, {@code produccion}) puede sobreescribir
 * cualquiera de estos valores en su fichero correspondiente del repositorio de
 * configuraciones.
 */
@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** Mensaje descriptivo del entorno activo. */
    private String mensaje = "sin configuración";

    /** Límite máximo de peticiones por minuto permitido en este entorno. */
    private int limitePeticiones = 0;

    /** Nombre del entorno (desarrollo, produccion…). */
    private String entorno = "desconocido";
}
