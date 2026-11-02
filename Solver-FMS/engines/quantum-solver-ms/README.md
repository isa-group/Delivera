# Quantum Solver MS

Microservicio experimental para estudiar la aplicación de técnicas cuánticas e híbridas al problema de rutas de vehículos con múltiples depósitos y capacidades (MD-CVRP). El proyecto combina:

- Un preprocesado clásico basado en GAP/MILP (Generalized Assignment Problem) y particionamiento espacial.
- Agrupación de clientes cercanos en Súper-Clientes.
- Formulaciones reducidas para Qiskit/QAOA y D-Wave/CQM.
- PyVRP como referencia clásica, backend directo y mecanismo de fallback.
- Evaluación local reproducible sobre instancias Cordeau.

El objetivo no es afirmar que el MD-CVRP completo de una instancia del benchmark ya sea ejecutable en una QPU pequeña. El objetivo es medir de forma explícita qué parte del problema puede reducirse, cuántas variables genera la formulación, qué coste se pierde al agrupar clientes y cuándo resulta necesario derivar el caso a un solver clásico.

## 1. Arquitectura general

El flujo de una petición es:

```text
JSON de RoutingRequest + backend solicitado
        |
        +--> `classical`: PyVRP sobre la instancia global
        |
        `--> `qiskit` o `dwave`
                |
                v
GraphAnalyzer: filtro global previo
        |
        v
GAP/MILP: asignación de clientes a depósitos
        |
        v
K_min regional + offsets K_min..K_min+3
        |
        v
Súper-Clientes mediante clustering geométrico
        |
        v
Validación de capacidad de Súper-Clientes y número de qubits
        |
        +--> QUBO/QAOA con Qiskit
        |
        +--> CQM híbrido con D-Wave
        |
        +--> PyVRP si el filtro, la formulación o el backend fallan
        |
        v
Coste global + coste interno de Súper-Clientes
        |
        v
