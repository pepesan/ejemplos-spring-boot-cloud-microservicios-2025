package com.cursosdedesarrollo.serviciopedidos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import reactor.core.publisher.Hooks;

@SpringBootApplication
public class ServicioPedidosApplication {

    public static void main(String[] args) {
        // Propaga el contexto de traza (ObservationContext) desde el Reactor Context
        // al ThreadLocal de Brave. Sin esto, las llamadas imperativas dentro de
        // callbacks reactivos (doOnSuccess, StreamBridge.send) no heredan el traceId
        // del span padre y cada operación genera una traza nueva e independiente.
        Hooks.enableAutomaticContextPropagation();
        SpringApplication.run(ServicioPedidosApplication.class, args);
    }
}
