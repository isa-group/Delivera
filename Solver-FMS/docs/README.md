# Documentación de Solver-FMS

Solver-FMS es el subsistema de resolución de rutas de Delivera. Resuelve el problema
**MD-CVRP** (*Multi-Depot Capacitated Vehicle Routing Problem*): dado un conjunto de clientes con
demanda, varios depósitos y una flota de vehículos con capacidad, encontrar el conjunto de rutas de
coste total mínimo que sirva a todos los clientes exactamente una vez.

Está formado por una pasarela y cuatro motores de resolución independientes, cada uno con una
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
| [engines/annealing-engine.md](engines/annealing-engine.md) | Motor de recocido simulado: vecindarios, calibración de la temperatura, factibilidad y curva anytime |
| [benchmark.md](benchmark.md) | Cómo medir contra las instancias Cordeau, comparar los solvers y resultados actuales |
| [decision-tree.md](decision-tree.md) | Árbol de decisión sobre esas medidas: qué solver conviene según la instancia |
| [decisiones-y-correcciones.md](decisiones-y-correcciones.md) | Qué se corrigió en el motor genético y por qué |

## Vista rápida

```
                      ┌──────────────────────┐
  Cliente HTTP  ────► │  fms-gateway  :8090  │
                      │  valida y despacha   │
                      └──────────┬───────────┘
                                 │  POST /api/v1/engine/solve
          ┌──────────────┬───────┴────────┬──────────────────┐
          ▼              ▼                ▼                  ▼
  ┌──────────────┐ ┌──────────────┐ ┌───────────────┐ ┌─────────────────┐
  │ greedy :8091 │ │ random :8092 │ │ genetic :8093 │ │ annealing :8094 │
  └──────────────┘ └──────────────┘ └───────┬───────┘ └────────┬────────┘
                                            └───── routing-core ┘
```

El cliente elige el motor con el campo `solverType` (`RANDOM`, `GREEDY`, `GENETIC`, `ANNEALING`). La
pasarela valida la petición, la reenvía al motor correspondiente y devuelve su respuesta sin
transformarla. Los dos motores metaheurísticos comparten `routing-core`, la función objetivo, para
que sus costes sean comparables.

Cada solver publica su propia metainformación en `GET /api/v1/fms/solvers`: descripción, familia
algorítmica y los parámetros que admite con sus valores por defecto, que la pasarela aplica cuando la
petición no los trae. Ver [metadatos-solvers.md](metadatos-solvers.md).

Qué restricciones respeta cada motor no está en ese descriptor, sino en la tabla siguiente y en la
ficha de cada uno: es la referencia a tener delante al comparar costes.

## Comparativa de los cuatro motores

| | random-engine | greedy-engine | genetic-engine | annealing-engine |
|---|---|---|---|---|
| Estrategia | Orden aleatorio | Vecino más cercano | Algoritmo genético con búsqueda local | Recocido simulado con búsqueda local |
| Determinista | No | Sí | No | No (sí con `seed` y `maxLevels`) |
| Criterio de parada | - | - | Estancamiento | **Presupuesto de tiempo** |
| Respeta capacidad | Sí | Sí | Sí | Sí |
| Respeta duración máxima | Sí | Sí | Sí | Sí |
| Respeta el tamaño de la flota | Sí | Sí | Sí | Sí |
| Un vehículo por ruta | No (multi-viaje sin límite) | No (multi-viaje sin límite) | **Sí, salvo excepción** | **Sí, salvo excepción** |
| Tiempo en p22 (360 clientes) | milisegundos | milisegundos | ~3-6 s | el que se le dé (5 s por defecto) |
| Calidad en p22 (BKS 5702) | - | - | ~6030 (+5,8 %) | ~5890 (+3,3 %), 1,8-4,2 % según semilla |
| Para qué sirve | Cota superior de referencia | Solución rápida razonable | Solución de producción | Solución de producción con tiempo acotado y curva anytime |

Los cuatro respetan la capacidad del vehículo y la duración máxima de ruta, así que sus costes son
comparables entre sí. La diferencia está en la calidad: el aleatorio es una cota superior de
referencia, el voraz una solución rápida razonable y los dos metaheurísticos los de producción, con
perfiles distintos: el genético gana cuando la flota está apretada (`p07`) y el recocido cuando hay
holgura y presupuesto (`p01`, `p03`, `p22`). Ninguno domina, que es lo que hace que la elección de
solver deje de ser trivial. Ver los detalles y limitaciones en la ficha de cada motor.

## Puesta en marcha

```bash
docker compose up --build
```

Swagger UI queda en `http://localhost:8090/swagger-ui/index.html`.
