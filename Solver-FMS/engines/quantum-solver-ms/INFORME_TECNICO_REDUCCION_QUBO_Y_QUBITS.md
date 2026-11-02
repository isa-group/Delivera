# Informe técnico: tamaño del modelo Qiskit y estimación de qubits

## 1. Objeto del informe

Este informe describe el tamaño de la formulación Qiskit utilizada por el microservicio para subproblemas reducidos del MD-CVRP. Se analizan las variables lógicas, las variables auxiliares introducidas al convertir restricciones a QUBO, el efecto de las restricciones MTZ y las limitaciones de los simuladores y dispositivos cuánticos.

El particionamiento espacial reduce el número de clientes que llegan a cada subproblema, pero no establece una equivalencia entre número de Súper-Clientes y número de qubits. El número final depende también del número de vehículos, la codificación de capacidad, las restricciones y la conversión a QUBO.

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

Para que la demanda media de un Súper-Cliente no exceda la capacidad por vehículo, debe cumplirse:

$$
Q_i \geq \overline{q_i}\left\lceil\frac{a_i}{K_i}\right\rceil
$$

De esta condición se obtiene la cota:

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

### 4.3 Variables enteras de carga

Con MTZ activado, se declara una variable entera `u[i,k]` para cada cliente y vehículo. Si la capacidad del vehículo es `Q`, una estimación del número de bits necesarios para codificar cada variable es:

$$
b=\left\lceil\log_2(Q+1)\right\rceil
$$

Por tanto:

$$
N_u\approx nkb
$$

### 4.4 Estimación previa

Una estimación orientativa del número de variables antes de convertir las restricciones es:

$$
N_{raw}\approx kn(n+1)+nk+nkb
$$

equivalentemente:

$$
N_{raw}\approx k(n^2+2n+nb)
$$

Esta expresión no predice por sí sola el número final de qubits. El valor fiable se obtiene construyendo el modelo y midiendo el QUBO resultante.

## 5. Conversión a QUBO

QAOA utiliza una función objetivo sin restricciones. `QuadraticProgramToQubo` transforma las restricciones de igualdad y desigualdad en términos de penalización incorporados a la función objetivo. Para ello puede introducir variables auxiliares, incluidas variables slack para representar desigualdades.

El número final puede expresarse como:

$$
N_{qubo}=N_{raw}+N_{slack}+N_{aux}
$$

El número de variables auxiliares depende de los rangos, los coeficientes y las restricciones concretas. Por este motivo, la medición correcta en el código es:

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

## 7. Mediciones reproducibles

En mediciones simplificadas con capacidad 6 y 2 vehículos se obtuvo:

| Clientes | Variables raw | Variables QUBO simplificadas |
|---:|---:|---:|
| 1 | 8 | 14 |
| 2 | 20 | 28 |
| 3 | 36 | 48 |
| 4 | 56 | 72 |
| 5 | 80 | 100 |

Con 5 vehículos:

| Clientes | Variables raw | Variables QUBO simplificadas |
|---:|---:|---:|
| 1 | 20 | 35 |
| 2 | 50 | 70 |
| 3 | 90 | 120 |
| 4 | 140 | 180 |
| 5 | 200 | 250 |

Estas tablas aíslan el crecimiento básico y no incluyen necesariamente todas las variables auxiliares del modelo completo.

En una formulación completa anterior, un subproblema con 2 clientes y 2 vehículos alcanzó 64 qubits lógicos durante la ejecución. La diferencia frente a las 28 variables de la tabla se debe a que el modelo completo incorpora restricciones de asignación, flujo, depósito, capacidad, subtours y las variables auxiliares correspondientes.

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
