# Informe técnico: tamaño del modelo Qiskit y estimación de qubits

## 1. Objeto del informe

Este informe describe el tamaño de la formulación Qiskit utilizada por el microservicio para subproblemas reducidos del MD-CVRP. Se analizan las variables lógicas, las variables auxiliares introducidas al convertir restricciones a QUBO, el efecto de las restricciones MTZ y las limitaciones de los simuladores y dispositivos cuánticos.

El particionamiento espacial reduce el número de clientes que llegan a cada subproblema, pero no establece una equivalencia entre número de Súper-Clientes y número de qubits. El número final depende también del número de vehículos, la codificación de capacidad, las restricciones y la conversión a QUBO.

Revisión del 5 de noviembre de 2026: se distinguen las variables del modelo de sus bits de codificación y se añaden recuentos reproducidos con el código actual de Delivera. La sección 7 conserva los casos medidos y el ajuste posterior del simulador.

## 2. Estimación regional del número de Súper-Clientes

Sean `n` el número de depósitos y `a_i` el número de clientes de la región `i`, con `i` en `{1, ..., n}`. Para cada región, `Q_i` es la capacidad máxima de cada vehículo asignado al depósito `i` y `q_ij` es la demanda del cliente `j`, con `j` en `{1, ..., a_i}`.

La demanda total regional es:

$$
D_i = \sum_{j=1}^{a_i} q_{ij}
$$

Si la región se divide en `K_i` Súper-Clientes y se supone un reparto uniforme, cada Súper-Cliente contiene aproximadamente:

$$
\left\lceil\frac{a_i}{K_i}\right\rceil
$$

clientes internos. La demanda media de un cliente de la región es:

$$
\overline{q_i} = \frac{1}{a_i}\sum_{j=1}^{a_i} q_{ij}
$$

Como aproximación bajo reparto uniforme de clientes y demandas, se considera:

$$
Q_i \geq \overline{q_i}\left\lceil\frac{a_i}{K_i}\right\rceil
$$

Independientemente de esa aproximación, para que la demanda total quepa en `K_i` grupos de capacidad `Q_i` es necesario que `K_i Q_i >= D_i`. Esto da la cota:

$$
K_i \geq \left\lceil\frac{\overline{q_i}a_i}{Q_i}\right\rceil
= \left\lceil\frac{\sum_{j=1}^{a_i}q_{ij}}{Q_i}\right\rceil
= \left\lceil\frac{D_i}{Q_i}\right\rceil
$$

El código utiliza:

$$
K_i = \max\left(1, \left\lceil\frac{D_i}{Q_i}\right\rceil\right)
$$

`D_i` es, por tanto, la demanda total de la región, y `Q_i` la capacidad de un vehículo de esa región. `K_i` es una cota inferior basada en capacidad, no una predicción exacta del número de variables ni una garantía de que un clustering geométrico concreto produzca Súper-Clientes factibles. Tras agrupar, cada demanda se valida frente a `Q_i`. Además, se exploran los candidatos `K_i`, `K_i + 1`, `K_i + 2` y `K_i + 3` hasta el número real de clientes de la región.

## 3. Formulación considerada

Para cada subproblema, el solver Qiskit recibe un depósito, sus Súper-Clientes y la flota asignada a la región. El modelo utiliza:

```text
x[u, v, k] = 1 si el vehículo k recorre el arco u -> v
y[i, k] = 1 si el cliente i se asigna al vehículo k
u[i, k] = carga acumulada del vehículo k al llegar al cliente i
```

Las variables `u` solo se incorporan cuando se activa `include_mtz=True`. Sin MTZ, el modelo utiliza una restricción agregada de capacidad por vehículo.

La función objetivo minimiza la suma de las distancias de los arcos activos. Las distancias son euclídeas redondeadas al entero más cercano, de acuerdo con la convención utilizada en el resto del proyecto.

## 4. Número de variables antes de la conversión

Sea `n` el número de clientes o Súper-Clientes del subproblema y `k` el número de vehículos.

### 4.1 Variables de arco

El depósito más los `n` clientes forman `n+1` nodos. Sin bucles, cada vehículo puede utilizar:

$$
(n+1)n
$$

arcos. Para `k` vehículos:

$$
N_x = k n(n+1)
$$

Este término crece como $O(kn^2)$.

### 4.2 Variables de asignación

Cada cliente puede asignarse a cada vehículo:

$$
N_y = nk
$$

