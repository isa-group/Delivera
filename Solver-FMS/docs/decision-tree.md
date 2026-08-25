# Árbol de decisión: qué solver conviene

Entrena un árbol de decisión sobre los datos que produce [`compare_solvers.py`](benchmark.md) y
obtiene una regla que elige solver a partir de las propiedades de la instancia: número de
clientes, depósitos, capacidad, densidad, demanda total y ocho más.

Se le pasa el `datos-*.csv` de un experimento y **busca su `instancias-*.csv` al lado**, que es de donde salen esas propiedades. El nivel de observación del árbol es la instancia: una fila, una decisión. Las ejecuciones se agregan hasta ahí -cada solver se queda con su mejor coste y su tiempo medio- y se les pegan las propiedades del problema.

Los solvers **se leen de los datos**, no están escritos en el código. El script clasifica sobre
los que encuentre en el CSV, sean dos o seis.

```bash
python decision_tree.py                          # el experimento más reciente de results/
python decision_tree.py results/datos-*.csv      # varios experimentos juntos
python decision_tree.py --exclude RANDOM,GREEDY  # solo candidatos de producción
```

Desde `Solver-FMS/`. A diferencia del paquete `comparison`, este **necesita dependencias**:

```bash
pip install pandas scikit-learn matplotlib
```

Cada ejecución deja dos ficheros bajo `--out`: el informe `tree-<fecha>.md` y la imagen
`tree-<fecha>.png`.

| Parámetro | Por defecto | Significado |
|---|---|---|
| `csv` | el más reciente de `results/` | Uno o varios `datos-*.csv`. Su `instancias-*.csv` hermano se lee solo. Al juntarlos, cada instancia se queda con su mejor coste |
| `--exclude` | - | Solvers a dejar fuera, separados por coma |
| `--tolerance` | `1.0` | Margen en % dentro del cual dos solvers se consideran de igual calidad |
| `--max-depth` | `10` | Profundidad máxima del árbol |
| `--min-leaf` | `5` | Instancias mínimas por hoja |
| `--folds` | `10` | Particiones de la validación cruzada |
| `--permutations` | `2000` | Permutaciones del contraste de significancia |
| `--seed` | `20260819` | Semilla, para que el árbol sea reproducible |
| `--out` | `results/` | Directorio de salida |
| `--skip-selftest` | - | No verificar el algoritmo sobre el problema sintético |

## Qué significa «conviene»

Es lo que el árbol aprende a predecir, así que define el problema entero.

**Conviene el solver de menor coste factible.** Si otros quedan a menos de `--tolerance` por
ciento del mejor, todos cuentan como de la misma calidad y **entre ellos gana el más rápido**.

Es la decisión real en producción: si dos motores dan prácticamente el mismo coste, el que tarda
diez veces menos es mejor elección. Y evita que el árbol separe diferencias que vienen de la
semilla y no del algoritmo: entre dos ejecuciones estocásticas, la distancia entre 5,1 % y 5,2 %
de gap es ruido.

La columna `Decidida por` del informe marca cada instancia como `coste` o `tiempo`.

La tolerancia cambia el problema, y conviene verlo medido. Sobre las 33 instancias Cordeau,
excluyendo el aleatorio:

| `--tolerance` | Reparto |
|---:|---|
| 0 % | `GENETIC` 33 |
| 10 % | `GENETIC` 33 |
| 20 % | `GENETIC` 32 · `GREEDY` 1 |
| 30 % | `GENETIC` 29 · `GREEDY` 4 |
| 40 % | `GENETIC` 15 · `GREEDY` 18 |
| 60 % | `GENETIC` 4 · `GREEDY` 29 |
| 80 % | `GREEDY` 33 |

El genético gana en coste las 33 instancias, con márgenes sobre el segundo que van del 19,4 % al
73,6 %. Por debajo de ese 19,4 % ninguna instancia cambia de manos.

Dos detalles del cálculo:

- El coste es el **mejor** de las N repeticiones; el tiempo es la **media**. Eso favorece
  ligeramente al estocástico, que pagó N veces ese tiempo para conseguir su mejor vuelta.
- Si dos solvers empatan **exactamente** en tiempo, decide el orden de aparición en las ejecuciones.

## Cómo leer el informe

### Lo primero: ¿vale algo el árbol?

| Medida | Qué dice |
|---|---|
| Acierto en validación cruzada | Cuánto acierta con instancias que no ha visto |
| Acierto eligiendo siempre el solver mayoritario | Lo que se consigue **sin árbol** |
| p del contraste de permutación | Con qué frecuencia el azar iguala ese acierto |

**Un árbol que no bate a la regla mayoritaria no sirve, aunque el dibujo tenga ramas.** Es la
fila que hay que mirar antes que el árbol: un 80 % de acierto suena bien hasta que se ve que
elegir siempre el mismo solver da un 78 %.

El contraste de permutación reentrena el árbol con las etiquetas barajadas 2000 veces y cuenta
cuántas igualan el acierto real. Consume el 90 % del tiempo de ejecución —unos 40 s, frente a los
0,5 ms que cuesta entrenar el árbol— y es lo que distingue un patrón de una casualidad con 33
instancias.

### Cuando solo un solver gana todas las instancias

El informe muestra el árbol, que es un único nodo, y **no publica métricas**. Con una sola clase
el acierto es del 100 % por construcción: acertar siempre es trivial cuando solo hay una respuesta
posible, y ese 100 % junto al de un árbol con ramas invitaría a compararlos.

