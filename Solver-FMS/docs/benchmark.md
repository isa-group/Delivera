# Benchmark

## Las instancias

`instances-MD-CVRP-JSON/` contiene las 33 instancias MDVRP de **Cordeau**, el banco de pruebas
estándar para este problema: `p01`–`p23` y `pr01`–`pr10`. Van de 50 a 360 clientes y de 2 a 9
depósitos.

Para cada una se conoce la **mejor solución publicada** (BKS, *best known solution*), lo que permite
medir el *gap*: cuánto peor es nuestra solución que la mejor conocida.

### Instancias que son la misma con distinta restricción

Esto es fácil de pasar por alto y da lugar a conclusiones erróneas. Varios grupos comparten clientes,
depósitos, capacidad y flota, y **solo se diferencian en `max_duration`**:

| Grupo | `max_duration` | BKS |
|---|---|---|
| p12 / p13 / p14 | 0 / 200 / 180 | 1318,95 / 1318,95 / 1360,12 |
| p15 / p16 / p17 | 0 / 200 / 180 | 2505,42 / 2572,23 / 2709,09 |
| p18 / p19 / p20 | 0 / 200 / 180 | 3702,85 / 3827,06 / 4058,07 |
| p21 / p22 / p23 | 0 / 200 / 180 | 5474,84 / 5702,16 / 6095,46 |

A más apretada la duración, mayor el BKS: la restricción encarece la solución óptima. **22 de las 33
instancias tienen límite de duración**, y las 10 `prXX` tienen además tiempos de servicio distintos
de cero, que consumen duración pero no coste.

Es la mejor prueba de que un motor respeta la restricción: si la ignora, resuelve los tres casos igual
y devuelve el mismo coste. Si la respeta, el coste sube conforme la duración aprieta, igual que sube el
BKS. Sobre p21/p22/p23 el voraz da 8977 / 9517 / 10665.

## La regla de oro

> **Un coste por debajo del BKS no es un récord: es la señal de que se está violando alguna
> restricción de la instancia.**

Así se detectó que `max_duration` no llegaba al motor: una consulta desde Postman devolvió 5935 en
p23, cuyo BKS es 6095,46. Ver [decisiones-y-correcciones.md](decisiones-y-correcciones.md).

Por eso el test de benchmark **valida factibilidad además de coste**: una mejora de coste no puede
venir de saltarse una restricción sin que el test lo cace.

## Cómo ejecutarlo

`SolverBenchmarkTest`, en `/fms-gateway`, ejerce **todos** los solvers registrados. Es un test de
integración: necesita el sistema levantado.

```bash
docker compose up -d
```

```bash
mvn test -Dtest=SolverBenchmarkTest -Dbenchmark=true -Dinstances=p01,p22 -Druns=3
```

Desde `/fms-gateway`. En la terminal hay que entrecomillar cada argumento: `"-Dbenchmark=true"`.

| Parámetro | Por defecto | Significado |
|---|---|---|
| `-Dbenchmark=true` | - | **Obligatorio.** Sin él el test se salta, para no ralentizar el build |
| `-Dinstances=` | `p01` | Instancias separadas por coma, o `all` para las 33 |
| `-Dsolvers=` | todos | Subconjunto, por ejemplo `GREEDY,GENETIC` |
| `-Druns=` | `1` | Repeticiones **por solver no determinista**. Los deterministas se ejecutan una vez |
| `-Dgateway=` | `http://localhost:8090` | Gateway contra el que medir |
| `-Dtimeout=` | `600` | Segundos por petición |

Salida:

```
Solvers registrados: [RANDOM, GREEDY, GENETIC]

p22  9 depositos, 360 clientes, duracion maxima 200  |  BKS 5702,16
  solver     n      mejor      media     gap  rutas   tiempo  factible     semilla
  RANDOM     2   20326,68   20918,49 +256,5%    131    189ms  si         127997977
  GREEDY     1    9517,33    9517,33  +66,9%     63    214ms  si                 -
  GENETIC    2    5946,53    5953,76   +4,3%     36     2,3s  si        1012033380
```

