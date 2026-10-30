# Cambios de PyVRP para la integración

30 de octubre de 2026 · Rama `feature/quantum-vehicle-reassignment`

Los cambios parten del PyVRP heterogéneo de David. El objetivo sigue siendo minimizar distancia, permitiendo reutilizar vehículos dentro del límite configurado. No se minimiza primero el número de viajes adicionales.

## Entrada del nuevo endpoint

`POST /api/v1/HGS-ADC/solve` recibe las opciones en `RoutingRequestStd.parameters`. Las cabeceras `X-*` ya no configuran este endpoint. Fragmento de petición con los valores predeterminados:

```json
{
  "parameters": {
    "classicalTimeLimit": 0.1,
    "seed": 42,
    "maxVehicleRepetition": 1,
    "vehicleStrategy": "future_hybrid",
    "vehicleStrategyConfig": "interurban"
  }
}
```

Se pueden omitir opciones. Se validan tipos, nombres y rangos antes de resolver. `classicalTimeLimit` admite decimales entre 0.1 y 300 segundos. `maxVehicleRepetition` es un entero de 1 a 10 e incluye el primer viaje. Los errores de entrada devuelven HTTP 422.

## Cambios principales

- **Reparto de viajes.** Se conserva la agrupación por depósito y capacidad. Cada camión tiene un contador que impide superar su máximo. `decide()` filtra disponibles, prioriza los que no se han usado, delega con `next(ctx, candidates=eligible)` y actualiza el estado. Robin gestiona su turno en su propia clase.
- **Híbridas.** Los mínimos y máximos se calculan juntos sobre los candidatos disponibles. Se documenta que `future_hybrid` equivale a `hybrid`, porque añadir la misma ruta se cancela en la normalización. Se conservan ambos nombres. No se añade penalización por capacidad sobrante.
- **Distancias.** `distanceMatrix` sigue fuera de `parameters`. Se escala por 1000 para la búsqueda y la salida se calcula sobre la matriz original. Se conserva la asimetría. Si falta, se usa euclídea cartesiana. Una matriz incorrecta se rechaza, sin sustituirla silenciosamente.
- **Respuesta.** Las rutas incorporan `tripIndex`. `solverDetails` informa del modo de flota, máximo de viajes, vehículos usados y viajes adicionales. Es provisional y se puede reorganizar al cerrar el contrato común. El peso de distancia es 1 y los componentes futuros de tiempo y salida quedan a 0.
- **Validación.** Se comprueban visita única, capacidad, depósito y viajes. Sin solución completa se devuelve `isFeasible: false`, `totalCost: null`, rutas vacías y motivo. No se certifica imposibilidad. No hay planificación horaria ni recargas. Se rechazan `maxDuration` y `serviceDuration` distintos de cero.

## Para continuar la integración

1. Registrar estos nombres de parámetros en los metadatos del gateway y conectarlos al frontend.
2. Conectar la entrada común `/api/v1/engine/solve`. El endpoint cuántico nativo conserva sus cabeceras y el clásico, fallback y referencia cuánticos siguen usando el solver anterior.
3. Unificar la salida: `solverUsed` todavía devuelve `HGS-ADC` en el nuevo clásico. Quedan por cerrar `status`, las zonas y la estructura definitiva de detalles por solver, conservándolos en gateway y data-service.

Validación local: **55 pruebas pasan**, incluidas peticiones HTTP con parámetros en el cuerpo. La documentación cuántica original del README se conserva íntegra.
