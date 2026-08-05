# Modelo de datos

## El problema: MD-CVRP

Dado un conjunto de clientes con demanda, varios depósitos y una flota de vehículos con capacidad,
hay que encontrar las rutas de coste total mínimo tales que:

- cada cliente se sirve **exactamente una vez**;
- cada ruta empieza y termina en el mismo depósito;
- la carga de una ruta no supera la capacidad del vehículo;
- la duración de una ruta no supera el máximo del depósito, si lo tiene;
- no se usan más vehículos de los que tiene cada depósito.

El coste que se minimiza es la **distancia total recorrida**. Los tiempos de servicio consumen
duración de ruta pero **no** suman al coste.

## Petición: `RoutingRequest`

| Campo | Tipo | Obligatorio | Significado |
|---|---|---|---|
| `problemId` | `String` | Sí | Identificador libre, se devuelve en la respuesta |
| `depots` | `DepotDto[]` | Sí, no vacío | Depósitos |
| `customers` | `CustomerDto[]` | Sí, no vacío | Clientes a servir |
| `vehicles` | `VehicleDto[]` | No | Flota. Si se omite, se asume capacidad y número ilimitados |
| `distanceMatrix` | `double[][]` | Sí | Matriz cuadrada `[origen][destino]` |
| `solverType` | `RANDOM \| GREEDY \| GENETIC` | Sí | Motor a usar. Solo en la pasarela |
| `parameters` | `Map<String, Object>` | No | Parámetros del solver. Los ausentes toman el valor por defecto declarado en sus metadatos |

`parameters` se resuelve contra los metadatos del solver antes de despachar: los que falten se
completan, los no declarados se descartan con un aviso y uno fuera del rango declarado devuelve 400.
Ver [metadatos-solvers.md](metadatos-solvers.md).

### `DepotDto`

| Campo | Tipo | Obligatorio | Significado |
|---|---|---|---|
| `id` | `String` | Sí | Identificador único |
| `lat`, `lng` | `Double` | Sí | Ubicación. Informativas: las distancias salen de la matriz |
| `matrixIndex` | `Integer` | Sí | Fila/columna de este nodo en la matriz |
| `maxDuration` | `Double` | No | Duración máxima de una ruta que sale de aquí. Ausente o `0` = sin límite |

### `CustomerDto`

| Campo | Tipo | Obligatorio | Significado |
|---|---|---|---|
| `id` | `String` | Sí | Identificador único |
| `demand` | `Integer ≥ 1` | Sí | Unidades a entregar |
| `lat`, `lng` | `Double` | Sí | Ubicación. Informativas |
| `matrixIndex` | `Integer` | Sí | Fila/columna de este nodo en la matriz |
| `serviceDuration` | `Double` | No | Tiempo de servicio. Ausente = `0`. Cuenta para la duración, **no** para el coste |

### `VehicleDto`

| Campo | Tipo | Obligatorio | Significado |
|---|---|---|---|
| `id` | `String` | Sí | Identificador único |
| `capacity` | `Integer ≥ 1` | Sí | Capacidad de carga |
| `startDepotId` | `String` | Sí | Depósito de origen. Debe existir en `depots` |

La flota se deduce contando los vehículos de cada `startDepotId`. **Si un depósito no tiene ningún
vehículo declarado, se le considera sin límite de capacidad ni de número de rutas**, no sin
vehículos. Es la interpretación que hace `capacityByDepot`/`fleetByDepot` en el motor genético.

## La matriz de distancias

Es el único origen de las distancias. `lat`/`lng` no se usan para calcular nada: puedes enviar
distancias de carretera, tiempos de viaje o cualquier otra métrica, y el solver la respeta.

`distanceMatrix[i][j]` es el coste de ir del nodo con `matrixIndex = i` al nodo con
`matrixIndex = j`. Debe ser cuadrada de tamaño `depots.length + customers.length`.

**Convención de indexado** que aplica `StandardInstanceMapper`:

```
índice 0 .. D-1        →  depósitos, en orden
índice D .. D+C-1      →  clientes, en orden
```

No es obligatoria: la pasarela solo exige que los índices sean únicos y estén en rango. Pero es la
que usan el mapeador de instancias y el test de benchmark, así que conviene mantenerla.

Los algoritmos **no asumen que la matriz sea simétrica**, aunque las instancias Cordeau lo son
(distancia euclídea). Sí asumen implícitamente la desigualdad triangular en un punto: el troceado
óptimo del motor genético nunca prefiere usar más rutas de las necesarias porque partir una ruta
sustituye una arista `a→b` por `a→depósito` más `depósito→b`, que nunca es más barato si se cumple
la desigualdad triangular.

## Respuesta: `RoutingResponse`