### La columna `semilla`

Es la semilla de la **mejor** de las repeticiones, la que hay que reenviar en `parameters` para
volver a obtener exactamente esa solución. Aparece `-` en los solvers deterministas, que no dependen
del azar. Es lo que hace útil subir `-Druns`: una buena vuelta ya no se pierde, se puede repetir y
usar de punto de partida para afinar parámetros.

### Los solvers no están escritos en el test

Se leen de `GET /api/v1/fms/solvers`, que se deriva de la configuración de motores. Registrar un
motor nuevo basta para que entre en el benchmark, aunque esté implementado en otra tecnología: lo
único que se le exige es el contrato HTTP. El descriptor aporta también el campo `deterministic`,
que es lo que decide si repetir el solver varias veces o una sola.

## Qué valida

`CordeauInstance.violations` comprueba, en cada ejecución y para cada solver:

1. Cada cliente aparece en **exactamente una** ruta, y ninguna ruta visita clientes inexistentes.
2. `totalLoad` de cada ruta coincide con la suma de las demandas de sus paradas.
3. Ninguna ruta excede la **capacidad** del vehículo.
4. Ninguna ruta excede la **duración máxima** del depósito, contando distancia + tiempos de servicio.
5. Ningún depósito usa **más vehículos distintos** de los que tiene.

Devuelve **todas** las violaciones, no solo la primera: así se ve de un vistazo si a un motor se le
escapa una restricción concreta o si la solución está rota de arriba abajo.

El coste se **recalcula desde las paradas**, no se toma el `totalCost` que informa el motor. Un motor
que se equivoque al sumar no puede quedar impune por haberlo calculado él mismo.

> **La comprobación 5 cuenta vehículos distintos, no rutas.** Un vehículo puede hacer más de un
> viaje: lo que no puede es no existir. Es una comprobación laxa a propósito, y el precio es que se
> pasa con facilidad -un vehículo que hace 26 viajes la pasa-, así que **no dice nada sobre cuántos
> viajes hace la flota**. Eso se ve comparando `routes` con `vehicles_used` en el CSV de
> `compare_solvers.py`: en `p22` el aleatorio hace 139 rutas con 9 vehículos y el genético 36 con 36.

El coste **no** hace fallar el test. Comparar calidad es cosa de `compare_solvers.py`; aquí lo que se
comprueba es que lo que devuelve cada motor sea una solución válida del problema.

## Resultados actuales del motor genético

Una ejecución por instancia. Todas factibles.

