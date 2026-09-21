# annealing-engine

**Puerto 8094** · `com.delivera.fms.engine.annealing` · clase principal
[`AnnealingRouteSolver`](../../engines/annealing-engine/src/main/java/com/delivera/fms/engine/annealing/service/AnnealingRouteSolver.java)
· algoritmo
[`SimulatedAnnealing`](../../engines/annealing-engine/src/main/java/com/delivera/fms/engine/annealing/algorithm/SimulatedAnnealing.java)

Recocido simulado: metaheurística de **solución única** que parte de una solución y la va
modificando con movimientos aleatorios, aceptando siempre los que mejoran y, con probabilidad
`exp(-Δ/T)`, también los que empeoran. La temperatura `T` baja con el tiempo, así que la búsqueda
pasa de explorar a afinar. Es el criterio de Metropolis de **Kirkpatrick, Gelatt y Vecchi (1983)**;
los vecindarios son la adaptación a multi-depósito del *λ-interchange* de **Osman (1993)**.

Está montado sobre la plantilla `AbstractLocalSearch` de jMetal 6.6: un paso de la plantilla es un
nivel de temperatura. jMetal aporta la plantilla y el generador aleatorio; el criterio de
aceptación, el enfriamiento y los vecindarios están escritos a mano.

Comparte con el motor genético el núcleo [`routing-core`](../arquitectura.md#el-núcleo-compartido):
el troceado óptimo, la búsqueda local a nivel de ruta y el reequilibrado entre depósitos. **Eso no
es reutilización, es control experimental**: los dos motores miden el coste con exactamente el mismo
código, así que una diferencia entre ellos es atribuible a la estrategia de búsqueda y no a que
midan cosas distintas.

## Qué lo distingue de los otros motores

| | genetic-engine | annealing-engine |
|---|---|---|
| Población | 150 individuos | 1 solución |
| Criterio de parada | estancamiento y reinicios | **presupuesto de tiempo** |
| Reproducible | sí, con `seed` | solo con `seed` **y** `maxLevels` |
| Curva anytime | no | **sí**, en el campo `trace` |

El presupuesto de tiempo es la diferencia que importa. El genético termina cuando se estanca, tarde
lo que tarde; al recocido se le dice cuánto tiempo tiene y lo usa entero. Eso es lo que permite
preguntarle *"¿qué coste das con 500 ms?"* y comparar motores por su **frontera coste/tiempo** en
lugar de solo por el coste final.

## Mapa de clases

```
service/
  AnnealingRouteSolver     entrada: parámetros, solución inicial, pulido final, decodificación
  AnnealingParameters      configuración de una ejecución
  ProblemMapper            DTO del contrato → modelo del núcleo

algorithm/
  SimulatedAnnealing       el recocido, sobre AbstractLocalSearch<AnnealingSolution> de jMetal

solution/
  AnnealingSolution        secuencias por depósito + asignación + costes cacheados
  Neighborhood             genera el vecino, guarda lo necesario para deshacerlo
```

Todo lo demás -`RouteSplitter`, `RouteOptimizer`, `DepotRebalancer`- viene de `routing-core`.

## Representación

La misma que el genético -orden por depósito más asignación cliente-depósito- pero **sin la
permutación global**, que allí existe solo por la representación de jMetal. Aquí la secuencia por
depósito es la forma canónica y la asignación se deriva de ella.

```
secuencia D1  [ 7, 2, 9, 8, 6 ]
secuencia D2  [ 4, 1, 5, 3 ]
depotOf       7→0  2→0  9→0  8→0  6→0  4→1  1→1  5→1  3→1
```

Las rutas no se guardan: se derivan troceando cada secuencia con `RouteSplitter`. Que ambos motores
recorran **el mismo espacio de soluciones** es deliberado: aísla lo que se quiere comparar.

## La función objetivo es exacta, y por qué es asequible

Cada movimiento se evalúa con el troceado óptimo real, no con un delta sobre la secuencia. Un delta
sería mucho más barato pero **no ve dónde va a caer el corte en rutas**, que es de donde sale el
coste; optimizar la secuencia y trocear después no es equivalente a optimizar el resultado del
troceado.

Que evaluar de verdad sea asequible depende de una sola idea: **un movimiento toca uno o dos
depósitos**, así que solo esos se vuelven a trocear y los demás conservan su coste cacheado.

```
AnnealingSolution
  depotCost[d]   coste penalizado de cada depósito
  totalCost      suma, mantenida por diferencias
```

`Neighborhood` guarda una copia (`DepotBackup`) de cada depósito **justo antes** de tocarlo. Esa
copia hace dos cosas: dice qué recalcular, y permite deshacer si el movimiento se rechaza. Medido:
un movimiento completo -proponer, evaluar, deshacer- cuesta 0,9 µs en `p01` (13 clientes por
depósito), 4 µs en `p22` (40) y 9 µs en `pr06` (60-90). El troceado es el 70 % de eso.

Un test de invariantes
([`NeighborhoodInvariantTest`](../../engines/annealing-engine/src/test/java/com/delivera/fms/engine/annealing/solution/NeighborhoodInvariantTest.java))
comprueba tras miles de movimientos y deshechos que cada cliente está en exactamente un depósito,
que la asignación cacheada coincide con las secuencias, que el coste mantenido por diferencias es
el recalculado desde cero y que deshacer devuelve exactamente al estado anterior.

## Vecindarios

Un movimiento elige un cliente al azar -lo que pondera los depósitos por su tamaño- y aplica:

**Intra-depósito** (reordenan una secuencia; el troceado decide dónde caen los cortes):

- **relocate** — mueve el cliente a otra posición.
- **swap** — lo intercambia con otro.
- **reverse** — invierte el tramo entre ambos.

**Inter-depósito** (los únicos que cambian el reparto, donde se juega la calidad en MD-CVRP):

- **relocate** — lo saca de su depósito y lo mete en otro, en su posición más barata.
- **swap** — intercambia el depósito de dos clientes. No altera cuántos clientes tiene cada
  depósito, así que sigue sirviendo cuando el reparto ya está ajustado a la flota.

Su proporción la fija `interDepotMoveProbability`. El depósito destino se elige entre los
**cercanos** al cliente: los que no están a más de `depotCandidateRatio` veces la distancia del más
cercano. Mover un cliente al otro extremo del mapa es un movimiento que ningún criterio de
aceptación va a admitir, y proponerlo solo gasta iteraciones. La lista se precalcula una vez porque
depende de la geometría, no de la asignación.

El vecindario es bueno por sí solo: una **bajada pura** con estos movimientos (aceptar solo mejoras)
lleva `p22` de la solución inicial a 5974 en 0,85 s, ya por debajo del genético. Lo que el recocido
añade es la capacidad de salir de ese valle.

## Temperatura

### Inicial: calibrada, no fijada

Se sondean `WARMUP_SAMPLES = 1000` movimientos desde la solución de partida (todos deshechos) y se
toma un **cuantil bajo** de los que empeoran, `calibrationQuantile` (5 % por defecto): un
empeoramiento *pequeño*, de los que la búsqueda necesita aceptar para salir de un óptimo local. La
temperatura inicial es la que acepta un empeoramiento así con `initialAcceptanceRate`:
`T0 = -Δ / ln(p0)`.

Un valor absoluto no sería transferible: el coste de un movimiento depende de la escala de las
coordenadas y del tamaño de la instancia, así que una temperatura buena para `p01` sería absurda
para `p22`.

**Un cuantil bajo y no la mediana, y esto cambia el resultado más que ningún otro parámetro.** Desde
un óptimo local casi todos los movimientos aleatorios empeoran mucho (reubicar un cliente en una
posición al azar es casi siempre terrible), así que la mediana es una escala enorme. A esa
temperatura la cadena se asienta en un coste de equilibrio muy por encima del punto de partida
-en `p22`, ~8600 con `T = 68` frente a un inicial de 6595- y, como la temperatura final se deriva
de la inicial, **nunca baja lo bastante para intensificar**. El resultado era que el mejor no
mejoraba nunca desde la solución inicial. Medido en `p22`: 12 % de *gap* con la mediana, 2,5 % con
el cuantil del 5 %.

Solo cuentan los empeoramientos de **distancia** (por debajo de media penalización de flota). Los
que arrastran una penalización son mil veces mayores que un movimiento normal y, en una instancia
cuya solución inicial no cabe en la flota (`p22` parte con 9-13 rutas por depósito para una flota
de 5), son además mayoría: contarlos disparaba la temperatura a ~1000 y el recocido aceptaba todo.

### Enfriamiento y recalentamiento

Geométrico: al terminar cada nivel, `T ← coolingRate × T`. Un nivel son
`movesPerTemperatureFactor × clientes` movimientos, proporcional al tamaño a propósito.

La temperatura mínima también se deriva: es la que acepta ese mismo empeoramiento pequeño con
`finalAcceptanceRate`. Con los valores por defecto, `T_min = 0,13 × T0` y un ciclo de enfriamiento
dura ~49 niveles. Al llegar ahí se **recalienta** a la mitad de la inicial y la búsqueda parte del
mejor conocido.

**No hay recalentamiento por estancamiento.** Se probó (recalentar tras 25 niveles sin mejorar el
mejor) y era contraproducente: saltaba *antes* de que un ciclo llegara a la temperatura mínima, así
que la búsqueda nunca se enfriaba y no intensificaba. Medido en `p01`: 36 % de aceptación global con
él, frente al 6 % esperable.

No hay tope de recalentamientos: el único criterio de parada es el tiempo (o `maxLevels`).

## Factibilidad

Aquí hay dos mecanismos y conviene no confundirlos.

### La penalización orienta, no decide

`RouteSplitter` **tolera** dos cosas cobrando 1000 de penalización: una ruta de un solo cliente que
no cabe en la duración, y más rutas que vehículos. Esa penalización es el gradiente que empuja la
búsqueda hacia lo factible, y el recocido lo sigue bien cuando el exceso de rutas es un problema de
**orden**: en `p22` pasa de 9-13 rutas por depósito a las 5 de la flota en el primer segundo,
porque cada reordenación que empaqueta mejor elimina una ruta y se acepta al instante.

Pero **no vale como criterio para aceptar un resultado**: con una penalización de 1000 sobre un coste
total de 6000, una solución infactible barata puede quedar por debajo de una factible cara y ganar la
comparación. Por eso el motor guarda aparte la **mejor solución factible vista** -verificada con
`RouteSplitter.isFeasible`, que materializa las rutas y comprueba capacidad, duración y flota- y es
esa la que devuelve. Solo si en toda la ejecución no apareció ninguna, repara la mejor que tenga
antes de rendirse.

La comprobación se hace una vez por nivel, no en cada mejora: el mejor histórico solo mejora, así que
mirarlo ahí recoge el mismo estado sin trocear todos los depósitos miles de veces.

### El reequilibrado repara lo que el recocido no puede

Hay instancias -`p07`, `p11`, `pr06`- en las que el exceso de rutas es de **asignación**: un depósito
tiene más clientes de los que su flota puede servir se ordenen como se ordenen, y salir de ahí exige
una **cadena** de reubicaciones a otros depósitos en la que solo la última elimina una ruta. El
criterio de Metropolis valora los movimientos de uno en uno, así que la probabilidad de recorrer la
cadena entera es el producto de las individuales: se queda atrapado salvo por casualidad.

Por eso se llama a `DepotRebalancer` -el mismo que usa el genético- sobre la solución de partida y
cada `rebalanceFrequency` niveles. Con una diferencia: el genético confirma con troceado **todas**
las posiciones de inserción del destino, que es exacto pero cuadrático (en `pr06` una pasada costaba
de 2 a 8 s, más que el presupuesto entero); el recocido confirma solo las **tres más baratas por
distancia** (`REPAIR_INSERTION_CANDIDATES`). Pierde la garantía de encontrar la que menos rutas
añade, pero el orden fino lo afina después el propio recocido, y `pr06` pasa de ser infactible a los
13 s a serlo al primer segundo.

Las fases auxiliares tienen plazo: la reparación inicial no puede consumir más de un cuarto del
presupuesto y el reequilibrado periódico se corta al agotarse, devolviendo lo hecho hasta entonces.

## Bucle principal

```
solución inicial: cliente al depósito más cercano + vecino más cercano aleatorizado
reequilibrar y pulir, hasta 5 pasadas o un cuarto del presupuesto
calibrar la temperatura inicial y la mínima

mientras quede tiempo (o niveles):

    nivel: para cada uno de factor × clientes movimientos
        proponer un vecino, guardando copia de los depósitos que toca
        recalcular solo esos depósitos
        aceptar si mejora, o con probabilidad exp(-Δ/T); si no, deshacer
        anotar el mejor

    T ← coolingRate × T
    si el mejor es factible y mejor que el mejor factible: anotarlo y añadir punto a la traza
    cada rebalanceFrequency niveles:   reequilibrar entre depósitos
    cada localSearchFrequency niveles: 2-opt y reubicación entre rutas
    si T < T_min: recalentar a T0/2 y volver al mejor conocido

pulido final del resultado (reequilibrado + búsqueda local); si nunca hubo factible, reparar
devolver la mejor solución FACTIBLE vista
```

## Parámetros

Configurables por petición, en el mapa `parameters`. Los valores por defecto son los de
[`AnnealingParameters.DEFAULTS`](../../engines/annealing-engine/src/main/java/com/delivera/fms/engine/annealing/service/AnnealingParameters.java)
y están declarados también en los metadatos del solver. **Si cambias uno, cámbialo en los dos
sitios**: el descriptor estaría anunciando una ejecución que no es la que ocurre.

| Parámetro | Defecto | Rango | Significado |
|---|---|---|---|
| `timeLimitMs` | 5000 | 100–300000 | Presupuesto de tiempo. Criterio de parada principal |
| `maxLevels` | 0 | 0–10⁶ | Tope de niveles; 0 = solo manda el tiempo. Lo que hace la ejecución reproducible |
| `calibrationQuantile` | 0,05 | 0,01–0,5 | Qué cuantil de los empeoramientos sondeados es el "empeoramiento pequeño" |
| `initialAcceptanceRate` | 0,4 | 0,01–0,99 | Con qué probabilidad se acepta ese empeoramiento al arrancar |
| `finalAcceptanceRate` | 0,001 | 10⁻⁶–0,5 | Por debajo de esa probabilidad el sistema está frío y se recalienta |
| `coolingRate` | 0,96 | 0,5–0,9999 | Factor de enfriamiento por nivel |
| `movesPerTemperatureFactor` | 12 | 1–1000 | Movimientos por nivel **y por cliente** |
| `interDepotMoveProbability` | 0,25 | 0–1 | Fracción de movimientos que cambian un cliente de depósito |
| `depotCandidateRatio` | 1,3 | 1–100 | Cuánto más lejos que el más cercano puede estar un depósito destino |
| `localSearchFrequency` | 8 | 0–10000 | Cada cuántos niveles se aplica 2-opt y relocate; 0 = solo al final |
| `rebalanceFrequency` | 20 | 0–10000 | Cada cuántos niveles se reequilibra entre depósitos; 0 = solo al inicio y al final |
| `seed` | - | 0 – 2⁴⁸−1 | Semilla. Sin ella el motor sortea una y la devuelve |

Constantes sin recorrido experimental medido, en `SimulatedAnnealing` y `AnnealingRouteSolver`:

| Constante | Valor | Significado |
|---|---|---|
| `WARMUP_SAMPLES` | 1000 | Movimientos de sondeo para calibrar la temperatura |
| `REHEAT_FACTOR` | 0,5 | Fracción de la temperatura inicial a la que se recalienta (0,5, 1 y 2 dan lo mismo dentro del ruido) |
| `CLOCK_CHECK_INTERVAL` | 512 | Cada cuántos movimientos se mira el reloj dentro de un nivel |
| `INITIAL_REPAIR_PASSES` / `_SHARE` | 5 / 4 | Pasadas de reparación inicial y fracción (1/4) del presupuesto que pueden gastar |
| `SEED_CANDIDATE_LIST` | 3 | Candidatos del vecino más cercano aleatorizado |
| `REPAIR_INSERTION_CANDIDATES` | 3 | Posiciones de inserción que confirma el reequilibrador |
| `FINAL_REPAIR_PASSES` | 5 | Pasadas de reparación del último recurso |

### `localSearchFrequency = 0` y `rebalanceFrequency = 0` son la ablación

Con ambos a cero el motor es **recocido puro** (las dos fases solo actúan al arrancar y al terminar).
Es lo que separa lo que aporta el criterio de Metropolis de lo que aporta la búsqueda local, que es
la comparación que hay que hacer antes de atribuirle el resultado a ninguno de los dos. Una primera
medida con una sola semilla: en `p22` el recocido puro da 1,9 % y el híbrido 3,3 %; en `p01` los dos
dan 2,4 %. Con la temperatura bien calibrada, la búsqueda local periódica aporta poco o nada en
esas dos instancias. **Hace falta repetir con varias semillas antes de concluirlo.**

## La curva anytime

La respuesta incluye un campo `trace`: los instantes en los que mejoró la mejor solución factible.

```json
"trace": [
  { "elapsedMs": 103,  "cost": 6595.14 },
  { "elapsedMs": 652,  "cost": 6364.21 },
  { "elapsedMs": 1817, "cost": 5795.25 },
  { "elapsedMs": 1818, "cost": 5786.42 }
]
```

Permite responder *qué coste habría dado el motor con un presupuesto menor* **sin volver a
ejecutarlo**, que es lo que hace falta para comparar motores por su frontera coste/tiempo. La
pasarela lo deja pasar sin transformarlo; los motores que no buscan de forma incremental lo devuelven
como `null`.

## Resultados

Con el presupuesto por defecto de 5 s y semilla `20260914`, contra el BKS. En la última columna, el
genético con esa misma semilla (de su test de regresión) donde se ha medido:

| Instancia | Coste | BKS | *Gap* | Factible | GENETIC |
|---|---:|---:|---:|---|---:|
| `p01` | 590,45 | 576,87 | 2,4 % | sí | 609,87 (5,7 %) |
| `p03` | 647,36 | 641,19 | **1,0 %** | sí | |
| `p07` | 904,42 | 885,80 | 2,1 % | sí | 941,56 (6,3 %) |
| `p11` | 3759,94 | 3554,18 | 5,8 % | sí | |
| `p15` | 2596,05 | 2505,42 | 3,6 % | sí | |
| `p22` | 5887,84 | 5702,16 | 3,3 % | sí | 6030,19 (5,8 %) |
| `pr06` | 3328,26 | 2676,30 | 24,4 % | sí | |

> Estas cifras son de **una ejecución por instancia** con una semilla fija. Para comparar
> configuraciones hay que repetir con varias semillas y mirar medias, o fijar `maxLevels` y comparar
> sin ruido de reloj.

**La dispersión entre semillas no es despreciable.** Tres semillas en `p22`: 1,8 %, 4,2 % y 2,3 %;
en `p11`: 6,6 %, 4,7 % y 7,4 %. En `p01`, una semilla alcanza el BKS exacto (576,87) y otras se
quedan en 585,00 o 590,45, tres óptimos locales muy estables. Una diferencia de dos puntos entre dos
configuraciones puede ser azar; de hecho, barrer `movesPerTemperatureFactor` (4, 6, 12) y
`coolingRate` (0,92, 0,96) no dio ninguna diferencia fuera del ruido.

## Limitaciones conocidas

- **No es reproducible con parada por tiempo.** Dos ejecuciones con la misma semilla hacen distinto
  número de movimientos según la carga de la máquina. Con `maxLevels` fijo sí lo es, y es lo que usa
  [`AnnealingRegressionTest`](../../engines/annealing-engine/src/test/java/com/delivera/fms/engine/annealing/benchmark/AnnealingRegressionTest.java).
- **Puede pasarse del presupuesto** unas decenas de milisegundos: las fases auxiliares tienen
  plazo, pero el pulido final no. Si en toda la ejecución no apareció ninguna solución factible, la
  reparación final puede tardar más; en el banco de 33 instancias no ha ocurrido.
- **`pr06` y las instancias con tiempos de servicio quedan lejos.** Con flota saturada (6/6 en los
  cuatro depósitos) casi cualquier cambio estructural añade una ruta. Con 20 s el *gap* baja del
  24 % al 11,5 %, así que ahí falta presupuesto, no algoritmo.
- **Es secuencial**: no aprovecha más de un núcleo. Un recocido admite ejecuciones paralelas
  independientes con semillas distintas quedándose con la mejor, que es la vía más directa si hiciera
  falta.
- **No soporta ventanas de tiempo.**
- **Flota heterogénea**: hereda del núcleo la capacidad del mayor vehículo de cada depósito.

## Cómo calibrar

[`AnnealingCalibrationTest`](../../engines/annealing-engine/src/test/java/com/delivera/fms/engine/annealing/benchmark/AnnealingCalibrationTest.java)
ejecuta el motor en local sobre instancias Cordeau e imprime coste, *gap*, factibilidad y traza. No
afirma nada; solo corre a petición. Cualquier parámetro se puede pasar como propiedad:

```bash
mvn test -Dtest=AnnealingCalibrationTest -Dcalibration=true -Dinstances=p01,p22 -Dseed=1 -DcalibrationQuantile=0.1
```

Con `-Dannealing.log=TRACE` imprime por nivel la temperatura, el coste actual y el mejor: es lo que
permite ver si la cadena se asienta por debajo del mejor al enfriar, que es la comprobación que
destapó el problema de la mediana.
