package com.cursosdedesarrollo.consulclient.tarea;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/tareas")
@RequiredArgsConstructor
public class TareaController {

    private final TareaService tareaService;

    @GetMapping
    public Flux<Tarea> findAll(@RequestParam(required = false) Boolean completada) {
        return completada != null
                ? tareaService.findByCompletada(completada)
                : tareaService.findAll();
    }

    @GetMapping("/{id}")
    public Mono<Tarea> findById(@PathVariable Long id) {
        return tareaService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Tarea> create(@RequestBody Tarea tarea) {
        return tareaService.save(tarea);
    }

    @PutMapping("/{id}")
    public Mono<Tarea> update(@PathVariable Long id, @RequestBody Tarea tarea) {
        return tareaService.update(id, tarea);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable Long id) {
        return tareaService.deleteById(id);
    }
}