| Instancia | Coste | BKS | Gap | Tiempo |
|---|---:|---:|---:|---:|
| p01 | 609,24 | 576,87 | 5,61 % | 0,6 s |
| p02 | 496,55 | 473,53 | 4,86 % | 0,8 s |
| p03 | 676,10 | 641,19 | 5,44 % | 0,9 s |
| p04 | 1075,46 | 1001,59 | 7,37 % | 1,6 s |
| p05 | 778,70 | 750,03 | 3,82 % | 1,4 s |
| p06 | 942,79 | 876,50 | 7,56 % | 1,2 s |
| p07 | 947,17 | 885,80 | 6,93 % | 1,2 s |
| p08 | 4691,85 | 4437,68 | 5,73 % | 3,8 s |
| p09 | 4108,53 | 3900,22 | 5,34 % | 2,7 s |
| p10 | 3952,55 | 3663,02 | 7,90 % | 2,9 s |
| p11 | 3818,61 | 3554,18 | 7,44 % | 4,3 s |
| p12 | 1341,84 | 1318,95 | 1,74 % | 1,0 s |
| p13 | 1341,84 | 1318,95 | 1,74 % | 1,1 s |
| p14 | 1365,69 | 1360,12 | **0,41 %** | 1,2 s |
| p15 | 2664,85 | 2505,42 | 6,36 % | 1,5 s |
| p16 | 2664,85 | 2572,23 | 3,60 % | 1,6 s |
| p17 | 2731,37 | 2709,09 | **0,82 %** | 1,6 s |
| p18 | 3957,85 | 3702,85 | 6,89 % | 2,2 s |
| p19 | 3988,79 | 3827,06 | 4,23 % | 2,3 s |
| p20 | 4097,06 | 4058,07 | **0,96 %** | 2,1 s |
| p21 | 6010,11 | 5474,84 | 9,78 % | 3,3 s |
| p22 | 5961,68 | 5702,16 | 4,55 % | 3,2 s |
| p23 | 6145,58 | 6095,46 | **0,82 %** | 3,1 s |
| pr01 | 934,50 | 861,32 | 8,50 % | 0,7 s |
| pr02 | 1362,70 | 1307,34 | 4,23 % | 1,3 s |
| pr03 | 1900,19 | 1803,80 | 5,34 % | 1,7 s |
| pr04 | 2216,75 | 2058,31 | 7,70 % | 3,1 s |
| pr05 | 2660,08 | 2331,20 | 14,11 % | 6,4 s |
| pr06 | 3100,59 | 2676,30 | 15,85 % | 10,2 s |
| pr07 | 1183,73 | 1089,56 | 8,64 % | 1,0 s |
| pr08 | 1816,64 | 1664,85 | 9,12 % | 1,9 s |
| pr09 | 2240,81 | 2153,10 | 4,07 % | 2,4 s |
| pr10 | 3288,99 | 2921,85 | 12,57 % | 11,8 s |

Mediana del gap ~6 %. Las instancias con la duración más apretada (p14, p17, p20, p23) son las de
mejor gap, porque la restricción reduce el espacio de soluciones. Las peores son `pr05`, `pr06` y
`pr10`, las más grandes con tiempos de servicio.

Como referencia de cuánto se ha avanzado: antes de las correcciones descritas en
[decisiones-y-correcciones.md](decisiones-y-correcciones.md), p22 daba 6737,95 **y era infactible**
(rutas con carga por encima de la capacidad del vehículo).

> **Verifica la tabla de BKS.** Los valores están en `Solver-FMS/best-known.json`, que leen tanto el
> test como `compare_solvers.py`. Son los publicados habitualmente para el conjunto Cordeau, pero solo se han
> contrastado explícitamente p01, p22 y p23. Si alguno estuviera mal, el gap que imprime el test
> estaría mal también. El coste y la validación de factibilidad no dependen de esta tabla.

## Comparar todos los solvers

El test anterior mide **solo el genético**. Para enfrentar a todos los registrados sobre las mismas
instancias hay un script que ataca la API, valida lo que devuelve cada uno y deja el experimento por
escrito:

```bash
python compare_solvers.py
```

Desde `Solver-FMS/`, con el sistema levantado. Sin dependencias: solo la librería estándar. Sin
argumentos lanza **las 33 instancias** con todos los solvers del catálogo.

| Parámetro | Por defecto | Significado |
|---|---|---|
| `--url` | `http://localhost:8090` | Gateway contra el que medir |
| `--instances` | `all` | `all`, o instancias separadas por coma (`p01,p22`) |
| `--solvers` | todos | Subconjunto, por ejemplo `GREEDY,GENETIC` |
| `--runs` | 3 | Repeticiones **por solver no determinista**. Con menos de 5 la desviación no es fiable |
| `--seed` | - | Semilla base. La repetición *k* usa `seed+k-1`, y el experimento entero se repite tal cual |
| `--out` | `results/` | Directorio donde deja el informe y los datos |
| `--label` | - | Etiqueta del experimento. Va al nombre de los ficheros y a una columna del CSV |
| `--timeout` | `600` | Segundos por petición |

### Dónde está cada cosa

