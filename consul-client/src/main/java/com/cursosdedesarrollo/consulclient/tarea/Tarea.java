package com.cursosdedesarrollo.consulclient.tarea;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("tarea")
public class Tarea {

    @Id
    private Long id;

    private String nombre;

    private String descripcion;

    @Builder.Default
    private Boolean completada = false;
}
