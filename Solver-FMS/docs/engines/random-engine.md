# random-engine

**Puerto 8092** · `com.delivera.fms.engine.random` · clase principal
[`RandomRouteSolver`](../../engines/random-engine/src/main/java/com/delivera/fms/engine/random/service/RandomRouteSolver.java)

## Para qué sirve

Es la **línea base**. Produce una solución válida en cuanto a capacidad sin ningún criterio de
optimización, de modo que cualquier otro motor debe batirla claramente. Si un algoritmo nuevo no
mejora sensiblemente al aleatorio, algo va mal en él.

No está pensado para producción.

## Algoritmo

```
1. Barajar la lista de clientes                    Collections.shuffle
2. Agrupar cada cliente con su depósito más cercano
3. Por cada vehículo:
      recorrer los clientes de su depósito en el orden barajado
      ir acumulando en la ruta hasta que no quepa uno más por capacidad
      cerrar la ruta, volver al depósito y abrir otra
4. Los clientes que hayan quedado sin servir van a una ruta de respaldo
```

El orden de visita dentro de una ruta es el orden barajado: no se hace ningún intento de acercar
clientes próximos entre sí. De ahí que el coste sea alto.

## Detalles de implementación

**Agrupación por depósito más cercano** (`groupByNearestDepot`): cada cliente va al depósito con
menor `distanceMatrix[depósito][cliente]`. Es la misma heurística que usan greedy y la población
inicial del genético.

**Multi-viaje** (`buildMultiTripRoutes`): un mismo vehículo puede aparecer en varias rutas. Al
llenarse, se cierra la ruta y se abre otra con el mismo `vehicleId`. Por eso **el motor no respeta el
número de vehículos por depósito**: no lo necesita, porque asume que un vehículo puede hacer tantos
viajes como haga falta.

**Ruta de respaldo**: si algún cliente queda sin servir porque su depósito no tenía vehículos
asignados, se crea una ruta con `vehicleId = "V-FALLBACK-<depósito>"` y **capacidad ilimitada**. Esa
ruta puede violar la capacidad. Es un mecanismo para no perder clientes, no una solución válida.

**Sin vehículos declarados**: si `vehicles` viene vacío o nulo, se crea una ruta por depósito con
`vehicleId = "V-<depósito>"` y capacidad ilimitada.

## Limitaciones

- Ignora el número de vehículos: usa multi-viaje sin límite.
- La ruta de respaldo puede exceder la capacidad.
- `Collections.shuffle` sin semilla: **no es reproducible**. Dos llamadas con la misma entrada dan
  resultados distintos, así que como línea base conviene tomar la media de varias ejecuciones y no una
  suelta.

## Coste computacional

Lineal en el número de clientes, más el barajado. Responde en milisegundos incluso en las instancias
grandes.
