# Decisiones y correcciones del motor genético

Registro de los problemas encontrados en la revisión del motor genético y de por qué se resolvieron
así. Sirve para no volver a introducir los mismos fallos y para entender por qué el código tiene la
forma que tiene.

**Punto de partida:** p22 daba 6737,95 con un BKS de 5702,16 (gap 17,9 %) y la solución **era
infactible**: la ruta `V4-2` llevaba carga 72 con vehículos de capacidad 60.

**Estado actual:** ~5960 en p22 (gap 4,5 %) y las 33 instancias factibles.

---

## 1. El troceado era voraz, no óptimo

**Qué pasaba.** La función objetivo recorría la gira gigante y cortaba en cuanto se llenaba el
vehículo. Ese corte es fácil de mejorar: para un mismo orden de clientes existe un troceado más
barato.

**Por qué importa.** Medido sobre p22: mismo orden, corte voraz 6688 frente a corte óptimo 6565.

**Cómo se resolvió.** `RouteSplitter` con la programación dinámica de Prins. Es ahora el **único**
punto donde un cromosoma se convierte en rutas, y lo usan tanto la función objetivo como el
decodificador de la respuesta, de modo que no pueden divergir.

## 2. La búsqueda local optimizaba una gira, no rutas

**Qué pasaba.** Se aplicaba 2-opt a la gira gigante de cada depósito, como si fuera un TSP, y el
troceado por capacidad venía después.

**Por qué importa.** Acortar la gira **no** equivale a abaratar las rutas: un 2-opt puede reducir la
gira y aumentar el coste real una vez cortada. Medido: pasar a búsqueda local a nivel de ruta llevó
de 6565 a 6378.

**Cómo se resolvió.** `LocalSearch` trocea primero en rutas reales y luego aplica 2-opt intra-ruta y
*relocate* entre rutas del mismo depósito, evaluando cada movimiento contra el coste real.

## 3. Costes de inserción ciegos a los límites de ruta y depósito

**Qué pasaba.** Los operadores calculaban `d(prev,c) + d(c,next) − d(prev,next)` usando los vecinos
en la **permutación global**, sin comprobar que pertenecieran al mismo depósito.

**Por qué importa.** Se valoraban aristas entre clientes de depósitos distintos, que no existen en
ninguna ruta. El greedy colocaba clientes en posiciones que *parecían* baratas y no lo eran.

**Cómo se resolvió.** `RouteSplitter.insertionCost` / `removalGain` / `bestPosition`, que miden
dentro de la secuencia del depósito con el depósito como extremo. Todos los operadores las usan.

## 4. 2-opt con aristas caducadas

**Qué pasaba.** El coste de la arista inicial se calculaba fuera del bucle interno. Al aceptar una
inversión, el segmento cambiaba pero la variable cacheada no se recalculaba, así que a partir de la
primera mejora **los deltas eran erróneos y se aceptaban movimientos que empeoraban**.

**Cómo se resolvió.** Tras aceptar una inversión se reinicia el barrido, recalculando las aristas.
Las rutas son cortas (~10 clientes), así que el coste extra es irrelevante.

## 5. El mejor histórico se degradaba solo

**Qué pasaba.** `bestSolution` guardaba una **referencia** a un individuo de la población. La búsqueda
local mutaba después ese mismo objeto *in situ*. Si lo empeoraba, `bestFitness` conservaba el valor
bueno antiguo mientras el objeto apuntado era peor, y al final se decodificaba la versión degradada.

**Cómo se resolvió.** `bestSolution` es siempre una copia independiente.

## 6. El elitismo inyectaba clones

**Qué pasaba.** Se sustituían los 5 peores hijos por 5 copias **del mismo** individuo.

**Por qué importa.** Con un torneo de tamaño 3, inyectar 5 clones del mismo genotipo cada generación
provoca convergencia prematura. Se notaba: la ejecución se estancaba hacia la generación 120.

**Cómo se resolvió.** Se copian los `k` mejores **distintos**.

## 7. El 20 % "heurístico" de la población eran 30 individuos idénticos

