# Arquitectura

## Módulos

Solver-FMS son cuatro aplicaciones Spring Boot independientes, cada una con su propio `pom.xml`,
su propio `Dockerfile` y su propio contenedor. No hay POM padre: cada módulo se compila por separado.

| Módulo | Artefacto | Puerto | Responsabilidad |
|---|---|---|---|
| `fms-gateway` | `fms-gateway` | 8090 | API pública, validación, despacho, carga de instancias, OpenAPI |
| `engines/greedy-engine` | `greedy-engine` | 8091 | Resolución voraz |
| `engines/random-engine` | `random-engine` | 8092 | Resolución aleatoria |
| `engines/genetic-engine` | `genetic-engine` | 8093 | Resolución con algoritmo genético |

Java 22, Spring Boot 3.4.0. El motor genético añade jMetal 6.6, del que usa la representación
`PermutationSolution` y el generador de números aleatorios; el algoritmo en sí está escrito a mano,
no usa los algoritmos de jMetal.

### Por qué los DTOs están duplicados

Cada motor tiene su **propia copia** de `CustomerDto`, `DepotDto`, `VehicleDto`, `RouteDto`,
`RoutingRequest` y `RoutingResponse`, en su propio paquete. No hay módulo compartido.

La consecuencia práctica: **un motor solo entiende los campos que su copia declara**. La pasarela
envía `DepotDto.maxDuration` y `CustomerDto.serviceDuration`, pero solo el motor genético los tiene
declarados; greedy y random los ignoran silenciosamente. Esto funciona porque Spring Boot desactiva
`FAIL_ON_UNKNOWN_PROPERTIES` por defecto, así que un campo de más no rompe la deserialización.

Si añades un campo al contrato, tenlo en cuenta: **hay que replicarlo en la copia de cada motor que
deba usarlo**, y omitirlo en los que no lo necesiten es una decisión, no un olvido.

## Flujo de una petición

```
1. POST /api/v1/fms/routing/solve            RoutingController
2. Validación de Bean Validation             @Valid sobre RoutingRequest
3. Validación de consistencia                validateConsistency()
4. Selección de WebClient por solverType     EngineDispatcher
5. Resolución de parámetros del solver       SolverRegistry.resolveParameters()
6. POST http://<motor>/api/v1/engine/solve   timeout 300 s
7. El motor resuelve                         <X>RouteSolver.solve()
8. La respuesta se devuelve sin transformar   ResponseEntity.ok(response)
```

### Validaciones de la pasarela

`RoutingController.validateConsistency` comprueba, antes de despachar:

- La matriz de distancias es cuadrada y de tamaño exactamente `depots + customers`.
- Los `matrixIndex` de depósitos y clientes son **únicos entre sí** y están dentro del rango.
- Cada vehículo referencia un `startDepotId` que existe entre los depósitos.

Superado eso, `EngineDispatcher` completa los **parámetros del solver** que no venían en la petición
con los valores por defecto declarados en sus metadatos, de modo que el motor siempre recibe la
configuración completa. Un parámetro no declarado se descarta con un aviso en el log; uno fuera del
rango declarado devuelve 400. Ver [metadatos-solvers.md](metadatos-solvers.md).

Los motores dan por hecho que esto ya se ha validado y **no lo vuelven a comprobar**. Si llamas a un
motor directamente en el puerto 8091-8093, saltándote la pasarela, una matriz mal dimensionada
provocará un `ArrayIndexOutOfBoundsException`, no un error 400.

## Endpoints

### Pasarela (8090)

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/fms/routing/solve` | Resuelve un problema enviado en el cuerpo de la petición |
| `POST` | `/api/v1/fms/instances/send?fileName=p01&solverType=GREEDY` | Carga una instancia del disco, la mapea y la resuelve |
| `GET` | `/api/v1/fms/solvers` | Catálogo de solvers con sus metadatos |
| `GET` | `/api/v1/fms/solvers/{type}` | Metadatos de un solver concreto |
| `GET` | `/api-docs` | Especificación OpenAPI |
| `GET` | `/swagger-ui/index.html` | Swagger UI |
| `GET` | `/actuator/health` | Estado |

> **Ojo con la ruta de OpenAPI.** Está personalizada con `springdoc.api-docs.path: /api-docs`, así que
> **no** está en la ruta por defecto `/v3/api-docs`. Cualquier cliente que descubra el contrato
> automáticamente debe apuntar a `/api-docs`.

`sendInstance` normaliza la ruta del fichero y comprueba que quede dentro del directorio de
instancias, para que un `fileName` con `../` no permita leer ficheros arbitrarios.

### Motores (8091, 8092, 8093)

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/engine/solve` | Resuelve el problema recibido |
| `GET` | `/actuator/health` | Estado |

Los tres motores exponen exactamente el mismo contrato. Añadir un cuarto motor es: implementar esos
dos endpoints —en la tecnología que sea—, añadir el valor al enum `TypeSolver` y declarar su bloque
bajo `fms.engines` en `application.yml`. Ninguna clase Java más cambia: el cliente HTTP, el despacho,
el catálogo y los parámetros se derivan de esa configuración. El detalle de qué declarar está en
[metadatos-solvers.md](metadatos-solvers.md).

## Configuración

Cada motor se declara bajo `fms.engines.<motor>`: además de la URL, ahí viven sus metadatos
(descripción, familia algorítmica y parámetros con sus valores por defecto). En `docker-compose.yml`
solo se sobrescriben las URL, por variables de entorno:

| Variable | Valor por defecto |
|---|---|
| `FMS_ENGINES_GREEDY_URL` | `http://greedy-engine:8091` |
| `FMS_ENGINES_RANDOM_URL` | `http://random-engine:8092` |
| `FMS_ENGINES_GENETIC_URL` | `http://genetic-engine:8093` |
| `FMS_INSTANCES_DIR` | `/app/instances-MD-CVRP-JSON` |

El directorio de instancias se monta como volumen de solo lectura desde
`./instances-MD-CVRP-JSON`, así que se pueden añadir instancias sin reconstruir la imagen.

## Manejo de errores

`EngineDispatcher` distingue dos casos:

- Si el motor responde con un código de error HTTP, la `WebClientResponseException` se relanza tal
  cual y el `GlobalExceptionHandler` de la pasarela la traduce.
- Cualquier otro fallo de comunicación (motor caído, timeout de 300 s, respuesta vacía) se envuelve
  en `EngineUnavailableException`.

## Despliegue

```bash
docker compose up --build
```

Los cuatro contenedores comparten la red `fms-network`. La pasarela declara `depends_on` con
`condition: service_healthy`, de modo que no arranca hasta que los tres motores responden a su
*healthcheck*.

Para desarrollo, cada módulo se puede arrancar suelto:

```bash
mvn spring-boot:run
```

En ese caso hay que apuntar las URL a `localhost`, porque los nombres `greedy-engine`,
`random-engine` y `genetic-engine` solo resuelven dentro de la red de Docker.
