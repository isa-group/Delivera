# genetic-engine

**Puerto 8093** · `com.delivera.fms.engine.genetic` · clase principal
[`GeneticRouteSolver`](../../engines/genetic-engine/src/main/java/com/delivera/fms/engine/genetic/service/GeneticRouteSolver.java)

Es el motor de producción y el único que respeta **todas** las restricciones de la instancia:
capacidad, duración máxima de ruta y número de vehículos por depósito.

Algoritmo genético **memético**: población + cruce + mutación, con búsqueda local aplicada
periódicamente a los mejores individuos. Usa jMetal 6.6 solo por la representación
`PermutationSolution` y el generador aleatorio; el algoritmo está escrito a mano.

## Mapa de clases

```
service/
  GeneticRouteSolver        bucle evolutivo, parámetros, decodificación
    └ MDCVRPProblem         función objetivo (clase interna)

scheduler/
  RouteSplitter             troceado óptimo de secuencia en rutas  ← el núcleo
  PermutationCodec          traducción permutación ↔ orden por depósito
  RouteScheduler            construcción de los RouteDto de la respuesta

operator/crossover/
  BestCostRouteCrossover    cruce BCRC

operator/mutation/
  IntraDepotMutation        reordena dentro de un depósito
  InterDepotMutation        reasigna un cliente a otro depósito (diversificación)

operator/search/
  LocalSearch               2-opt intra-ruta + relocate entre rutas del mismo depósito
  InterDepotLocalSearch     reasignación entre depósitos: reparación + mejora
```

## Representación de la solución

Un individuo tiene **dos partes**, porque una permutación sola no basta: define un orden pero no
quién sirve a quién.

**1. La permutación** (`solution.variables()`) — una *gira gigante*: los índices de los clientes en un
orden, sin marcas de dónde empieza y acaba cada ruta.

**2. El mapa de depósitos** (`solution.attributes().get("depotMap")`) — un `Map<Integer, DepotDto>`
que dice a qué depósito pertenece cada cliente.

```
permutación   [ 7, 2, 9, 4, 1, 5, 8, 3, 6 ]
depotMap      7→D1  2→D1  9→D1  4→D2  1→D2  5→D2  8→D1  3→D2  6→D1

secuencia D1  [ 7, 2, 9, 8, 6 ]
secuencia D2  [ 4, 1, 5, 3 ]
```

Solo importa el **orden relativo** de los clientes de un mismo depósito. Que estén intercalados o
agrupados en la permutación es irrelevante para el coste.

Las rutas concretas **no se guardan**: se derivan troceando cada secuencia con `RouteSplitter`.

`PermutationCodec` centraliza esta traducción y ofrece dos formas de reescribir la permutación:

- `writeBack` — conserva las posiciones que ocupa cada depósito. Vale cuando solo cambia el orden
  dentro de los depósitos.
- `writeBackContiguous` — concatena los depósitos. **Es la que hay que usar cuando algún cliente ha
  cambiado de depósito**, porque `writeBack` asume que el conjunto de clientes de cada depósito no
  ha variado.

## `RouteSplitter`: el núcleo

Es el único punto donde un cromosoma se convierte en rutas. Que sea único es deliberado: **la función
objetivo y la respuesta que se devuelve al cliente son exactamente el mismo cálculo**, y no pueden
divergir.

Dada la secuencia de un depósito, encuentra el corte en rutas de **coste mínimo** mediante la
programación dinámica de Prins. No es lo mismo que ir cortando cuando se llena el vehículo: el corte
voraz es fácil de mejorar.

### El DP

```
cost[0] = 0
para cada posición inicial i:
    recorrer clientes hacia adelante acumulando carga, distancia y tiempo de servicio
    parar cuando no quepa uno más por capacidad o por duración
    para cada corte válido i..j:
        cost[j+1] = min(cost[j+1], cost[i] + coste de la ruta i..j)
```

`predecessor[]` permite reconstruir las rutas, y `routeCount[]` cuenta cuántas se han usado.

### El límite de flota va dentro del DP

Si el troceado de coste mínimo necesita más rutas que vehículos tiene el depósito, se relanza un
**segundo DP en dos dimensiones**, `cost[rutas][posición]`, que busca el mejor troceado con como
mucho `fleet` rutas.

Esto es importante y no es un detalle: si la penalización por exceso de flota se aplicara *después*
del troceado, el corte no podría cambiar un poco de distancia por una ruta menos, y cada nuevo
troceado —el de la búsqueda local, el de cada evaluación— desharía la reparación que hubiera hecho
la búsqueda inter-depósito. Ver [decisiones-y-correcciones.md](../decisiones-y-correcciones.md).

