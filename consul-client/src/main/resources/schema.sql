CREATE TABLE IF NOT EXISTS tarea (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    nombre      VARCHAR(255) NOT NULL,
    descripcion VARCHAR(1024),
    completada  BOOLEAN      NOT NULL DEFAULT FALSE
);
