# greedy-engine

**Puerto 8091** · `com.delivera.fms.engine.greedy` · clase principal
[`GreedyRouteSolver`](../../engines/greedy-engine/src/main/java/com/delivera/fms/engine/greedy/service/GreedyRouteSolver.java)

## Para qué sirve

Solución rápida y **determinista** de calidad razonable. Es la opción por defecto de
`/api/v1/fms/instances/send` y la que conviene cuando importa más responder en milisegundos que
apurar el coste.

## Algoritmo

Heurística del **vecino más cercano**, aplicada por depósito y por vehículo:

```
1. Agrupar cada cliente con su depósito más cercano
2. Repartir los clientes del depósito entre sus vehículos
      baseStops = clientes / vehículos
      cada vehículo se lleva baseStops paradas, el último se lleva el resto
3. Por cada vehículo, construir sus rutas:
      partir del depósito
      elegir repetidamente el cliente no visitado más cercano al punto actual
      si no cabe por capacidad, descartarlo y seguir con el siguiente
      cerrar la ruta al llegar al tope de paradas, y abrir otra si quedan clientes
4. Los clientes que hayan quedado sin servir van a una ruta de respaldo
```

## Detalles de implementación

**Reparto de paradas** (`baseStops`): los clientes de un depósito se dividen a partes iguales entre
sus vehículos, y el **último vehículo recibe `maxStops = Integer.MAX_VALUE`**, es decir, todo lo que
sobre. El reparto es por *número de paradas*, no por carga, así que un vehículo puede quedarse con
todas las paradas de demanda alta y otro con las de demanda baja.

**Descarte por capacidad**: cuando el cliente más cercano no cabe, se elimina de la lista de
candidatos *de ese viaje* y se sigue con el siguiente más cercano. No se abre una ruta nueva de
inmediato; se intenta llenar la actual con lo que quepa.

**Multi-viaje** (`buildGreedyMultiTripRoutes`): igual que en el motor aleatorio, un `vehicleId` puede
aparecer en varias rutas. Por eso este motor tampoco respeta el número de vehículos.

**Ruta de respaldo**: los clientes que queden sin servir se agrupan en una ruta
`V-FALLBACK-<depósito>` con **capacidad ilimitada**, que puede violar la capacidad.

## Limitaciones

- Ignora `maxDuration`.
- Ignora el número de vehículos por depósito (multi-viaje sin límite).
- La ruta de respaldo puede exceder la capacidad.
- El reparto por número de paradas ignora la demanda, y puede dejar los vehículos muy
  desequilibrados en carga.
- El vecino más cercano tiene el defecto clásico: deja "olvidados" clientes aislados que se recogen
  al final con trayectos muy largos.
- Solo optimiza el orden de visita. No reasigna clientes entre depósitos ni mejora las rutas una vez
  construidas.

## Coste computacional

O(n²) por depósito por la búsqueda del vecino más cercano. Responde en milisegundos en todas las
instancias del banco de pruebas.

## Comparación con el motor genético

El genético usa esta misma heurística —vecino más cercano desde el depósito más próximo— para
sembrar parte de su población inicial, pero **aleatorizada**: en vez del cliente más cercano elige al
azar entre los 3 más cercanos, para no generar 30 individuos idénticos. Ver
[genetic-engine.md](genetic-engine.md).