### 4.3 Variables enteras de carga y sus bits

Con MTZ activado, se declara una variable entera `u[i,k]` para cada cliente y vehículo. Si la capacidad del vehículo es `Q`, una estimación del número de bits necesarios para codificar cada variable es:

$$
b=\left\lceil\log_2(Q+1)\right\rceil
$$

Hay `nk` variables enteras de carga en el modelo, cuando se activa MTZ. Después de codificarlas en binario, ocupan aproximadamente:

$$
B_u\approx nkb
$$

### 4.4 Recuento del modelo y estimación binaria

El valor `problem.get_num_vars()` cuenta cada variable entera como una variable, no como varios bits. Si `I_MTZ` vale 1 cuando MTZ está activado y 0 cuando está desactivado:

$$
N_{raw}=kn(n+1)+nk+I_{MTZ}nk
$$

En cambio, la estimación del número de bits de las variables originales, antes de añadir los bits auxiliares de las restricciones, es:

$$
B_{base}\approx kn(n+1)+nk+I_{MTZ}nkb
$$

Por ejemplo, con dos clientes, dos vehículos y capacidad 6, el modelo con MTZ tiene 20 variables originales: 16 binarias y 4 enteras. Codificar esas cuatro cargas requiere 12 bits, de modo que `B_base = 28`. El QUBO completo medido tiene 68 bits, al incluir también los auxiliares. Sin MTZ son 16 variables binarias originales y 22 bits en el QUBO.

Ni `N_raw` ni `B_base` predicen por sí solos el número final de qubits. El valor fiable se obtiene construyendo el modelo y midiendo el QUBO resultante.

## 5. Conversión a QUBO

QAOA utiliza una función objetivo sin restricciones. `QuadraticProgramToQubo` transforma las restricciones de igualdad y desigualdad en términos de penalización incorporados a la función objetivo. Para ello puede introducir variables auxiliares, incluidas variables slack para representar desigualdades.

El número final puede expresarse como:

$$
N_{qubo}=B_{base}+B_{slack}+B_{aux}
$$