RoutingResponse + métricas
```

La API de producción está en `api/main.py`. El runner `tests/run_local_evaluation.py` reproduce localmente la misma preparación y evaluación usando archivos en formato Cordeau.

## 2. Estructura de directorios

```text
quantum-solver-ms/
├── api/
│   └── main.py
├── domain/
│   ├── models.py
│   └── spatial_partitioner.py
├── services/
│   ├── evaluation_service.py
│   └── graph_analyzer.py
├── solvers/
│   ├── pyvrp_solver.py
│   ├── quantum_dwave.py
│   ├── quantum_qiskit.py
│   └── quantum_qiskit_proc_isolate.py
├── tests/
│   ├── run_local_evaluation.py
│   ├── test_parser.py
│   ├── test_evaluate_quantum_viability.py
│   ├── test_api.py
│   ├── test_quantum_preparation.py
│   └── data/
│       ├── p01 ... p23
│       ├── quantum_demo.txt
│       ├── quantum_small.txt
│       └── data_test_metric_*.txt
├── utils/
│   └── parser.py
├── requirements.txt
├── INFORME_TECNICO_REDUCCION_QUBO_Y_QUBITS.md
└── README.md
```

### `api/main.py`

Crea la aplicación FastAPI y expone:

```text
GET  /health
POST /api/v1/quantum-routing/analyze
POST /api/v1/quantum-routing/solve
```

`GET /health` expone el estado mínimo del servicio y los backends disponibles, por lo que puede utilizarse en monitores, contenedores y orquestadores. `POST /api/v1/quantum-routing/analyze` devuelve el filtro de `GraphAnalyzer` y sus features sin ejecutar ningún solver.

El endpoint `solve`:

1. Recibe un `RoutingRequest` validado por Pydantic.
2. Obtiene el backend de la cabecera `X-Target-Quantum-Backend`.
3. Resuelve directamente con PyVRP cuando el backend es `classical`.
4. Aplica el filtro inicial de `GraphAnalyzer` para `qiskit` y `dwave`.
5. Prepara regiones, Súper-Clientes y candidatos `K_min..K_min+3` para los backends cuánticos.
6. Ejecuta Qiskit o D-Wave.
7. Usa PyVRP como fallback cuando no hay candidato cuántico viable o el solver falla.
8. Calcula las métricas comunes.
9. Devuelve un diccionario con `routing_response` y `metrics`.

El backend predeterminado es `qiskit`. Los valores admitidos son `qiskit`, `dwave` y `classical`. Un valor distinto devuelve HTTP 422. Qiskit se ejecuta en un proceso aislado con un límite temporal de 30 segundos por candidato. Si vence, el candidato se descarta. Si no queda ningún candidato viable, la instancia se deriva a PyVRP. Como se exploran hasta cuatro candidatos, el límite total de una solicitud puede ser mayor.

El endpoint explora cuatro offsets (`0` a `3`). Permite ajustar el límite de qubits hasta un máximo de `24` y activar MTZ para Qiskit. Por defecto usa `24` qubits y MTZ desactivado.

### PyVRP heterogéneo y reutilización de vehículos

`POST /api/v1/HGS-ADC/solve` recibe el contrato de Delivera definido en
`api/adapter/models.py` y utiliza `solvers/pyvrp_v2_solver.py`. Este endpoint es
independiente del clásico y fallback del flujo cuántico, que siguen utilizando
`solvers/pyvrp_solver.py`.

El objetivo es minimizar la distancia total. Se permiten viajes adicionales
cuando reducen la distancia, dentro del límite autorizado. No se minimiza primero
el número de viajes y no se calcula un coste monetario.

Todas las opciones elegibles por el usuario se reciben dentro de `parameters`.
Las cabeceras `X-*` ya no configuran este endpoint. La API nativa
`/api/v1/quantum-routing/solve` mantiene por ahora su contrato de cabeceras.

| Campo de `parameters` | Valor predeterminado | Significado |
|---|---|---|
| `classicalTimeLimit` | `0.1` | Segundos de búsqueda PyVRP, admite decimales entre 0.1 y 300 |
| `seed` | `42` | Semilla entera entre 0 y 2147483647. El límite por tiempo puede afectar a la reproducibilidad |
| `maxVehicleRepetition` | `1` | Máximo de viajes por vehículo, incluido el primero. Entero de 1 a 10 |
| `vehicleStrategy` | `future_hybrid` | Reparto entre vehículos del mismo depósito y capacidad |
| `vehicleStrategyConfig` | `interurban` | Perfil de pesos de distancia y carga para el reparto híbrido |

Fragmento de una petición:

```json
{
  "parameters": {
    "classicalTimeLimit": 0.5,
    "seed": 42,
    "maxVehicleRepetition": 2,
    "vehicleStrategy": "distance",
    "vehicleStrategyConfig": "balanced"
  }
}
```

Las opciones omitidas toman su valor predeterminado. Los valores inválidos y los
nombres desconocidos devuelven HTTP 422 antes de ejecutar PyVRP. El campo de la
petición se llama `parameters`, como en Delivera. Para que atraviesen el gateway,
sus metadatos deberán declarar esos mismos nombres y tipos.

Con un límite de 1, cada camión realiza como máximo un viaje. Con un límite de 2,
puede realizar dos. Los vehículos se agrupan por depósito y capacidad, sin exigir
homogeneidad de toda la flota. PyVRP genera las rutas con esa disponibilidad y la
asignación posterior prioriza los vehículos sin usar. Las estrategias
`robin_round`, `distance`, `load`, `hybrid` y `future_hybrid` solo consideran los
camiones que todavía tienen viajes disponibles. Los pesos híbridos reparten
trabajo, no cambian el objetivo de distancia del solver.

La matriz sigue en `distanceMatrix`, fuera de `parameters`, porque describe el
problema. Es opcional en este endpoint. Si se proporciona, debe ser cuadrada,
finita, no negativa y con diagonal cero. `matrixIndex` debe identificar cada fila
una sola vez. Una matriz incorrecta devuelve HTTP 422 y no activa una distancia
alternativa. Si falta o es `null`, se calcula distancia euclídea sobre coordenadas
cartesianas. No se interpretan grados geográficos como kilómetros.

PyVRP recibe distancias enteras tras multiplicarlas por 1000, tanto si la matriz
se proporciona como si se calcula. Así conserva tres decimales de la unidad de
entrada durante la búsqueda. Las distancias de la respuesta se reconstruyen
sobre la matriz original, en su unidad original y sin suponer que sea simétrica.
`totalCost` es la suma de `totalDistance` de los viajes devueltos.

Cada ruta incorpora `tripIndex`, que empieza en 1 por vehículo. `solverDetails`
incluye el modo autorizado (`SINGLE_TRIP` o `MULTI_TRIP`), máximo por vehículo,
viajes totales, vehículos utilizados y viajes adicionales. Un camión con tres
viajes aporta dos adicionales, aunque otro camión quede sin usar.

El resultado declara `objective: "DISTANCE"`, `distanceWeight: 1`, `timeCost: 0`
y `departureCost: 0`. Los dos últimos campos reservan los componentes futuros de
tiempo y salida. Son informativos y todavía no son parámetros configurables.
`scheduleValidated: false` indica que no se calculan horarios, recargas ni duración
acumulada de jornada. Por ello se rechazan `maxDuration` y `serviceDuration`
distintos de cero, en lugar de ignorarlos. Pueden omitirse o ser `null` o cero.

Si no se encuentra una solución completa dentro del tiempo y las restricciones,
se devuelve `isFeasible: false`, `totalCost: null`, `routes: []` y el motivo en
`solverDetails.failureReason`. Agotar la búsqueda no demuestra que el problema sea
imposible. No se devuelven rutas parciales como una solución completada.

### `domain/models.py`

Define los contratos de entrada y salida mediante Pydantic:

- `NodeDto`: identificador y coordenadas `x`, `y`. Su distancia euclídea se redondea al entero más cercano.
- `CustomerDto`: nodo de cliente con `demand`. Puede incluir `internal_nodes` cuando representa un Súper-Cliente.
- `DepotDto`: especialización de `NodeDto` para depósitos.
- `VehicleDto`: identificador y capacidad.
- `RoutingRequest`: instancia, depósitos, clientes y vehículos.
- `RouteDto`: vehículo, paradas, distancia y carga.
- `RoutingResponse`: solver utilizado, tiempo, distancia total, factibilidad, rutas globales, rutas internas, qubits y motivo de fallback.

`RoutingResponse.internal_routes` contiene rutas PyVRP internas de los Súper-Clientes cuando existen. `RoutingResponse.total_distance` representa la distancia de las rutas globales. El coste combinado se expone en las métricas como `total_cost`. Swagger UI muestra los schemas de entrada utilizados por la API. Las respuestas se mantienen como diccionarios para no duplicar DTOs de salida y conservar flexible la estructura de métricas.

### `domain/spatial_partitioner.py`

Implementa el preprocesamiento espacial:

- `get_vehicles_by_depot()` reparte los vehículos por bloques uniformes entre depósitos. La flota Cordeau se interpreta como homogénea dentro de cada depósito (distintas regiones pueden tener capacidades distintas).
- `apply_capacitated_voronoi()` formula y resuelve un GAP/MILP con PuLP. Cada cliente se asigna a un depósito y la demanda asignada no puede superar la capacidad agregada de la flota de ese depósito.
- `generate_super_clients()` aplica `AgglomerativeClustering` con enlace `complete`. Agrupa clientes cercanos, calcula el centroide y suma sus demandas. Conserva los clientes originales en `internal_nodes`.

El particionador no contiene una función para construir subrequests, sino que esa construcción se hace en `services/evaluation_service.py` mediante `prepare_quantum_subrequests()`.

### `services/graph_analyzer.py`

Calcula un filtro global y rápido sobre el grafo de la instancia inicial, antes del GAP. No intenta predecir directamente el número final de Súper-Clientes ni el número de qubits.

Features actuales, significado y finalidad:

- `num_customers` (`N`), `num_depots` y `total_vehicles` (`V`): número de clientes, depósitos y vehículos de la instancia global. Describen su escala antes del particionamiento.
- `total_demand` (`D_total`) y `total_capacity` (`C_total`): suma de todas las demandas y suma de las capacidades de los vehículos. Permiten detectar si la carga puede caber en la flota.
- `avg_capacity` (`Q_medio = C_total / V`) y `avg_demand` (`D_medio = D_total / N`): capacidad y demanda medias. Normalizan las métricas de carga para que sean comparables entre instancias.
- `max_demand_ratio` (`max_j(q_j) / Q_medio`): demanda individual máxima relativa a la capacidad media. Señala clientes que por sí mismos dejan poco margen para agrupar.
- `demand_granularity` (`G = D_medio / Q_medio`): fracción de la capacidad media que ocupa un cliente medio. Se busca un valor bajo para que la agrupación de clientes tenga margen de capacidad.
- `capacity_tightness` (`T_c = D_total / C_total`): utilización global de la flota. Se busca un valor alejado de uno, ya que una flota casi saturada tolera mal la pérdida de granularidad.
- `avg_dist_to_depot`: media, para todos los clientes, de la distancia a su depósito más cercano. Describe la escala espacial global de la instancia.
- `avg_nearest_neighbor_dist`: media de la distancia de cada cliente a su vecino más cercano. Describe la compactación espacial local.
- `spatial_compressibility_index` (`S = avg_nearest_neighbor_dist / avg_dist_to_depot`): compara la escala local con la global. Se busca un valor bajo: clientes cercanos entre sí respecto de su distancia al depósito hacen más representativo un centroide.

La viabilidad inicial exige actualmente:

```text
demand_granularity < 0.6
capacity_tightness <= 0.90
spatial_compressibility_index <= 1.2
```

Los umbrales son heurísticos, no condiciones matemáticas de optimalidad. `G < 0.6` descarta una demanda media que, al reunir aproximadamente dos clientes, agotaría o superaría la capacidad media. `T_c <= 0.90` reserva al menos un 10 % de holgura global para que GAP y la agrupación no dependan de un empaquetado perfecto. `S <= 1.2` descarta configuraciones en las que los vecinos locales no son claramente más próximos que la escala depósito-cliente y, por tanto, un centroide puede distorsionar la ruta. El filtro es una criba, no una demostración de que el QUBO final sea viable: la validación de capacidad de los Súper-Clientes y del número de qubits se realiza después, para cada candidato.

### `services/evaluation_service.py`

Es la capa principal de orquestación y la referencia común para API y runner local.

Funciones principales:

- `_homogeneous_capacity()`: verifica que los vehículos de un subproblema tengan la misma capacidad.
- `calculate_cluster_k()`: calcula por región:

  $$
  K_i = max\left(1,\left\lceil\frac{D_i}{Q_i}\right\rceil\right)
  $$

  Es una cota de capacidad regional, no el número global de Súper-Clientes ni una predicción exacta del QUBO. La derivación de `D_i`, `Q_i` y `K_i` está en el informe técnico.

- `validate_superclients()`: comprueba que ningún Súper-Cliente supere `Q` y que la demanda regional quepa en la flota asignada.
- `prepare_quantum_subrequests()`: ejecuta GAP una vez, calcula `K_i`, aplica el offset, crea los Súper-Clientes, construye las subrequests y las valida.
- `calculate_internal_results()`: resuelve con PyVRP el TSP interno de cada Súper-Cliente y devuelve coste y rutas internas.
- `solve_best_quantum_candidate()`: explora `K_min`, `K_min+1`, `K_min+2` y `K_min+3`, evita repetir una configuración idéntica y elige el menor coste global + coste interno entre candidatos válidos.
- `evaluate_initial_quantum_viability()`: aplica el filtro de `GraphAnalyzer`.
- `evaluate_quantum_metrics()`: calcula coste global, coste interno, coste total, referencia clásica, degradación, cargas, capacidades, violaciones y factibilidad.
- `solve_with_classical()`: resuelve directamente la instancia global con PyVRP cuando se solicita el backend `classical`.
- `solve_with_classical_fallback()`: resuelve con PyVRP y conserva rutas, distancia, carga, tiempo y motivo del fallback cuántico.

Los imports de Qiskit y D-Wave se realizan cuando se solicita el backend correspondiente. Esto evita que el modo clásico o el arranque de la ayuda del runner dependan de extensiones nativas de D-Wave.

### `solvers/pyvrp_solver.py`

Contiene la referencia clasica:

- `solve_with_pyvrp()` construye un modelo PyVRP, añade depósitos, clientes, flota y matriz de distancias, ejecuta HGS-ADC con una semilla fija y devuelve una `RoutingResponse` completa.
- `solve_internal_tsp()` crea un depósito artificial en el centroide de un Súper-Cliente y resuelve sus clientes internos con un vehículo de capacidad muy grande. Devuelve la ruta interna y su coste.

PyVRP se utiliza para cuatro objetivos: backend clásico directo de la API, referencia clásica, fallback operativo y estimación del coste interno perdido por el coarse-graining.

### `solvers/quantum_qiskit.py`

Formula cada subrequest como un `QuadraticProgram` y lo resuelve con QAOA:

- `x[origen, destino, vehiculo]`: arco utilizado.
- `y[cliente, vehiculo]`: asignacion de un cliente a un vehiculo.
- `u[cliente, vehiculo]`: carga acumulada, solo si se activa MTZ.

Restricciones principales:

- Cada cliente se asigna a un solo vehículo.
- Conservación de entrada y salida en cada cliente.
- Capacidad agregada por vehículo cuando MTZ está desactivado.
- Equilibrio de salida y regreso al depósito.
- Restricciones MTZ opcionales para eliminar subtours.

El modelo restringido se convierte a QUBO con `QuadraticProgramToQubo`. Un QUBO expresa las restricciones como términos de penalización dentro de una única función objetivo cuadrática sin restricciones. Durante esta transformación se crean, cuando son necesarias, variables auxiliares para representar las restricciones. El número medido con `qubo.get_num_vars()` es el número de variables binarias lógicas, incluidas esas auxiliares, y se usa como estimación de qubits.

Por defecto `include_mtz=False` para reducir el QUBO. En ese modo la reconstrucción sigue los arcos desde el depósito y comprueba que los clientes alcanzables coincidan con los clientes asignados por `y`. Esto detecta soluciones con clientes en ciclos desconectados, aunque no sustituye a MTZ como restricción formal.

El simulador actual es `StatevectorSampler`. QAOA utiliza `COBYLA(maxiter=20)` y `reps=1`, una configuración experimental de bajo nivel de profundidad, no una garantía de rendimiento óptimo. `quantum_qiskit_proc_isolate.py` ejecuta este cálculo en un intérprete independiente, recibe la entrada por JSON y permite terminarlo si vence el límite temporal.

### `solvers/quantum_dwave.py`

Formula cada subrequest como un `ConstrainedQuadraticModel` con:

- Variables binarias de arcos y asignaciones.
- Variables enteras de carga.
- Restricciones de visita, flujo, capacidad y MTZ.
- Muestreo mediante `LeapHybridCQMSampler`.

El solver requiere credenciales, acceso de red, cuota y disponibilidad de D-Wave Leap. No es un simulador local equivalente al de Qiskit.

### `utils/parser.py`

`CordeauParser.parse_instance()` transforma un archivo local Cordeau MD-CVRP en un `RoutingRequest`.

Interpreta la cabecera como:

```text
type m n t
```

donde `m` es el número de vehículos por depósito, `n` el número de clientes y `t` el número de depósitos. Cada línea de depósito aporta su capacidad `Q`. Se crean `m` vehículos con esa capacidad para cada depósito.

En produccion FastAPI recibe JSON estructurado, por lo que este parser es una herramienta local de evaluacion y pruebas.

### `tests/run_local_evaluation.py`

Es el runner reproducible equivalente al endpoint para archivos Cordeau.

- Carga una instancia o todas las `pNN`.
- Aplica el filtro inicial.
- Explora candidatos y backend usando `evaluation_service`.
- Puede resolver directamente con PyVRP mediante `--backend classical`.
- Calcula métricas con `evaluate_quantum_metrics`, igual que `main.py`.
- Muestra un resumen o un documento JSON por instancia.

El modo `classical` reproduce el backend clásico directo de la API y resuelve la instancia global sin aplicar el flujo de preparación cuántica.

### Tests

- `tests/test_parser.py`: dimensiones, capacidades, coordenadas y demanda del parser.
- `tests/test_evaluate_quantum_viability.py`: granularidad, tension de capacidad y compresibilidad espacial.
- `tests/test_api.py`: estado del servicio, análisis sin solver, backend clásico directo, timeout de Qiskit y validación de backend.
- `tests/test_quantum_preparation.py`: reparto de vehículos, `K` regional, GAP, nombres de subrequests y validación de Súper-Clientes.
- `tests/data/`: instancias Cordeau y casos sinteticos pequenos.

## 3. Instalación

Se recomienda utilizar un entorno virtual independiente.

El proyecto utiliza Python `3.14.7`, declarado en `.python-version`. Herramientas
como `uv` o `pyenv` pueden usar ese archivo para seleccionar el intérprete. Si
usas otro método, asegúrate de que `python --version` indique `3.14.7` antes de
crear el entorno virtual. `requirements.txt` fija las dependencias, pero no la
versión de Python.

### Windows PowerShell

```powershell
cd "<ruta>\quantum-solver-ms"
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
```

Si PowerShell impide activar scripts, puede ejecutarse directamente el interprete del entorno:

```powershell
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
```

Dependencias principales fijadas actualmente:

- FastAPI `0.141.1` y Uvicorn `0.52.4`.
- Pydantic `2.13.5`.
- NumPy `2.5.2`, PuLP `3.3.2` y scikit-learn `1.9.0`.
- PyVRP `0.14.0`.
- Qiskit `2.5.2`, Qiskit Algorithms `0.4.0` y Qiskit Optimization `0.7.0`.
- Dimod `0.12.22` y D-Wave System `1.36.0`.

Las versiones exactas pueden cambiar en el futuro. Para reproducibilidad, la referencia operativa es siempre el `requirements.txt` del repositorio.

## 4. Uso mediante API

### Arrancar FastAPI

Desde la raiz del proyecto:

```powershell
.\.venv\Scripts\Activate.ps1
uvicorn api.main:app --reload
```

Por defecto queda disponible en:

```text
http://127.0.0.1:8000
```

Documentacion interactiva:

```text
http://127.0.0.1:8000/docs
```

### Estado y análisis sin solver

El endpoint de estado no requiere cuerpo y sirve para comprobaciones de disponibilidad:

```powershell
Invoke-RestMethod -Uri "http://127.0.0.1:8000/health"
```

`POST /api/v1/quantum-routing/analyze` recibe el mismo JSON que `solve`, pero solo devuelve las features de `GraphAnalyzer` y el resultado del filtro inicial. Permite a un orquestador decidir si envía después la instancia a un backend cuántico sin iniciar QAOA, D-Wave ni PyVRP.

### Petición HTTP

Endpoint:

```text
POST /api/v1/quantum-routing/solve
```

Cabeceras opcionales:

```text
X-Target-Quantum-Backend: qiskit
X-Quantum-Timeout-Seconds: 30
X-Classical-Time-Limit: 60
X-Max-Qubits: 24
X-Include-Mtz: false
```

Esta cabecera HTTP es un mecanismo de selección de backend. FastAPI convierte
su nombre en el parámetro `x_target_quantum_backend` de `main.py`. Si no se
envía, su valor predeterminado es `qiskit`. No es una credencial, no selecciona
un procesador de IBM Quantum y no establece una conexión con IBM Quantum.

Valores previstos:

- `qiskit`: formula un QUBO y ejecuta QAOA en el simulador local de Qiskit.
- `dwave`: formula un CQM y solicita el solver híbrido D-Wave Leap.
- `classical`: resuelve directamente la instancia MD-CVRP global con PyVRP, sin aplicar `GraphAnalyzer`, GAP ni Súper-Clientes.

Cualquier otro valor devuelve HTTP 422 con los valores admitidos.

El límite temporal solo se aplica a Qiskit. La ejecución se aísla en un proceso para que una llamada lenta de QAOA o SciPy no bloquee el proceso de la API. Si vence, se termina ese proceso y se descarta el candidato. Si todos los candidatos vencen o fallan, se usa PyVRP con `fallback_reason` indicando el timeout. `SparseEfficiencyWarning` es un aviso de rendimiento emitido internamente por SciPy, no una excepción ni la causa directa del bloqueo.

`X-Classical-Time-Limit` limita cada ejecución de PyVRP, tanto en el backend `classical` como en los fallbacks y en la referencia usada para las métricas. Admite enteros de `1` a `300` segundos y su valor predeterminado es `60`. `X-Max-Qubits` admite enteros de `1` a `24` y fija el máximo de variables binarias lógicas del QUBO de cada subproblema Qiskit. `X-Include-Mtz` admite `true` o `false`, está desactivada por defecto y solo afecta a Qiskit. Las dos últimas cabeceras no cambian la formulación CQM de D-Wave ni el backend `classical`.

### JSON de entrada

```json
{
  "instance_name": "demo",
  "depots": [
    {
      "id": "D1",
      "x": 0.0,
      "y": 0.0
    }
  ],
  "customers": [
    {
      "id": "C1",
      "x": 10.0,
      "y": 10.0,
      "demand": 20
    },
    {
      "id": "C2",
      "x": 12.0,
      "y": 10.0,
      "demand": 25
    }
  ],
  "vehicles": [
    {
      "id": "V1",
      "capacity": 100
    }
  ]
}
```

Campos:

- `instance_name`: nombre usado para trazabilidad.
- `depots`: depósitos con `id`, `x` e `y`.
- `customers`: clientes con `id`, `x`, `y` y `demand`. `internal_nodes` es opcional y se reserva para Súper-Clientes internos.
- `vehicles`: vehículos con `id` y `capacity`.

En el formato Cordeau del proyecto, la flota se considera homogénea dentro de cada depósito. Los depósitos pueden tener capacidades diferentes.

Ejemplo con PowerShell:

```powershell
$body = @'
{
  "instance_name": "demo",
  "depots": [{"id": "D1", "x": 0, "y": 0}],
  "customers": [
    {"id": "C1", "x": 10, "y": 10, "demand": 20},
    {"id": "C2", "x": 12, "y": 10, "demand": 25}
  ],
  "vehicles": [{"id": "V1", "capacity": 100}]
}
'@
Invoke-RestMethod `
  -Uri "http://127.0.0.1:8000/api/v1/quantum-routing/solve" `
  -Method Post `
  -Headers @{
    "X-Target-Quantum-Backend" = "qiskit"
    "X-Quantum-Timeout-Seconds" = "30"
    "X-Classical-Time-Limit" = "60"
    "X-Max-Qubits" = "24"
    "X-Include-Mtz" = "false"
  } `
  -ContentType "application/json" `
  -Body $body
```