Esa imagen no la dibuja `plot_tree`, sino código propio en `plot.py`. sklearn omite la línea
`class =` cuando el árbol tiene una sola clase, y produce una caja en blanco con `samples = 33` y
`value = 1.0` que no menciona qué solver conviene. El nodo propio incluye el nombre del solver y
el margen sobre el segundo.

### La verificación del algoritmo

Un árbol de un nodo no demuestra que el código funcione: un árbol roto y un árbol correctamente
trivial se ven igual. Cada ejecución entrena además sobre un problema sintético con la respuesta
conocida —120 muestras donde la regla verdadera es `señal > 150`, más dos características de puro
ruido que debe ignorar— y comprueba tres cosas: que corta por la columna correcta, que encuentra
el umbral (±15) y que acierta por encima del 95 % dejando una fuera.

```
Autoverificacion del algoritmo: PASA (umbral 150.2 sobre 150 real, acierto 99.2 %)
```

`PASA` significa que el resultado sobre los datos reales es un resultado. `FALLA` significa que
hay un bug. Se desactiva con `--skip-selftest`.

### Los valores de los nodos

En la imagen, `value = [16.5, 16.5]` **no son instancias**: son recuentos ponderados por
`class_weight="balanced"`, que compensa que una clase tenga más ejemplos que otra. Con 17 y 16
instancias, `17 × 0,9706 = 16,5` y `16 × 1,0312 = 16,5`. El recuento real está en la línea
`samples`.

## Ejemplo: voraz frente a genético

El genético gana en coste las 33 instancias, así que por coste puro el árbol es un nodo. La
pregunta *«aceptando hasta un 40 % más de coste a cambio de velocidad, ¿cuándo basta el voraz?»*
sí reparte las instancias entre dos clases:

```bash
python decision_tree.py --exclude RANDOM --tolerance 40 --max-depth 3
```

```
Instancias: 33  |  solvers: GENETIC, GREEDY
Conviene: GREEDY 18, GENETIC 15
Autoverificacion del algoritmo: PASA (umbral 150.2 sobre 150 real, acierto 99.2 %)

Acierto en validacion cruzada: 81.7 % (regla mayoritaria: 55.0 %, p = 0.0035)
```

El árbol que sale:

```
vehicle_capacity <= 70          -> GENETIC   (12 instancias, puro)
vehicle_capacity > 70
    customers_per_depot <= 55   -> GREEDY    (16 instancias, puro)
    customers_per_depot > 55    -> GENETIC   (5 instancias, mezclado)
```

Con vehículos pequeños o depósitos muy cargados compensa pagar el genético; en la zona intermedia
el voraz llega lo bastante cerca y es unas treinta veces más rápido. Supera en 26,7 puntos a
elegir siempre el mismo solver, y el azar iguala ese acierto en el 0,35 % de los barajados.

Ese 40 % es un umbral de negocio, no un hallazgo del análisis: lo fija quien decide cuánto coste
está dispuesto a cambiar por velocidad.

## Ajustar el árbol

- **Subir `--max-depth` casi siempre sube el acierto en entrenamiento y lo baja en validación.**
  Si al subirlo mejora la validación cruzada, el árbol se quedaba corto; si empeora, está
  memorizando. Con 33 instancias, un árbol sin frenos las memoriza y da un 100 % que no significa
  nada.
- **Una característica con importancia 0,000 que aun así aparece en un corte** marca un corte que
  no cambia la decisión: sobra profundidad.
- **`--exclude RANDOM,GREEDY`** cuando la pregunta sea cuál de los motores de producción usar.
  Incluir las líneas base infla el acierto con instancias que nadie dudaba.
- **`--folds` afecta a la estabilidad de la estimación.** Con 33 instancias y 10 particiones, cada
  pliegue tiene 3 instancias y fallar una son 33 puntos: el promedio es sólido, la desviación
  enorme. Con `--folds 5` sale más estable.
- **`--permutations` es lineal en tiempo.** Con 200 la ejecución baja a unos 6 s, a cambio de
  resolución: por debajo de p ≈ 0,005 ya no distingue.

## Dónde está cada cosa

`decision_tree.py` es la línea de comandos y el cableado. El trabajo está en
`experimentation/tree/`:

| Módulo | Qué sabe |
|---|---|
| `dataset.py` | Qué significa «conviene»: coste y, a igualdad de coste, tiempo |
| `model.py` | Entrenar, validar contra la regla mayoritaria y verificar el algoritmo |
| `plot.py` | El árbol como imagen |
| `report.py` | El informe en Markdown |

## Limitaciones

- **33 instancias.** Cualquier modelo sobre esa cantidad está al límite. Por eso los valores por
  defecto son conservadores y el acierto que se reporta es siempre en validación cruzada, nunca
  sobre los datos de entrenamiento.
- **Los árboles son inestables con pocos datos:** cambiar una instancia puede cambiar el primer
  corte. Antes de llevar una regla al código conviene comprobar si sobrevive a repetir el
  experimento con otra semilla.
- **Las instancias Cordeau no son datos reales de reparto.** Una regla aprendida aquí vale para
  elegir motor en el banco de pruebas; extrapolarla a producción es una hipótesis, no un
  resultado.
- **El árbol no dice qué algoritmo es mejor, dice cuál conviene según el criterio configurado.**
  Cambiar `--tolerance` cambia las respuestas, y es correcto que lo haga.