**Qué pasaba.** La siembra heurística era un vecino más cercano determinista, partiendo de la misma
asignación cliente-depósito para todos. Recibía un generador aleatorio y **no lo usaba**. Los 30
individuos eran idénticos, y en cada reinicio se generaban 29 más iguales.

**Cómo se resolvió.** Vecino más cercano aleatorizado: en cada paso se elige al azar entre los 3
clientes más próximos (`SEED_CANDIDATE_LIST`).

## 8. El cruce extraía un depósito entero, no una ruta

**Qué pasaba.** El BCRC extraía **todos** los clientes de un depósito (~40) en lugar de una ruta
(~10), y los reinsertaba con un greedy.

**Por qué importa.** Así el cruce no hereda estructura de ningún padre: degenera en una
reconstrucción voraz. Y costaba unas 14 400 evaluaciones de inserción por hijo.

**Cómo se resolvió.** Se trocea la secuencia del depósito donante y se extrae **una** de sus rutas.

## 9. El cruce corrompía la mitad de los hijos

**Qué pasaba.** El hijo se construía copiando al padre 2, con lo que heredaba su asignación
cliente-depósito, que es la coherente con la permutación resultante. Acto seguido el bucle principal
se la **sustituía por la del padre 1**.

**Por qué importa.** La asignación dejaba de corresponderse con el orden recién construido. La mitad
de los hijos de cada generación nacía corrupta.

**Cómo se resolvió.** Se eliminó esa sustitución.

## 10. La mutación intra-depósito se desactivaba sola

**Qué pasaba.** Exigía que el depósito tuviera al menos 2 **bloques contiguos** en la permutación. Un
depósito cuyos clientes estaban agrupados —que es el estado deseable, y al que tienden el cruce y la
búsqueda local— **nunca mutaba**.

**Cómo se resolvió.** Opera sobre la secuencia del depósito, con la condición sobre el número de
clientes, no de bloques.

## 11. `phase2Improve` producía rutas infactibles

**Qué pasaba.** El último paso antes de devolver la respuesta movía el último cliente de una ruta al
principio de la siguiente si bajaba la distancia, **sin comprobar la capacidad**.

**Por qué importa.** Era el origen directo de la ruta `V4-2` con carga 72 sobre capacidad 60. Además,
al ejecutarse solo en la decodificación, el fitness que guiaba la búsqueda y el coste que se reportaba
eran funciones distintas.

**Cómo se resolvió.** Se eliminó. El decodificador usa ahora el mismo troceado óptimo que la función
objetivo.

## 12. La asignación al depósito más cercano es infactible en algunas instancias

**Qué pasaba.** No es un bug del código anterior sino una propiedad de las instancias, que salió al
empezar a validar factibilidad. En `p07` la asignación al depósito más próximo deja 412 unidades de
carga en un depósito con 4 vehículos de capacidad 100; en `p11`, 3041 con 6 de 500.

**Por qué es difícil.** No existe **ningún movimiento individual** que mejore el coste penalizado:
sacar un cliente encarece la distancia y, si no llega a eliminar una ruta, no compensa. Un criterio
de "aceptar solo si mejora" se queda atrapado indefinidamente.

**Cómo se resolvió.** `InterDepotLocalSearch` separa **reparar** de **mejorar**. La reparación saca
carga del depósito sobrecargado **sin condición de coste**, eligiendo el movimiento más barato de
entre los que dejan al destino dentro de su flota.

## 13. El número de rutas depende de dónde se corte, no solo de la carga

**Qué pasaba.** La reparación seguía fallando aun probando todos los candidatos. El motivo: solo se
probaba **una** posición de inserción en el destino, la más barata en distancia, y esa posición puede
ser justo la que parte la secuencia de forma que el destino necesite una ruta más.

Un depósito con carga por debajo de su capacidad total puede necesitar rutas de más si la secuencia
no se puede cortar bien: los cortes tienen que ser contiguos.

**Cómo se resolvió.** Al reparar se recorren **todas** las posiciones de inserción del destino,
midiendo el troceado real de cada una.

## 14. El límite de flota tenía que estar dentro del troceado

