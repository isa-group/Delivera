# Documentación de Solver-FMS

Solver-FMS es el subsistema de resolución de rutas de Delivera. Resuelve el problema
**MD-CVRP** (*Multi-Depot Capacitated Vehicle Routing Problem*): dado un conjunto de clientes con
demanda, varios depósitos y una flota de vehículos con capacidad, encontrar el conjunto de rutas de
coste total mínimo que sirva a todos los clientes exactamente una vez.

Está formado por una pasarela y tres motores de resolución independientes, cada uno con una
estrategia distinta, para poder comparar algoritmos sobre las mismas instancias.

## Índice

| Documento | Contenido |
|---|---|
| [arquitectura.md](arquitectura.md) | Módulos, puertos, flujo de una petición, endpoints y despliegue |
| [metadatos-solvers.md](metadatos-solvers.md) | Descriptor de cada solver: descripción, parámetros con sus valores por defecto y alta de un motor nuevo |
| [modelo-de-datos.md](modelo-de-datos.md) | DTOs, matriz de distancias, formato de instancia y restricciones del problema |
| [engines/random-engine.md](engines/random-engine.md) | Motor aleatorio: línea base de referencia |
| [engines/greedy-engine.md](engines/greedy-engine.md) | Motor voraz: vecino más cercano |
| [engines/genetic-engine.md](engines/genetic-engine.md) | Motor genético: representación, operadores, troceado y parámetros |
| [benchmark.md](benchmark.md) | Cómo medir contra las instancias Cordeau, comparar los solvers y resultados actuales |
| [decisiones-y-correcciones.md](decisiones-y-correcciones.md) | Qué se corrigió en el motor genético y por qué |

## Vista rápida

```
                      ┌──────────────────────┐
  Cliente HTTP  ────► │  fms-gateway  :8090  │
                      │  valida y despacha   │
                      └──────────┬───────────┘
                                 │  POST /api/v1/engine/solve
              ┌──────────────────┼──────────────────┐
              ▼                  ▼                  ▼
     ┌────────────────┐ ┌────────────────┐ ┌─────────────────┐
     │ greedy  :8091  │ │ random  :8092  │ │ genetic  :8093  │
     └────────────────┘ └────────────────┘ └─────────────────┘
```

El cliente elige el motor con el campo `solverType` (`RANDOM`, `GREEDY`, `GENETIC`). La pasarela
valida la petición, la reenvía al motor correspondiente y devuelve su respuesta sin transformarla.

Cada solver publica su propia metainformación en `GET /api/v1/fms/solvers`: descripción, familia
algorítmica y los parámetros que admite con sus valores por defecto, que la pasarela aplica cuando la
petición no los trae. Ver [metadatos-solvers.md](metadatos-solvers.md).

Qué restricciones respeta cada motor no está en ese descriptor, sino en la tabla siguiente y en la
ficha de cada uno: es la referencia a tener delante al comparar costes.

## Comparativa de los tres motores

| | random-engine | greedy-engine | genetic-engine |
|---|---|---|---|
| Estrategia | Orden aleatorio | Vecino más cercano | Algoritmo genético con búsqueda local |
| Determinista | No | Sí | No |
| Respeta capacidad | Sí | Sí | Sí |
| Respeta duración máxima | Sí | Sí | Sí |
| Respeta el tamaño de la flota | Sí | Sí | Sí |
| Un vehículo por ruta | No (multi-viaje sin límite) | No (multi-viaje sin límite) | **Sí, salvo excepción** |
| Tiempo en p22 (360 clientes) | milisegundos | milisegundos | ~3 s |
| Calidad en p22 (BKS 5702) | — | — | ~5960 (+4,5 %) |
| Para qué sirve | Cota superior de referencia | Solución rápida razonable | Solución de producción |

Los tres respetan la capacidad del vehículo y la duración máxima de ruta, así que sus costes son
comparables entre sí. La diferencia está en la calidad: el aleatorio es una cota superior de
referencia, el voraz una solución rápida razonable y el genético el de producción. Ver los detalles y
limitaciones en la ficha de cada motor.

## Puesta en marcha

```bash
docker compose up --build
```

Swagger UI queda en `http://localhost:8090/swagger-ui/index.html`.
