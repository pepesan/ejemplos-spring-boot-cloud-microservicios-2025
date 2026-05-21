package com.cursosdedesarrollo.adminserver;

import de.codecentric.boot.admin.server.config.AdminServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.logout.RedirectServerLogoutSuccessHandler;

import java.net.URI;

@Configuration
public class SecurityConfig {

    private final String adminContextPath;

    public SecurityConfig(AdminServerProperties adminServerProperties) {
        this.adminContextPath = adminServerProperties.getContextPath();
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(adminContextPath + "/assets/**").permitAll()
                        .pathMatchers(adminContextPath + "/actuator/info").permitAll()
                        .pathMatchers(adminContextPath + "/actuator/health").permitAll()
                        .pathMatchers(adminContextPath + "/login").permitAll()
                        .anyExchange().authenticated()
                )
                .formLogin(login -> login
                        .loginPage(adminContextPath + "/login")
                        .authenticationSuccessHandler(
                                new RedirectServerAuthenticationSuccessHandler(adminContextPath + "/"))
                )
                .logout(logout -> {
                    var handler = new RedirectServerLogoutSuccessHandler();
                    handler.setLogoutSuccessUrl(URI.create(adminContextPath + "/login?logout"));
                    logout.logoutSuccessHandler(handler);
                })
                .httpBasic(Customizer.withDefaults())
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .build();
    }
}
