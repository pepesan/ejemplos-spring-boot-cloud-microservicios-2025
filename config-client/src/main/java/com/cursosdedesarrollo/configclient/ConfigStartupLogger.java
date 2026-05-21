package com.cursosdedesarrollo.configclient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Imprime por log, al arrancar, qué ficheros de configuración fueron cargados
 * desde el Config Server y qué valores efectivos tienen las propiedades {@code app.*}.
 *
 * <p>Spring Cloud Config agrupa los ficheros servidos en una {@link CompositePropertySource}
 * con nombre {@code configserver}. Dentro de ese composite, cada fichero YAML del
 * {@code config-repo/} aparece como una fuente independiente con su nombre original.
 * Inspeccionando esas fuentes se puede saber exactamente qué ficheros se aplicaron
 * y en qué orden de prioridad.
 *
 * <p>Si el Config Server no está disponible y {@code spring.config.import} usa el
 * prefijo {@code optional:}, el cliente arranca igualmente con los valores por defecto
 * definidos en {@link AppProperties}. En ese caso este logger emite un aviso de advertencia.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigStartupLogger implements ApplicationRunner {

    private final AppProperties appProperties;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        String[] perfiles = environment.getActiveProfiles();
        List<String> fuentesConfigServer = resolverFuentesConfigServer();

        log.info("╔══════════════════════════════════════════════════════╗");
        log.info("║           CONFIGURACIÓN CARGADA AL ARRANQUE          ║");
        log.info("╠══════════════════════════════════════════════════════╣");
        log.info("║ Perfiles activos : {}",
                perfiles.length > 0 ? String.join(", ", perfiles) : "(ninguno — perfil por defecto)");
        log.info("╠══════════════════════════════════════════════════════╣");
        log.info("║ Ficheros cargados desde el Config Server (mayor prioridad primero):");

        if (fuentesConfigServer.isEmpty()) {
            log.warn("║   ⚠  Sin fuentes del Config Server — usando valores por defecto.");
            log.warn("║      Comprueba que el config-server está arrancado en localhost:8888");
            log.warn("║      y que spring.config.import apunta a la URL correcta.");
        } else {
            fuentesConfigServer.forEach(nombre -> log.info("║   → {}", nombre));
        }

        log.info("╠══════════════════════════════════════════════════════╣");
        log.info("║ Valores efectivos de app.*:");
        log.info("║   app.entorno          = {}", appProperties.getEntorno());
        log.info("║   app.mensaje          = {}", appProperties.getMensaje());
        log.info("║   app.limitePeticiones = {}", appProperties.getLimitePeticiones());
        log.info("╚══════════════════════════════════════════════════════╝");
    }

    /**
     * Extrae los nombres de las fuentes de configuración servidas por el Config Server.
     *
     * <p>Spring Cloud Config las agrupa en una {@link CompositePropertySource} con nombre
     * {@code configserver}. Se navega dentro del composite para obtener los nombres de
     * los ficheros individuales. También se contempla el caso (versiones antiguas) en que
     * las fuentes tienen nombre con prefijo {@code configserver:}.
     */
    private List<String> resolverFuentesConfigServer() {
        List<String> nombres = new ArrayList<>();
        if (!(environment instanceof ConfigurableEnvironment configurableEnv)) {
            return nombres;
        }

        for (PropertySource<?> ps : configurableEnv.getPropertySources()) {
            if ("configserver".equals(ps.getName()) && ps instanceof CompositePropertySource composite) {
                // Caso habitual: composite con los ficheros del Config Server dentro
                composite.getPropertySources().forEach(hijo -> nombres.add(hijo.getName()));
            } else if (ps.getName().startsWith("configserver:")) {
                // Caso alternativo (formato antiguo o versiones anteriores de Spring Cloud)
                nombres.add(ps.getName());
            }
        }
        return nombres;
    }
}