Los **tramos de ruta se precalculan una sola vez** (`buildSegments`) y los comparten ambos DP, porque
el DP acotado los recorre una vez por cada número de vehículos. Esto redujo casi a la mitad el tiempo
en las instancias grandes.

### Duración y tiempo de servicio

La duración de una ruta es `distancia + suma de tiempos de servicio`. El **coste** es solo la
distancia. Son cosas distintas y el DP las lleva por separado.

Un cliente cuya demanda supera la capacidad, o cuyo viaje de ida y vuelta ya excede la duración
máxima, forma ruta propia con una penalización, en lugar de dejar la secuencia sin troceado posible.

### Deltas de inserción

`RouteSplitter` ofrece además `insertionCost`, `removalGain` y `bestPosition`, que **todos** los
operadores deben usar. Calculan el delta *dentro de la secuencia de un depósito*, con el depósito
como extremo:

```
insertar c entre a y b   →   d(a,c) + d(c,b) − d(a,b)
```

Medir contra los vecinos de la permutación global sería un error: valoraría aristas entre clientes
de depósitos distintos, que no existen en ninguna ruta real.

## Función objetivo

`MDCVRPProblem.evaluate`, por cada depósito:

```
coste = coste del troceado óptimo
      + FLEET_PENALTY × (rutas − vehículos)   si se pasa de flota
```

`FLEET_PENALTY` y `DURATION_PENALTY` valen 1000, muy por encima del coste de una ruta típica (~160 en
p22), para que una solución infactible nunca gane a una factible.

Las penalizaciones afectan al **fitness**, no al `totalCost` de la respuesta, que es siempre distancia
real recorrida.

## Operadores

### `BestCostRouteCrossover` (BCRC)

Probabilidad **0,9**. Genera dos hijos por cruce, intercambiando los papeles de los padres.

```
1. Elegir un depósito al azar del padre donante y trocear su secuencia
2. Extraer UNA de sus rutas
3. Copiar el padre receptor (el hijo hereda SU asignación cliente-depósito)
4. Quitar del hijo los clientes extraídos
5. Reinsertar cada uno en la posición más barata de la secuencia
   del depósito que le asigna el receptor
```

Es importante que se extraiga **una ruta** (unos 10 clientes) y no todos los clientes del depósito:
extraer el depósito entero convertiría el cruce en una reconstrucción voraz que no hereda estructura
de ningún padre.

El hijo hereda el `depotMap` del **receptor**, que es el coherente con la permutación sobre la que se
construye.

### `IntraDepotMutation`

Probabilidad **0,2**, decidida por individuo. Si se activa, aplica a la secuencia de **cada** depósito
uno de tres operadores elegido al azar:

- **swap** — intercambia dos clientes.
- **invert** — invierte un tramo.
- **relocate** — mueve un cliente a otra posición.

Trabaja sobre la secuencia del depósito, no sobre los bloques contiguos de la permutación. Un
depósito cuyos clientes ya están agrupados es justo el caso que hay que poder mutar.

### `InterDepotMutation`

Probabilidad **0,3**, aplicada solo a los individuos ya seleccionados por el bucle principal (10 al
azar, cada 5 generaciones). Es un operador de **diversificación**, no de mejora:

```
1. Localizar los clientes "frontera": aquellos con otro depósito a menos
   de BORDER_RATIO = 1,3 veces la distancia de su depósito actual
2. Elegir uno al azar
3. Elegir al azar un depósito destino entre los cercanos
4. Insertarlo en su posición más barata dentro de ese depósito
```

El destino se elige **al azar** entre los cercanos, no siempre el segundo más próximo, que daría un
movimiento determinista y casi siempre el mismo. No comprueba si el cambio mejora: de eso se encarga
la selección de la generación siguiente. Lo que sí comprueba es que el depósito destino tenga flota
suficiente para absorber la demanda.

### `LocalSearch`

Búsqueda local **a nivel de ruta**. Trocea la secuencia de cada depósito en rutas reales *antes* de
optimizar, de forma que cada movimiento se evalúa contra el coste que realmente tendrá la solución.

- **2-opt intra-ruta** — invierte un tramo si acorta la ruta. Tras aceptar una inversión reinicia el
  barrido, porque las aristas cacheadas dejan de ser válidas en cuanto el segmento se invierte.
- **relocate entre rutas** del mismo depósito — mueve un cliente a la mejor posición de otra ruta,
  comprobando capacidad.

Hasta `MAX_PASSES = 8` pasadas o hasta que no haya mejora.