`compare_solvers.py` es solo la línea de comandos y el cableado. El trabajo está en el paquete
`experimentation/`, dividido en dos según para qué sirve:

| Paquete | Para qué | Dependencias |
|---|---|---|
| `comparison/` | **Medir**: lanzar los solvers y dejar el experimento por escrito. Lo usa `compare_solvers.py` | Solo librería estándar |
| `tree/` | **Analizar**: aprender de esas mediciones qué solver conviene. Lo usa `decision_tree.py` | scikit-learn, matplotlib |

La división no es cosmética: mezclarlos obligaría a instalar scikit-learn para poder medir, o a
renunciar a él para poder analizar.

| `comparison/` | Qué sabe |
|---|---|
| `instance.py` | Qué es una instancia Cordeau: sus características, cuánto cuesta una solución y qué restricciones incumple |
| `gateway.py` | Hablar con la pasarela: catálogo y resolución |
| `runner.py` | Lanzar, medir y convertir cada ejecución en una fila |
| `dataset.py` | El esquema del CSV y la agregación de repeticiones |
| `report.py` | El informe en Markdown |

| `tree/` | Qué sabe |
|---|---|
| `dataset.py` | Qué significa «conviene»: coste, y a igualdad de coste, tiempo |
| `model.py` | Entrenar, validar contra la regla mayoritaria y verificar el algoritmo |
| `plot.py` | El árbol como imagen |
| `report.py` | El informe en Markdown |

Cómo se ejecuta e interpreta el árbol está en [decision-tree.md](decision-tree.md).

La separación tiene un destinatario concreto: **el análisis posterior de los resultados necesita
`instance.py` y nada más**. Leer las características de las 33 instancias no debería exigir que haya
un gateway levantado ni arrastrar el generador de informes.

```python
from experimentation.comparison.instance import Instance, all_names

filas = [Instance.load(nombre).features() for nombre in all_names()]
```

### Las dos salidas

Cada ejecución deja dos ficheros bajo `--out`, con la fecha y la etiqueta en el nombre:

```
datos-2026-08-13-1316.csv
informe-2026-08-13-1316.md
datos-2026-08-13-1332-semilla-fija.csv
informe-2026-08-13-1332-semilla-fija.md
```

- **`datos-<fecha>.csv`** - una fila por ejecución individual, en columnas fijas y en inglés. Es el
  dato crudo. Que el esquema no cambie entre experimentos es lo que permite concatenar los CSV de
  varias sesiones y analizarlos juntos: se distinguen por `run_id` y `label`, no por tener columnas
  distintas.
- **`informe-<fecha>.md`** - el informe legible: fecha de ejecución, commit, configuración,
  descriptores de los solvers con sus parámetros, resumen global, comparativa por instancia, **una
  tabla por solver** con las 33 filas, incidencias y notas metodológicas.

La fecha llega al minuto, no al segundo, porque el nombre se lee y se cita. Dos experimentos dentro
del mismo minuto -dos pruebas rápidas sobre una instancia- desempatan con un sufijo
(`...-1316-2.csv`) en vez de pisarse, y ese mismo sufijo va en la columna `run_id`, de modo que el
nombre del fichero y el identificador de sus filas siempre coinciden.

La división es deliberada: el CSV es para la máquina y no debe cambiar de forma; el informe es para
leerlo y citarlo. El CSV se va escribiendo y vaciando a disco según avanza, y una interrupción con
Ctrl-C genera igualmente el informe con lo medido hasta ese momento: un barrido completo son varios
minutos y lo ya medido no se tira.

### Las columnas del CSV

Tres bloques. **Identificación**: `filename` (el id de la instancia), `solver`, `repetition`,
`run_id`, `label`, `timestamp`, `solver_version`, `strategy`, `deterministic`, `seed` y `params` -
los parámetros efectivos con los que corrió, que son los valores por defecto del descriptor más lo
que se le enviara.

