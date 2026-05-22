# config-repo

Directorio de configuración centralizada. El `config-server` lee estos ficheros y los sirve a cada microservicio a través de su API REST.

## Estructura

```
config-repo/
├── application.yml                        ← config compartida por TODOS los servicios
├── config-client/
│   ├── config-client.yml                  ← config base del config-client
│   ├── config-client-desarrollo.yml       ← config del config-client en desarrollo
│   └── config-client-produccion.yml       ← config del config-client en producción
├── eureka-client/
│   └── eureka-client.yml
└── eureka-server/
    └── eureka-server.yml
```

Cada microservicio tiene su propia subcarpeta. El servidor busca ficheros en la raíz y 
en la subcarpeta del servicio que hace la petición.

## Reglas de resolución y fusión

Cuando un servicio solicita su configuración, el servidor fusiona los ficheros en este 
orden de **menor a mayor prioridad**:

```
application.yml                  ← base compartida (menor prioridad)
<servicio>.yml                   ← propiedades base del servicio
<servicio>-<perfil>.yml          ← propiedades específicas del perfil (mayor prioridad)
```

Las propiedades del fichero de mayor prioridad sobreescriben a las de menor prioridad. Las que no aparecen en el fichero de mayor prioridad se heredan del de menor prioridad.

### Ejemplo con `config-client` y perfil `desarrollo`

El servidor fusiona en este orden:

| Prioridad | Fichero | Qué aporta |
|---|---|---|
| 1 (base) | `application.yml` | Configuración de Actuator compartida |
| 2 | `config-client/config-client.yml` | Puerto, `app.limitePeticiones: 50` |
| 3 (mayor) | `config-client/config-client-desarrollo.yml` | `app.limitePeticiones: 10`, log DEBUG |

El resultado final que recibe el servicio tiene `limitePeticiones: 10` (sobreescrito por el perfil) y la configuración de Actuator (heredada de `application.yml`).

## Cómo añadir un nuevo microservicio

1. Crear la subcarpeta con el nombre exacto del servicio (`spring.application.name`):
   ```bash
   mkdir config-repo/nombre-servicio
   ```

2. Crear el fichero base:
   ```
   config-repo/nombre-servicio/nombre-servicio.yml
   ```

3. Opcionalmente, crear un fichero por cada perfil que necesite configuración diferente:
   ```
   config-repo/nombre-servicio/nombre-servicio-desarrollo.yml
   config-repo/nombre-servicio/nombre-servicio-produccion.yml
   ```

4. El Config Server sirve los cambios en la siguiente petición sin reiniciarse.

## Cómo consultar la configuración servida

Con el `config-server` arrancado en `localhost:8888`:

```bash
# Configuración fusionada de un servicio (perfil por defecto)
curl http://localhost:8888/config-client/default

# Configuración fusionada con un perfil concreto
curl http://localhost:8888/config-client/desarrollo
curl http://localhost:8888/config-client/produccion

# YAML resultante de la fusión para un perfil
curl http://localhost:8888/config-client-desarrollo.yml
```

## Configuración `application.yml` (compartida)

Las propiedades de este fichero aplican a **todos** los servicios sin excepción.
Úsalo para configuración verdaderamente transversal: Actuator, niveles de log por defecto, timeouts globales, etc.

Evita poner aquí propiedades que solo sean relevantes para un servicio concreto — esas van en la subcarpeta del servicio.

## Estructura alternativa: todos los ficheros en la raíz

Spring Cloud Config Server también funciona con todos los YAML en la raíz sin subcarpetas:

```
config-repo/
├── application.yml
├── config-client.yml
├── config-client-desarrollo.yml
└── eureka-server.yml
```

La estructura con subcarpetas por servicio es preferible cuando hay muchos servicios porque evita que la raíz se llene de ficheros y agrupa la configuración de cada servicio en un único lugar.