Para solicitar PyVRP como backend explícito, se usa el mismo cuerpo y se cambia la cabecera:

```powershell
Invoke-RestMethod `
  -Uri "http://127.0.0.1:8000/api/v1/quantum-routing/solve" `
  -Method Post `
  -Headers @{"X-Target-Quantum-Backend" = "classical"} `
  -ContentType "application/json" `
  -Body $body
```

La respuesta de esta llamada tendrá `solver_used: "PyVRP"`, `fallback_reason: null`, `metrics.backend_requested: "classical"` y `metrics.preparation.execution_mode: "classical_direct"`.

### Respuesta

La API devuelve un objeto con `routing_response` y `metrics`. La llamada de
PowerShell anterior es una petición real al endpoint: si Uvicorn está en
ejecución y las dependencias están instaladas, recibe y procesa ese JSON. El
siguiente documento es un ejemplo ilustrativo de la estructura y de valores
posibles. No es una salida pregrabada ni se garantizan sus distancias, qubits,
rutas o solver. En una ejecución real puede devolverse una solución Qiskit o
un fallback PyVRP con su correspondiente `fallback_reason`.

```json
{
  "routing_response": {
    "instance_name": "Delivera_Quantum_Qiskit_Global",
    "solver_used": "IBM Quantum / BSC Simulator",
    "execution_time_ms": 123.4,
    "total_distance": 32.0,
    "is_feasible": true,
    "routes": [
      {
        "vehicle_id": "V1",
        "stops": ["D1", "C1", "C2", "D1"],
        "distance": 32.0,
        "load": 45
      }
    ],
    "internal_routes": [],
    "qubits": 18,
    "fallback_reason": null
  },
  "metrics": {
    "quantum_total_distance": 32.0,
    "internal_cost": 0.0,
    "total_cost": 32.0,
    "classical_reference_distance": 32.0,
    "relative_degradation": 0.0,
    "route_count": 1,
    "route_loads": [45],
    "capacities": {"V1": 100},
    "capacity_violations": [],
    "is_feasible": true,
    "qubits": 18,
    "preparation": {},
    "backend_requested": "qiskit"
  }
}
```

`fallback_reason` identifica por qué se abandonó la ruta cuántica. Una respuesta con `solver_used` igual a `PyVRP classical fallback` no representa una ejecución QAOA exitosa. Representa una derivación controlada al solver clásico. En cambio, `solver_used` igual a `PyVRP` y `execution_mode` igual a `classical_direct` identifican una solicitud clásica explícita. `routes` contiene las rutas realmente decodificadas. Solo estará vacía en casos sin clientes o cuando la respuesta concreta no contenga rutas.

## 5. Evaluación local con Cordeau

El runner local permite evaluar las instancias de `tests/data` sin levantar FastAPI.

### Una instancia

```powershell
python tests/run_local_evaluation.py --instance p01
```

El valor de `--instance` es el nombre de un archivo dentro de `tests/data`, por ejemplo:

```powershell
python tests/run_local_evaluation.py --instance quantum_demo.txt
python tests/run_local_evaluation.py --instance quantum_small.txt
```

### Todas las instancias benchmark

```powershell
python tests/run_local_evaluation.py --all --backend classical
```

`--all` procesa los archivos cuyo nombre coincide con `pNN`. Si una instancia falla, el runner la informa y continúa con las demás.

### Argumentos disponibles

| Argumento | Valores | Funcion |
|---|---|---|
| `--instance` | nombre de archivo | Evalúa una instancia concreta de `tests/data`. |
| `--all` | bandera | Evalúa todos los archivos `pNN`, es incompatible con `--instance`. |
| `--backend` | `qiskit`, `dwave`, `classical` | Selecciona solver, por defecto `qiskit`. |
| `--max-qubits` | entero, por defecto `24` | Límite de variables binarias del QUBO por subproblema. |
| `--quantum-time-limit` | decimal, por defecto `30` | Límite en segundos de cada candidato Qiskit antes de usar el fallback clásico. |
| `--classical-time-limit` | entero, por defecto `60` | Tiempo máximo de cada ejecución PyVRP de referencia/fallback. |
| `--include-mtz` | bandera | Activa las restricciones MTZ en la formulación Qiskit. Aumenta el QUBO. |
| `--json` | bandera | Imprime el resultado completo como JSON en vez del resumen de consola. |

Ejemplos:

```powershell
python tests/run_local_evaluation.py --instance p01 --backend classical
python tests/run_local_evaluation.py --instance quantum_demo.txt --backend qiskit --json
python tests/run_local_evaluation.py --instance quantum_demo.txt --backend qiskit --quantum-time-limit 30
python tests/run_local_evaluation.py --instance quantum_small.txt --backend qiskit --include-mtz --max-qubits 64
python tests/run_local_evaluation.py --all --backend classical --classical-time-limit 60 --json
```

Para guardar el JSON en un archivo, la redirección la hace PowerShell. `_jsonable()` solo prepara un objeto Python serializable:

```powershell
python tests/run_local_evaluation.py --instance p01 --backend classical --json > resultado_p01.json
```

## 6. Pruebas

Ejecutar desde la raíz:

```powershell
python -m unittest discover -s tests -p "test_*.py"
```

Pruebas individuales:

```powershell
python -m unittest tests.test_parser -v
python -m unittest tests.test_evaluate_quantum_viability -v
python -m unittest tests.test_quantum_preparation -v
```

En algunos entornos Windows, SciPy/scikit-learn o extensiones nativas de D-Wave pueden fallar al cargar DLL por políticas del sistema. Ese error ocurre durante la importación nativa, antes de ejecutar la lógica de estas pruebas. El runner usa imports diferidos para que el modo clásico y la ayuda puedan arrancar sin D-Wave.

#### DLL bloqueada por Smart App Control en Windows 11

En un equipo personal, si el error de DLL se debe a **Smart App Control** de Windows 11, puede desactivarse desde:

1. Menú Inicio → **Seguridad de Windows**.
2. **Control de aplicaciones y navegador**.
3. **Configuración de Smart App Control**.
4. Cambiar **Evaluación** o **Activado** a **Desactivado**.

Esta medida reduce la protección del sistema frente a aplicaciones no confiables. Debe aplicarse únicamente cuando se haya identificado Smart App Control como causa del bloqueo, no como solución general para cualquier error de DLL.

## 7. Métricas y coste

### Distancias

`NodeDto.euclidean_distance()` utiliza distancia euclídea redondeada:

$$
d(a,b)=round\left(\sqrt{(x_a-x_b)^2+(y_a-y_b)^2}\right)
$$

La misma convención se usa en el parser, PyVRP, GAP y las formulaciones cuánticas.

### Filtro inicial

Granularidad media:

$$
G=\frac{D_{total}/N_{clientes}}{Q_{medio}}
$$

Tensión de capacidad:

$$
T_c=\frac{D_{total}}{C_{total}}
$$

Compresibilidad espacial:

$$
S=\frac{\text{distancia media al vecino más cercano}}
{\text{distancia media al depósito más cercano}}
$$

Umbrales actuales:

```text
G < 0.6
Tc <= 0.90
S <= 1.2
```

Después del GAP se estima, para cada región, el número mínimo de Súper-Clientes `K_i` a partir de su demanda total `D_i` y de la capacidad por vehículo `Q_i`. Esta cota guía la exploración de `K_i` a `K_i+3`, pero no garantiza por sí sola que el clustering geométrico respete la capacidad. Por ello, cada candidato se valida después de agrupar. La derivación completa y la definición de `D_i`, `Q_i`, `q_ij`, `a_i` y `K_i` se encuentran en el [informe técnico](INFORME_TECNICO_REDUCCION_QUBO_Y_QUBITS.md).

### Coste total

El candidato se compara con:

$$
C_{total}=C_{global}+C_{interno}
$$

Donde:

- `C_global`: distancia de las rutas entre depósito y Súper-Clientes obtenida por Qiskit o D-Wave.
- `C_interno`: suma de los TSP internos de cada Súper-Cliente resueltos con PyVRP.
- `C_total`: coste usado para seleccionar el mejor candidato.

El coste interno no debe ignorarse: agrupar clientes reduce el grafo, pero la ruta real debe visitar los clientes originales. El centroide del Súper-Cliente se utiliza como depósito artificial del TSP interno.

`capacity_violations` es una comprobación defensiva de las rutas devueltas. En una respuesta factible correcta debería ser una lista vacía.

## 8. Modelo Qiskit y estimación de qubits

El número de Súper-Clientes no equivale al número de qubits. En esta sección solo se resume el criterio utilizado por el proyecto:

- Las variables de arco crecen como $O(kn^2)$.
- Las variables de asignación crecen como $O(nk)$.
- Las variables enteras de carga y las restricciones MTZ añaden bits y restricciones adicionales.
- `QuadraticProgramToQubo` transforma las restricciones en penalizaciones de la función objetivo y puede crear variables auxiliares.
- El tamaño que debe utilizarse para decidir si un candidato cabe es `qubo.get_num_vars()`.
- El límite operativo del runner es `24` qubits por subproblema.

Las restricciones MTZ son opcionales porque eliminan subtours de forma formal, pero aumentan el número de variables y restricciones del QUBO. Con MTZ desactivado, la decodificación posterior rechaza soluciones incoherentes, aunque no sustituye a la restricción matemática.

La derivación completa, las fórmulas, la justificación cuantitativa de MTZ, las mediciones reproducibles y el análisis de `N_x`, `N_y`, `N_u`, variables slack y variables auxiliares se encuentran en el [informe técnico](INFORME_TECNICO_REDUCCION_QUBO_Y_QUBITS.md).

## 9. Resultados y objetivos cumplidos

El proyecto consigue:

1. Recibir un problema estructurado mediante API.
2. Leer benchmarks Cordeau localmente.
3. Analizar una instancia antes de intentar el solver cuántico.
4. Asignar clientes a depósitos con GAP/MILP.
5. Respetar la capacidad específica de la flota de cada depósito.
6. Generar Súper-Clientes y conservar sus clientes internos.
7. Explorar varios valores regionales de `K`.
8. Medir el número real de variables del QUBO.
9. Activar o desactivar MTZ como compromiso entre exactitud y tamaño.
10. Calcular el coste interno con PyVRP.
11. Devolver rutas globales e internas en una respuesta estructurada.
12. Derivar a PyVRP cuando la instancia, el QUBO o el backend no son viables.
13. Ejecutar pruebas unitarias del parser, filtros y preparación.
14. Comparar resultados cuánticos o híbridos con una referencia clásica.

La principal aportación del proyecto es hacer visible el coste real de la formulación. El particionamiento espacial reduce clientes por subproblema, pero no convierte automáticamente `K` Súper-Clientes en `K` qubits.

## 10. Obstáculos y limitaciones actuales

### Tamaño del QUBO

Las variables de arco crecen como `O(k n^2)` y se multiplican por el número de vehículos. Las variables de asignación, cargas y auxiliares elevan aún más el tamaño.

El límite real no depende solo de clientes: depende también de vehículos, capacidad, restricciones, MTZ, codificación y convertidor.

### Simulación local

`StatevectorSampler` es un simulador clásico. No representa una ejecución en hardware cuántico real. Además, la optimización QAOA puede ser costosa incluso en modelos pequeños por las evaluaciones repetidas del circuito y del optimizador COBYLA.

### D-Wave

El camino D-Wave necesita credenciales, red, cuota y disponibilidad de Leap. No es un backend local autocontenido.

### Agrupación geométrica

Agglomerative clustering optimiza proximidad geométrica, no empaquetado de demanda. Dos clientes cercanos pueden producir un Súper-Cliente con demanda superior a `Q`. Por eso, la validación posterior puede rechazar una región aunque su `K_min` teórico parezca suficiente.

### Referencia clásica

PyVRP proporciona una referencia heurística fuerte, pero no debe llamarse automáticamente óptimo matemático. El resultado depende del tiempo límite y de la configuración del modelo.

### Homogeneidad y múltiples depósitos

El parser crea bloques de vehículos homogéneos por depósito. La formulación cuántica trabaja sobre subrequests de un solo depósito. La referencia global clásica de `evaluate_quantum_metrics` usa el `RoutingRequest` recibido. Para estudios estrictos de benchmarks Cordeau conviene conservar también comparaciones por región.

### Factibilidad y subtours

Sin MTZ, el QUBO puede admitir ciclos de clientes desconectados del depósito. La decodificación los detecta indirectamente al comparar la ruta alcanzable con las asignaciones `y`, pero una validación posterior no equivale a imponer la restricción en el modelo.

## 11. Ventajas y desventajas frente a métodos clásicos

### Ventajas

- Permite experimentar con una formulación cuántica de un problema de rutas real.
- Separa asignación espacial, compresión, ordenación y validación.
- Mide qubits y coste interno en lugar de ocultar la pérdida por coarse-graining.
- Ofrece las rutas Qiskit, D-Wave y PyVRP.
- Tiene fallback clásico para mantener una respuesta operativa.
- Permite reproducir las pruebas con instancias Cordeau.

### Desventajas

- La formulación QUBO escala peor que los solvers clásicos maduros para CVRP general.
- El coste de convertir restricciones a QUBO puede dominar el tamaño.
- MTZ mejora la factibilidad, pero aumenta qubits.
- Quitar MTZ reduce el modelo, pero obliga a detectar soluciones inválidas después.
- QAOA local puede ser más lento y menos fiable que PyVRP para estos tamaños.
- D-Wave introduce dependencia externa, coste y latencia.
- El clustering puede degradar la solución y requiere resolver el coste interno.
- Los valores de QAOA, el número de shots y los optimizadores introducen variabilidad experimental.

## 12. Trabajo futuro

### Corto plazo

- Incorporar comprobaciones de disponibilidad específicas para dependencias locales y para el acceso remoto a D-Wave.
- Registrar `raw_variables`, `qubo_variables`, número de restricciones y tiempo de conversión. Así se distingue cuánto crece el modelo al definirlo de cuánto añade la conversión a QUBO.
- Devolver el número de qubits estimado antes de iniciar QAOA.
- Parametrizar por backend el límite de qubits, shots, profundidad y tiempo.
- Mejorar el clustering para imponer capacidad durante la agrupación, no solo después. Esto evitaría generar y rechazar después un Súper-Cliente cuya demanda excede `Q`.
- Añadir pruebas de rutas globales, rutas internas, fallback y serialización JSON.
- Reparar el entorno Windows cuando bloquee extensiones nativas de SciPy o D-Wave.
- Añadir un archivo de configuración para no duplicar valores como `24`, `3` y `60` entre API y runner. Sería una única fuente para el límite de qubits, el máximo offset y el tiempo clásico.

### Medio plazo

- Formular TSP o routing posicional de un solo vehículo como experimento QAOA más compacto.
- Separar la asignación `y` del orden de rutas y completar estas con PyVRP, OR-Tools o 2-opt.
- Estudiar penalizaciones y codificaciones de capacidad más compactas.
- Comparar tiempo, distancia, factibilidad, qubits, profundidad y número de ejecuciones.
- Probar solvers clásicos exactos o CP-SAT en instancias pequeñas para una referencia mejor que una sola heurística.
- Añadir circuit cutting o quantum knitting solo con una métrica que justifique el coste de reconstrucción.

### Largo plazo

Si la tecnología permite más qubits lógicos, mejores conectividades, menor ruido y optimizadores más robustos, se podrían reintroducir restricciones completas de capacidad y MTZ para subproblemas más grandes. Aun así, sería razonable mantener una arquitectura híbrida. Los métodos clásicos se ocuparían de la asignación, el particionamiento, la capacidad y la validación. Los métodos cuánticos se reservarían para ordenaciones o subproblemas donde exista una ventaja medible.

## 13. Referencias del repositorio

- `INFORME_TECNICO_REDUCCION_QUBO_Y_QUBITS.md`: detalle de la reducción mediante `K_i`, el tamaño del QUBO, la estimación de qubits y el efecto de MTZ.
- `tests/data/`: entradas en formato Cordeau para reproducción local.
- `requirements.txt`: versiones instalables utilizadas por el proyecto.

### Carpeta `experimentacion/`

La carpeta hermana `../experimentacion/` se creó para medir el empeoramiento que introduce el preprocesamiento (GAP y Súper-Clientes) antes de usar una formulación cuántica. Su script `evaluation.py` compara el coste de PyVRP aplicado directamente a la instancia MD-CVRP global con el coste compuesto por PyVRP tras el preprocesamiento: rutas entre depósitos y Súper-Clientes más los TSP internos. El resultado es el gap de coste atribuible a la compresión, no una comparación entre PyVRP y Qiskit.

## 14. Resumen final

Este proyecto no debe interpretarse como un microservicio cuántico que resuelve actualmente todos los problemas MD-CVRP representados por las instancias en formato Cordeau. Es una plataforma experimental y un microservicio híbrido que:

1. Analiza la instancia.
2. Divide clientes por depósito.
3. Comprime espacialmente cuando es posible.
4. Mide el QUBO real.
5. Intenta Qiskit o D-Wave en subproblemas.
6. Calcula el coste interno de la compresión.
7. Valida las rutas.
8. Conserva una salida clásica cuando la vía cuántica no es viable.