El número de bits auxiliares depende de los rangos, los coeficientes y las restricciones concretas. Los convertidores pueden transformar algunas desigualdades directamente en penalizaciones y otras mediante variables slack. Por ello, no todas añaden el mismo número de bits. Véase la [documentación oficial de convertidores de Qiskit Optimization 0.7.0](https://qiskit-community.github.io/qiskit-optimization/apidocs/qiskit_optimization.converters.html). La medición correcta en el código es:

```python
qubo = QuadraticProgramToQubo().convert(problem)
qubit_count = qubo.get_num_vars()
```

`qubit_count` representa el número de variables binarias lógicas de la formulación convertida. No equivale necesariamente al número de qubits físicos que podrá utilizar un dispositivo, porque todavía deben considerarse conectividad, transpilation, profundidad y ruido.

## 6. Restricciones MTZ y efecto en los qubits

Las restricciones MTZ evitan que los arcos de clientes formen subtours desconectados del depósito. Se han hecho opcionales porque su coste en el tamaño del QUBO es cuantificable. Para cada pareja cliente-vehículo se declara una variable entera de carga `u[i,k]`. Si la capacidad es `Q`, su codificación requiere aproximadamente `b = ceil(log2(Q + 1))` variables binarias. Por tanto, solo estas cargas añaden aproximadamente `nkb` variables binarias antes de convertir las restricciones.

Además, para cada vehículo se genera una restricción MTZ por cada par ordenado de clientes distintos. Su número es:

$$
N_{MTZ}=kn(n-1)
$$

Cada una de estas restricciones pasa después al convertidor `QuadraticProgramToQubo`, que la incorpora como una penalización en la función objetivo y puede añadir variables auxiliares. Por ello, el incremento efectivo del QUBO no se limita a los bits de `u`: depende también de las penalizaciones y de las variables auxiliares que cree el convertidor.

Sin MTZ, el modelo elimina esas variables enteras de carga y utiliza una restricción agregada:

$$
\sum_i q_i y_{i,k}\leq Q_k
$$

Esto reduce el número de variables y de restricciones que deben transformarse a QUBO. La contrapartida es que el flujo puede admitir ciclos de clientes desconectados del depósito. La decodificación reconstruye la ruta desde el depósito y rechaza soluciones cuyos clientes asignados no sean alcanzables desde él, pero esta comprobación posterior no sustituye a MTZ como garantía formal.

Con MTZ activado se obtiene una formulación más estricta, a costa de aumentar el tamaño del modelo. Sin MTZ se eliminan tanto las `nkb` variables binarias aproximadas de carga como las `kn(n-1)` restricciones MTZ, aunque se mantienen las restricciones agregadas de capacidad. La medición definitiva de ambas configuraciones se realiza con `qubo.get_num_vars()`. La opción se expone en el runner mediante `--include-mtz`. El endpoint de producción la mantiene desactivada por defecto.

## 7. Mediciones y pruebas

### Recuento actual del QUBO

Se contaron 30 modelos del código de Delivera con un depósito, demanda 1 por cliente y capacidad 6 por vehículo. Estos valores son bits del QUBO, no ejecuciones de sus circuitos:

| Clientes reducidos | 1 vehículo, sin MTZ | 1 vehículo, con MTZ | 2 vehículos, sin MTZ | 2 vehículos, con MTZ | 5 vehículos, sin MTZ | 5 vehículos, con MTZ |
|---:|---:|---:|---:|---:|---:|---:|
| 1 | 7 | 13 | 14 | 26 | 35 | 65 |
| 2 | 11 | 34 | 22 | 68 | 55 | 170 |
| 3 | 18 | 66 | 36 | 132 | 90 | 330 |
| 4 | 27 | 108 | 54 | 216 | 135 | 540 |
| 5 | 38 | 160 | 76 | 320 | 190 | 800 |

Se utilizó Qiskit 2.5.2 y Qiskit Optimization 0.7.0. Las tablas simplificadas anteriores no representaban esta formulación completa. El antecedente de 64 qubits para dos clientes y dos vehículos no se ha reproducido con su configuración original. Con capacidad 6 y demandas 1, el código actual necesita 68 con MTZ.

El caso de seis qubits usa un vehículo de capacidad 3 y un único cliente reducido de demanda 2. Son 2 arcos, 1 asignación y 3 bits auxiliares. Si ese nodo agrupa dos clientes originales, PyVRP ordena después sus entregas.

### Pruebas anteriores al ajuste del simulador

Presupuesto de 30 s, 1024 shots, COBYLA con `maxiter=20`, `reps=1` y semilla 42:

| Caso | Originales → reducidos | Qubits | Resultado |
|---|---:|---:|---|
| Un cliente, un vehículo, capacidad 3 | 1 → 1 | 6 | Válida, coste 20, 2.88 s |
| Dos clientes directos, capacidad 3 | 2 → 2 | 10 | Timeout |
| Un cliente y dos vehículos, capacidad 3 | 1 → 1 | 12 | Timeout |
| Un cliente con MTZ, capacidad 3 | 1 → 1 | 10 | Timeout |
| Tres clientes directos, capacidad 4 | 3 → 3 | 18 | No ejecutado, superaba el umbral experimental de 14 |
| Veinte clientes agrupados, capacidad 40 | 20 → 1 | 10 | Timeout |
| `quantum_small.txt`, capacidad 100 | 2 → 1 | 11 | Timeout |
| Dos clientes agrupados, capacidad 3 | 2 → 1 | 6 | Válida, coste 22, 3.05 s |

Reducir COBYLA a 6 evaluaciones en el caso de dos clientes directos tampoco evitó el timeout. Los dos costes válidos coincidieron con el óptimo enumerado. Fueron pruebas del solver y de expansión, sin fallback ni llamadas HTTP. La prueba de un cliente directo de demanda 2 y capacidad 3 elude el filtro de granularidad que aplicaría la API.

### Ajuste del simulador y presupuesto

El perfil de ejecución mostró tiempo consumido en `PauliEvolutionGate.to_matrix()` y en la exponencial de SciPy. Ahora QAOA transpila el circuito a puertas básicas antes de usar `StatevectorSampler`. La prueba de equivalencia de estados verifica que esta transformación conserva la evolución.

Se mantienen `maxiter=20`, `reps=1` y 1024 shots. Probar 60 evaluaciones no aportó una mejora en los casos comparados. Estas opciones pueden ajustarse en las funciones Python y en la prueba manual, sin cambiar las restricciones del problema.

Si no se proporciona un timeout, se calcula a partir del QUBO real. Para un subproblema y los parámetros predeterminados: hasta 10 qubits, 60 s. De 11 a 14, 90 s. De 15 a 18, 150 s. De 19 a 21, 210 s. De 22 a 24, 300 s. Incluye 30 s de margen de arranque. Varios subproblemas suman sus presupuestos hasta un máximo de 300 s por candidato. Aumentar `maxiter` o `reps` amplía el presupuesto dentro de ese máximo. Un límite explícito se respeta y no se amplía automáticamente.

Con el circuito descompuesto se comprobaron estos resultados, con la misma profundidad, shots y límite de evaluaciones:

| Caso | Semilla | Qubits | Resultado |
|---|---:|---:|---|
| Dos clientes directos | 42 | 10 | Sin muestra factible, 2.08 s |
| Dos clientes directos | 7 | 10 | Válida, coste 22, 2.15 s |
| `quantum_small.txt` agrupado | 42 | 11 | Válida, coste 32, 2.20 s |
| Dos clientes agrupados | 42 | 6 | Válida, coste 22, 1.83 s |
| Tres clientes directos | 7 | 18 | Subtour rechazado, 54.08 s |

Los tres costes válidos coinciden con los óptimos enumerados de esos ejemplos pequeños. Completar la simulación no garantiza una muestra factible. No hay una medida estadística de tasa de éxito ni evidencia de ventaja cuántica.

### Repetir una prueba

```powershell
python -B tests/run_qiskit_validation.py --case two-clients --seed 7
python -B tests/run_qiskit_validation.py --case quantum_small.txt --timeout 120
```

El script imprime un resultado breve y admite `--maxiter`, `--reps`, `--mtz` y `--output`. Las pruebas directas reciben clientes sin agrupar. Las agrupadas ejecutan GAP y clustering con offset 0, y expanden y validan la respuesta. Los resultados históricos y actuales se conservan en un único [resumen JSON](tests/qiskit_results.json). Los tiempos incluyen el proceso aislado y la expansión, no la referencia clásica. Son simulaciones locales y no ejecuciones en QPU.

## 8. Límite del simulador y dispositivos reales

El límite operativo utilizado en el proyecto es de 24 qubits por subproblema. El solver actual utiliza `StatevectorSampler` como simulador local de Qiskit. No se realiza una conexión automática con IBM Quantum ni se ejecuta el circuito en un procesador cuántico real.

Un simulador de estado vectorial tiene un coste que crece exponencialmente con el número de qubits. Además, un dispositivo real introduce restricciones adicionales:

- Número de qubits físicos.
- Conectividad entre qubits.
- Transpilation a la topología del dispositivo.
- Profundidad del circuito.
- Ruido y errores de lectura.
- Número de ejecuciones o shots.
- Tiempo y cuota disponibles.

Por tanto, el número de qubits lógicos del QUBO es una condición necesaria para estudiar la ejecución, pero no garantiza por sí solo que la ejecución sea posible o útil.

## 9. Quantum knitting o circuit cutting

Quantum knitting, también denominado circuit knitting o circuit cutting, divide un circuito en subcircuitos, ejecuta cada parte y reconstruye estadísticamente el resultado.

Esta técnica puede reducir el número de qubits físicos usados en una ejecución individual, pero no elimina el número de variables lógicas del QUBO. Además:

1. Introduce fronteras que deben reconstruir correlaciones entre subcircuitos.
2. Puede aumentar rápidamente el número total de ejecuciones.
3. Amplifica el efecto del ruido y del error de lectura.
4. No elimina las restricciones de capacidad ni de eliminación de subtours.
5. Hace especialmente compleja la reconstrucción cuando se separan variables relacionadas por MTZ.

En esta formulación, circuit cutting es una línea experimental independiente, no un mecanismo que convierta automáticamente un QUBO de 64 qubits en uno equivalente de 24 qubits con el mismo coste y calidad.

## 10. Conclusiones

El número de Súper-Clientes mide la reducción del grafo, no el número de qubits. El tamaño del QUBO depende de `n`, `k`, las variables de capacidad, las restricciones y las variables auxiliares introducidas por la conversión.

Las restricciones MTZ proporcionan una garantía formal frente a subtours, pero añaden variables y restricciones que pueden aumentar notablemente el número de qubits. Su carácter opcional permite comparar una formulación compacta con una formulación más estricta.

La medición definitiva debe realizarse siempre sobre el QUBO generado por el modelo concreto. Las estimaciones asintóticas sirven para explicar el crecimiento, pero no sustituyen a `qubo.get_num_vars()`.