| Campo | Tipo | Significado |
|---|---|---|
| `problemId` | `String` | El mismo de la petición |
| `status` | `String` | `"COMPLETED"` |
| `solverUsed` | `String` | `"RANDOM"`, `"GREEDY"` o `"GENETIC"` |
| `totalCost` | `Double` | Suma de `totalDistance` de todas las rutas |
| `computationTimeMs` | `Long` | Tiempo de resolución del motor |
| `routes` | `RouteDto[]` | Rutas de la solución |

### `RouteDto`

| Campo | Tipo | Significado |
|---|---|---|
| `vehicleId` | `String` | Vehículo asignado |
| `depotId` | `String` | Depósito de salida y regreso |
| `stops` | `String[]` | IDs de cliente, **en orden de visita** |
| `totalDistance` | `Double` | Distancia del ciclo depósito → paradas → depósito |
| `totalLoad` | `Integer` | Suma de demandas de las paradas |

El depósito **no** aparece en `stops`; está implícito al principio y al final.

> **Diferencia de semántica entre motores.** En greedy y random, un mismo `vehicleId` puede aparecer
> en **varias** rutas: modelan multi-viaje, un vehículo hace varios trayectos. En el motor genético
> cada ruta lleva un vehículo distinto y el número de rutas por depósito nunca supera su flota. Si
> consumes `routes` contando vehículos, ten en cuenta de qué motor viene.
>
> Cuando el motor genético necesita más rutas que vehículos declarados —solo puede pasar si no
> consigue reparar la solución— genera identificadores sintéticos `V-GA-<depósito>-<n>`.

## Formato de instancia de benchmark

Los ficheros de `instances-MD-CVRP-JSON/` los genera `parse_mdcvrp.py` a partir del formato original
de Cordeau. `StandardInstanceParser` los lee y `StandardInstanceMapper` los convierte a
`RoutingRequest`.

```json
{
  "filename": "p22",
  "problem_type": "MDVRP",
  "problem_type_code": 2,
  "vehicles_per_depot": 5,
  "num_customers": 360,
  "num_depots": 9,
  "depots":    [ { "depot_id": 1, "max_duration": 200, "vehicle_capacity": 60,
                   "id": 361, "x": 0.0, "y": 0.0, "service_duration": 0, "demand": 0 } ],
  "customers": [ { "id": 1, "x": -10.0, "y": -10.0, "service_duration": 0, "demand": 12,
                   "visit_frequency": 1, "num_combinations": 9, "visit_combinations": [1, 2, 4] } ]
}
```

Mapeo que aplica `StandardInstanceMapper`:

| Campo de la instancia | Destino |
|---|---|
| `depots[i].x`, `.y` | `DepotDto.lng`, `DepotDto.lat` — **ojo: `x` es longitud, `y` es latitud** |
| `depots[i].max_duration` | `DepotDto.maxDuration` |
| `depots[i].vehicle_capacity` | `VehicleDto.capacity` de sus vehículos |
| `vehicles_per_depot` | Cuántos `VehicleDto` se generan por depósito |
| `customers[i].demand` | `CustomerDto.demand` |
| `customers[i].service_duration` | `CustomerDto.serviceDuration` |
| — | `DepotDto.id` = `"1"`, `"2"`, … según posición |
| — | `CustomerDto.id` = el `id` numérico de la instancia, como texto |

La matriz la calcula `DistanceMatrixCalculator` con distancia **euclídea** sobre `(lng, lat)`.

### Campos que se parsean pero no se usan

`StandardInstanceParser` lee estos campos y los guarda en `NodeEntry`, pero el mapeador no los
propaga y ningún motor los conoce:

- `visit_frequency`, `num_combinations`, `visit_combinations` — pertenecen al problema *periódico*
  (PVRP), no al MD-CVRP.
- `time_window_earliest`, `time_window_latest` — ventanas de tiempo. Ningún motor las soporta.

Están ahí para no perder información al parsear, de cara a soportar esas variantes más adelante.

## Restricciones: quién las respeta

| Restricción | De dónde sale | random | greedy | genetic |
|---|---|---|---|---|
| Cada cliente exactamente una vez | Definición del problema | Sí | Sí | Sí |
| Capacidad del vehículo | `VehicleDto.capacity` | Sí | Sí | Sí |
| Duración máxima de ruta | `DepotDto.maxDuration` | Sí | Sí | Sí |
| Número de vehículos por depósito | Recuento de `vehicles` | No | No | Sí |
| Ventanas de tiempo | No se propaga | No | No | No |

Esta tabla importa al comparar costes: **un motor que ignora restricciones resuelve un problema más
fácil y da costes más bajos que no son comparables**. Ver [benchmark.md](benchmark.md).
