package com.cursosdedesarrollo.consulclient.tarea;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface TareaRepository extends ReactiveCrudRepository<Tarea, Long> {

    Flux<Tarea> findByCompletada(Boolean completada);
}
