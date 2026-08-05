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

```bash
mvn test -Dtest=CordeauBenchmarkTest -Dbenchmark=true -Dinstance=p22 -Druns=3
```

Desde `engines/genetic-engine`. En PowerShell hay que entrecomillar cada argumento: `"-Dbenchmark=true"`.

| Parámetro | Por defecto | Significado |
|---|---|---|
| `-Dbenchmark=true` | — | **Obligatorio.** Sin él el test se salta, para no ralentizar el build |
| `-Dinstance=` | `p22` | Nombre de la instancia, sin extensión |
| `-Druns=` | `1` | Repeticiones. Útil porque el algoritmo no es determinista |

El test no levanta Docker: instancia `GeneticRouteSolver` directamente y replica el mapeo del
gateway (`StandardInstanceMapper` + `DistanceMatrixCalculator`).

Salida:

```
  run 1: cost=5952,10  routes=36  time=2249ms
[p22] runs=5  best=5935,41  avg=5956,11  avgTime=2104ms  BKS=5702,16  gapBest=4,09%  gapAvg=4,45%
```

## Qué valida

`assertFeasible` comprueba, en cada ejecución:

1. Cada cliente aparece en **exactamente una** ruta.
2. `totalLoad` de cada ruta coincide con la suma de las demandas de sus paradas.
3. Ninguna ruta excede la **capacidad** del vehículo.
4. Ninguna ruta excede la **duración máxima** del depósito, contando distancia + tiempos de servicio.
5. Ningún depósito usa **más rutas que vehículos** tiene.

> El test está escrito para el motor genético. La comprobación 5 cuenta rutas por depósito, lo que
> presupone una ruta por vehículo. Greedy y random modelan multi-viaje —varias rutas comparten
> `vehicleId`— así que esa comprobación no les aplica tal cual.

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

> **Verifica la tabla de BKS.** Los valores están en la constante `BEST_KNOWN` de
> `CordeauBenchmarkTest`. Son los publicados habitualmente para el conjunto Cordeau, pero solo se han
> contrastado explícitamente p01, p22 y p23. Si alguno estuviera mal, el gap que imprime el test
> estaría mal también. El coste y la validación de factibilidad no dependen de esta tabla.

## Comparar los tres solvers

El test anterior mide **solo el genético**. Para enfrentar los tres sobre las mismas instancias hay
un script que ataca la API y valida lo que devuelve cada uno:

```bash
python compare_solvers.py --instances p01,p22 --runs 3
```

Desde `Solver-FMS/`, con el sistema levantado. Sin dependencias: solo la librería estándar.

| Parámetro | Por defecto | Significado |
|---|---|---|
| `--url` | `http://localhost:8090` | Gateway contra el que medir |
| `--instances` | `p01` | Instancias separadas por coma |
| `--all` | — | Las 33 del banco |
| `--solvers` | todos | Subconjunto, por ejemplo `GREEDY,GENETIC` |
| `--runs` | 3 | Repeticiones **por solver no determinista**. Con menos de 5 la desviación no es fiable |
| `--csv` | — | Vuelca cada ejecución para analizarla aparte |

Salida:

```
p22  9 depositos, 360 clientes, duracion maxima 200  |  BKS 5702.16
  solver     n      mejor      media    desv     gap  rutas   tiempo
  RANDOM     3   20656.41   20826.14   221.0 +262.3%    130     81ms
  GREEDY     1    9517.33    9517.33       -  +66.9%     63     77ms
  GENETIC    3    5952.10    5960.98     7.7   +4.4%     36     2.6s
```

### La columna `desv`

Ningún motor salvo el voraz es reproducible, así que una ejecución suelta no dice nada. Si dos
configuraciones se separan menos que esta desviación, la diferencia es ruido y no mejora. Aparece `-`
cuando solo ha habido una ejecución: sin repeticiones no hay dispersión que medir.

**Con pocas repeticiones engaña.** El genético converge a la misma solución a menudo, y en instancias
pequeñas es fácil que dos vueltas den el mismo número. En `p01`, cinco vueltas dan tres valores
distintos; dos vueltas pueden dar cero dispersión aparente.

El número de repeticiones lo decide el descriptor del solver: si declara `deterministic: true`, se
ejecuta una sola vez porque repetirlo solo gasta tiempo.

### El coste que se compara

Es el **recalculado desde las paradas** de cada ruta, no el que informa el motor. El `--csv` trae los
dos, `coste` y `coste_informado`, así que si alguna vez dejaran de coincidir se vería.

El script no comprueba que la solución cumpla las restricciones de la instancia. Eso lo hace
`assertFeasible` en el test del motor genético, descrito más arriba.

## Benchmark a través de la API

Para medir contra el sistema desplegado en lugar de contra la clase directamente:

```bash
curl -X POST "http://localhost:8090/api/v1/fms/instances/send?fileName=p22&solverType=GENETIC"
```

Este camino sí ejerce el mapeo real del gateway y la comunicación entre contenedores, pero **no
valida factibilidad**: la comprobación hay que hacerla aparte. Es el camino por el que se detectó la
anomalía de p23, precisamente porque el coste salió por debajo del BKS.