> Optimizar la gira gigante como si fuera un TSP y trocearla después **no** es equivalente: puede
> acortar la gira y aumentar el coste final tras el corte.

Se probó añadir intercambio (*swap*) de clientes entre rutas y se descartó: no mejoraba el resultado
de forma medible y añadía código.

### `InterDepotLocalSearch`

Hace dos cosas que se mantienen deliberadamente separadas:

**1. Reparar** (`repairFleet`). Si un depósito necesita más rutas que vehículos tiene, se le saca
carga **aunque cueste distancia**. La aceptación no está condicionada a mejorar el coste: puede no
existir ningún movimiento individual que compense, y un criterio de "solo si mejora" se quedaría
atrapado. Para cada candidato se recorren **todas** las posiciones de inserción del destino, porque el
número de rutas depende de dónde se corte la secuencia y la posición más barata en distancia puede
ser justo la que le añade una ruta.

**2. Mejorar** (`applyBestMove`). Reubica clientes frontera cuando reduce el coste real de los dos
depósitos implicados. El delta estimado solo sirve para ordenar candidatos; antes de aceptar se
recalcula el troceado real de ambos depósitos y se deshace el movimiento si no mejora. Se verifican
hasta `MAX_CANDIDATES = 25` candidatos en lugar de rendirse cuando el primero falla.

## Bucle principal

```
inicializar población (20 % heurística aleatorizada, 80 % aleatoria)
evaluar

mientras queden evaluaciones o generaciones mínimas:

    élites ← copias de los ELITISM_COUNT mejores DISTINTOS

    repetir hasta llenar la descendencia:
        seleccionar 2 padres por torneo de 3 (evitando que sean el mismo)
        cruzar  → 2 hijos
        mutación intra-depósito a cada hijo

    evaluar descendencia

    cada INTER_DEPOT_FREQUENCY generaciones:
        mutación inter-depósito a INTER_DEPOT_INDIVIDUALS hijos al azar

    sustituir los peores por las élites
    población ← descendencia

    cada LOCAL_SEARCH_FREQUENCY generaciones:
        búsqueda local a los TOP_K_LOCAL_SEARCH mejores

    cada INTER_DEPOT_OPT_FREQUENCY generaciones:
        búsqueda local inter-depósito sobre una copia del mejor
        si mejora, inyectarla sustituyendo al peor

    actualizar el mejor histórico (siempre como COPIA independiente)

    si lleva RESTART_STAGNANT generaciones sin mejorar:
        reiniciar la población, o cortar si ya se agotaron los reinicios

pulido final del mejor: inter-depósito + búsqueda local
decodificar a rutas
```

Dos detalles que importan:

- El **mejor histórico se guarda siempre como copia**. Si fuera una referencia a un individuo de la
  población, la búsqueda local lo mutaría por debajo y podría degradarlo sin que el algoritmo se
  entere.
- El **elitismo copia los `k` mejores distintos**, no `k` veces el mejor. Inyectar `k` clones del
  mismo genotipo cada generación provoca convergencia prematura.

## Parámetros

Configurables por petición, en el mapa `parameters`. Los valores por defecto son los de
[`GeneticParameters.DEFAULTS`](../../engines/genetic-engine/src/main/java/com/delivera/fms/engine/genetic/service/GeneticParameters.java)
y están declarados también en los metadatos del solver, que es lo que la pasarela aplica cuando el
cliente no los envía (ver [metadatos-solvers.md](../metadatos-solvers.md)). **Si cambias uno, cámbialo
en los dos sitios**: el descriptor estaría anunciando una ejecución que no es la que ocurre.

| Parámetro | Defecto | Rango | Significado |
|---|---|---|---|
| `populationSize` | 150 | 10–2000 | Individuos por generación |
| `maxEvaluations` | 75000 | 1000–5·10⁶ | Presupuesto de evaluaciones (~500 generaciones) |
| `minGenerations` | 100 | 1–100000 | Suelo antes de permitir un reinicio |
| `crossoverProbability` | 0,9 | 0–1 | Probabilidad de cruce BCRC |
| `intraDepotMutationProbability` | 0,2 | 0–1 | Probabilidad de mutación dentro del depósito |
| `interDepotMutationProbability` | 0,3 | 0–1 | Probabilidad de reasignación entre depósitos |
| `elitismCount` | 5 | 0–100 | Mejores distintos que se conservan cada generación y tras un reinicio |
| `tournamentSize` | 3 | 2–20 | Individuos por torneo de selección |
| `localSearchFrequency` | 10 | 1–1000 | Cada cuántas generaciones se lanza la búsqueda local |
| `interDepotFrequency` | 5 | 1–1000 | Cada cuántas generaciones se muta entre depósitos |
| `restartStagnantGenerations` | 20 | 1–10000 | Generaciones sin mejora antes de reiniciar |
| `maxRestarts` | 3 | 0–100 | Reinicios permitidos; al siguiente estancamiento se corta |
| `heuristicSeedRatio` | 0,2 | 0–1 | Fracción de población inicial construida con heurística |