**Características de la instancia**: `num_customers`, `num_depots`, `vehicles_per_depot`,
`vehicle_capacity`, `max_duration`, `total_demand`, `load_ratio`, `avg_service_duration`,
`customers_per_depot`, `area`, `customer_density`, `mean_nn_distance`, `mean_depot_distance` y `bks`.
Van repetidas en cada fila, y no en un fichero aparte, para que el CSV sea autocontenido: describen
el problema, no la solución, y son las columnas que hacen falta para relacionar el tipo de instancia
con el algoritmo que le conviene.

**Resultado**: `status`, `cost`, `reported_cost`, `gap_pct`, `routes`, `vehicles_used`, `feasible`,
`violations`, `violations_detail`, `elapsed_ms`, `engine_ms` y `error`.

### Salida en la terminal

```
[22/33] p22  9 depositos, 360 clientes, duracion maxima 200  |  BKS 5702.16
  solver     n      mejor      media    desv     gap  rutas   tiempo  factible         semilla
  RANDOM     3   20656.41   20826.14   221.0 +262.3%    130     81ms       0/3      1668748295
  GREEDY     1    9517.33    9517.33       -  +66.9%     63     77ms       0/1               -
  GENETIC    3    5952.10    5960.98     7.7   +4.4%     36     2.6s       3/3      1012033380
```

### La columna `desv`

Salvo el voraz, ningún motor repite resultado si no se le fija la semilla, y por defecto el script no
se la fija: mide la variabilidad real del algoritmo, que es lo que interesa comparar. Una ejecución
suelta, por tanto, no dice nada. Si dos configuraciones se separan menos que esta desviación, la
diferencia es ruido y no mejora. Aparece `-` cuando solo ha habido una ejecución: sin repeticiones no
hay dispersión que medir.

**Con pocas repeticiones engaña.** El genético converge a la misma solución a menudo, y en instancias
pequeñas es fácil que dos vueltas den el mismo número. En `p01`, cinco vueltas dan tres valores
distintos; dos vueltas pueden dar cero dispersión aparente.

El número de repeticiones lo decide el descriptor del solver: si declara `deterministic: true`, se
ejecuta una sola vez porque repetirlo solo gasta tiempo.

Con `--seed` el experimento pasa a ser reproducible: la repetición *k* usa `seed+k-1`, de modo que las
repeticiones siguen siendo distintas entre sí pero el barrido completo se puede volver a lanzar y dar
lo mismo. Es lo que conviene para comparar dos configuraciones del genético sin que el azar entre en
la comparación.

### El coste que se compara, y la factibilidad

El coste es el **recalculado desde las paradas** de cada ruta, no el que informa el motor. El CSV trae
los dos, `cost` y `reported_cost`, así que si alguna vez dejaran de coincidir se vería.

El script **valida además las restricciones de la instancia** en cada ejecución, con las mismas
comprobaciones que `CordeauInstance` en el test del gateway: cliente servido exactamente una vez,
carga informada, capacidad, duración máxima y vehículos por depósito. Son dos implementaciones de las
mismas reglas -una en Java y otra en Python- que hay que mantener a la vez, a cambio de que el script
no dependa de Maven.

Por eso el **mejor coste de cada solver y las victorias del informe se calculan solo sobre
ejecuciones factibles**. Sin esa regla la comparación se invierte: el voraz ignora la duración máxima
y el número de vehículos, así que en las 22 instancias con límite puede ganar en distancia
precisamente por saltarse la restricción.

## Benchmark a través de la API

Para medir contra el sistema desplegado en lugar de contra la clase directamente:

```bash
curl -X POST "http://localhost:8090/api/v1/fms/instances/send?fileName=p22&solverType=GENETIC"
```

Este camino sí ejerce el mapeo real del gateway y la comunicación entre contenedores, pero **no
valida factibilidad**: la comprobación hay que hacerla aparte. Es el camino por el que se detectó la
anomalía de p23, precisamente porque el coste salió por debajo del BKS.