**Qué pasaba.** Con la duración ya activada, `p08` necesitaba más rutas y un depósito se pasaba: 15
rutas con 14 vehículos, teniendo 27 de 28 vehículos usados en total. La reparación funcionaba, pero
**cualquier re-troceado posterior la deshacía**.

**Por qué.** Con la penalización aplicada *después* del corte, el troceado no puede cambiar un poco
de distancia por una ruta menos: minimiza distancia y punto. Cada evaluación y cada pasada de
búsqueda local volvía a partir la secuencia y volvía a producir 15 rutas.

**Cómo se resolvió.** Si el troceado de coste mínimo excede la flota, se relanza un **DP en dos
dimensiones** `cost[rutas][posición]` acotado al número de vehículos. Es la lección más general de
toda la revisión: **una restricción que la penalización no puede hacer cumplir tiene que estar dentro
del modelo que decide**, no fuera.

Los tramos de ruta se precalculan una vez y los comparten ambos DP, porque el acotado los recorre una
vez por cada número de vehículos. Eso bajó `pr06` de 11,8 s a ~7 s y `pr10` de 12,8 s a ~6 s.

## 15. `max_duration` no llegaba al motor

**Cómo se detectó.** Una consulta desde Postman devolvió **5935** en `p23`, cuyo BKS es **6095,46**.
Estar por debajo del BKS es imposible sin violar algo.

**Qué pasaba.** El parser leía `max_duration` y lo guardaba en `DepotConfig`, pero el mapeador no lo
propagaba y ningún DTO tenía el campo. Como p21, p22 y p23 son la misma instancia con duración 0, 200
y 180, el motor resolvía p21 en los tres casos. Lo mismo con p12–p14, p15–p17 y p18–p20: **22 de las
33 instancias** estaban afectadas.

**Cómo se resolvió.** `DepotDto.maxDuration`, opcional, propagado desde el gateway y aplicado en el
DP de troceado. Los motores greedy y random no se tocaron: Spring ignora las propiedades desconocidas
por defecto.

## 16. Los tiempos de servicio también faltaban

**Qué pasaba.** Al arreglar lo anterior se vio que las 10 instancias `prXX` tienen
`service_duration` distinto de cero. El límite de duración los incluye, así que seguían mal medidas.

**El matiz que importa.** El tiempo de servicio consume **duración de ruta** pero **no** suma al
**coste**. Si se acumulara en el coste, el objetivo dejaría de ser comparable con el BKS.

**Cómo se resolvió.** `CustomerDto.serviceDuration`, opcional, y en `RouteSplitter` la distancia y la
duración se acumulan por separado.

---

## Cambios menores

- Comparación de índices con `HashSet` en lugar de `contains` sobre lista, que era O(n²) por hijo.
- Se evita que el torneo devuelva dos veces el mismo padre.
- `restartPopulation` conserva de verdad los `keepCount` mejores; antes recibía el parámetro y solo
  conservaba uno.
- Eliminado código muerto: contador de rutas sin usar, variables sin leer, condicionales que no
  hacían nada, ruta de respaldo inalcanzable que además no comprobaba capacidad.
- `buildDepotOrder` estaba duplicado en cuatro clases; ahora está en `PermutationCodec`.

## Ideas probadas y descartadas

- **Intercambio (*swap*) de clientes entre rutas del mismo depósito.** Implementado y medido: 5958 de
  media frente a 5955 sin él. No aportaba, se revirtió para no engordar el código.
- **Subir `MAX_EVALUATIONS`, `LOCAL_SEARCH_FREQUENCY` y `MAX_RESTARTS`.** El coste llega a una meseta
  en torno a 5950 en p22 y no baja con más presupuesto.

## Pendiente

- **Ventanas de tiempo**: el parser las lee pero no se propagan ni se soportan.
- **Reproducibilidad**: `JMetalRandom` es un singleton global sin semilla configurable.
- **Parámetros externalizables** a `application.yml` con `@ConfigurationProperties`.
- **Paralelización**: el motor es secuencial. La evaluación de la descendencia es paralelizable, pero
  habría que usar `ThreadLocalRandom`: la contención sobre el generador compartido de jMetal lo
  penalizaría.
- **Gestión de diversidad** (*path relinking*, vecindarios granulares) para bajar de la meseta.