Siguen siendo constantes de [`GeneticRouteSolver`](../../engines/genetic-engine/src/main/java/com/delivera/fms/engine/genetic/service/GeneticRouteSolver.java),
por no tener recorrido experimental medido:

| Constante | Valor | Significado |
|---|---|---|
| `INTER_DEPOT_INDIVIDUALS` | 10 | Cuántos hijos reciben la mutación inter-depósito (índices con repetición) |
| `INTER_DEPOT_OPT_FREQUENCY` | 30 | Cada cuántas generaciones se lanza la búsqueda inter-depósito |
| `TOP_K_LOCAL_SEARCH` | 3 | A cuántos individuos se les aplica la búsqueda local |
| `SEED_CANDIDATE_LIST` | 3 | Candidatos del vecino más cercano aleatorizado |

Constantes en otras clases:

| Constante | Clase | Valor | Significado |
|---|---|---|---|
| `FLEET_PENALTY` | `RouteSplitter` | 1000 | Sobrecoste por ruta que excede la flota |
| `DURATION_PENALTY` | `RouteSplitter` | 1000 | Sobrecoste por ruta que no cabe en la duración |
| `MAX_PASSES` | `LocalSearch` | 8 | Pasadas máximas de 2-opt + relocate |
| `BORDER_RATIO` | `InterDepot*` | 1,3 | Umbral para considerar un cliente "frontera" |
| `MAX_ITERATIONS` | `InterDepotLocalSearch` | 10 | Movimientos de mejora por llamada |
| `MAX_REPAIR_MOVES` | `InterDepotLocalSearch` | 20 | Movimientos de reparación de flota |
| `MAX_CANDIDATES` | `InterDepotLocalSearch` | 25 | Candidatos verificados antes de rendirse |

### Qué termina realmente la ejecución

`MAX_EVALUATIONS` **casi nunca se alcanza**. Quien corta es el criterio de estancamiento: el primer
reinicio no puede darse antes de la generación 100, y luego hacen falta 20 generaciones estancadas
por cada uno de los 3 reinicios. En la práctica las ejecuciones acaban entre la generación 160 y la
210, con 25 000–33 000 evaluaciones. `MIN_GENERATIONS` tampoco llega a actuar nunca.

Si quieres experimentar buscando mejores soluciones, los parámetros con recorrido son
`restartStagnantGenerations` y `maxRestarts`, no `maxEvaluations`. Está medido: subir el presupuesto o
la frecuencia de búsqueda local no mueve el coste.

El contador `evaluations` es **aproximado**: la búsqueda local suma 3 cuando en realidad hace miles
de cálculos de distancia, y el cruce voraz no suma nada.

## Población inicial

- **20 % heurística** — vecino más cercano **aleatorizado**: en cada paso elige al azar entre los 3
  clientes más próximos. Sin esa aleatorización los 30 individuos saldrían idénticos.
- **80 % aleatoria** — permutaciones barajadas.

En ambos casos el `depotMap` asigna cada cliente a su depósito más cercano. Esa asignación inicial
**puede ser infactible por flota** en algunas instancias, y es la búsqueda inter-depósito la que la
repara.

## Rendimiento

Sobre las 33 instancias Cordeau: entre 0,6 s y 12 s, con la mayoría por debajo de 3 s. Las más
lentas son `pr06` y `pr10`, donde la restricción de duración obliga a lanzar a menudo el DP acotado.

El motor es **secuencial**: no aprovecha más de un núcleo.

## Limitaciones conocidas

- **No es reproducible**: `JMetalRandom` arranca con una semilla derivada del reloj y no se puede fijar
  desde la petición. Dos ejecuciones idénticas dan costes distintos, así que una diferencia pequeña
  entre dos configuraciones puede ser azar; para comparar, repetir varias veces y mirar medias.
- **No soporta ventanas de tiempo.**
- **Flota heterogénea**: toma la capacidad mayor de cada depósito, así que no respeta capacidades
  distintas cliente a cliente.
- El coste llega a una meseta de la que no baja subiendo el presupuesto. Para reducir más el gap
  haría falta gestión de diversidad (*path relinking*, vecindarios granulares).
