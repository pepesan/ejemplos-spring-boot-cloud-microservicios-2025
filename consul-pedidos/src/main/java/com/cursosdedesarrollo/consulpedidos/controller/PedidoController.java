package com.cursosdedesarrollo.consulpedidos.controller;

import com.cursosdedesarrollo.consulpedidos.domain.Pedido;
import com.cursosdedesarrollo.consulpedidos.service.PedidoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/pedidos")
@RequiredArgsConstructor
public class PedidoController {

    private final PedidoService service;

    @GetMapping
    public Flux<Pedido> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public Mono<Pedido> findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Pedido> crear(@RequestBody Map<String, Object> body) {
        Long productoId = Long.valueOf(body.get("productoId").toString());
        Integer cantidad = Integer.valueOf(body.get("cantidad").toString());
        return service.crear(productoId, cantidad);
    }

    @PatchMapping("/{id}/estado")
    public Mono<Pedido> actualizarEstado(@PathVariable Long id,
                                         @RequestParam String estado) {
        return service.actualizarEstado(id, estado);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable Long id) {
        return service.deleteById(id);
    }
}
