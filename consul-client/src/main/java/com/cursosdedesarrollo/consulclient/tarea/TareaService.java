package com.cursosdedesarrollo.consulclient.tarea;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class TareaService {

    private final TareaRepository tareaRepository;

    public Flux<Tarea> findAll() {
        return tareaRepository.findAll();
    }

    public Flux<Tarea> findByCompletada(Boolean completada) {
        return tareaRepository.findByCompletada(completada);
    }

    public Mono<Tarea> findById(Long id) {
        return tareaRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Tarea no encontrada: " + id)));
    }

    public Mono<Tarea> save(Tarea tarea) {
        return tareaRepository.save(tarea);
    }

    public Mono<Tarea> update(Long id, Tarea tarea) {
        return findById(id)
                .map(existing -> {
                    existing.setNombre(tarea.getNombre());
                    existing.setDescripcion(tarea.getDescripcion());
                    existing.setCompletada(tarea.getCompletada());
                    return existing;
                })
                .flatMap(tareaRepository::save);
    }

    public Mono<Void> deleteById(Long id) {
        return findById(id).flatMap(tareaRepository::delete);
    }
}
