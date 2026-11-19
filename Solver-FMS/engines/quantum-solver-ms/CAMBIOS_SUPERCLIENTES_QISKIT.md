# Cambios de Súper-Clientes y Qiskit

7 de noviembre de 2026 · Rama `feature/quantum-pyvrp-core` · Commit de implementación `6c8a73e`

## Cambios principales

- **Rutas completas.** Cada Súper-Cliente se sustituye por sus clientes originales, conservando el orden de los grupos, el vehículo y el depósito. PyVRP ordena cada grupo entre el último cliente visitado y el siguiente nodo reducido. Si el siguiente nodo es otro grupo, su centroide orienta la salida, pero no aparece en la ruta final. La búsqueda local tiene un presupuesto predeterminado de 0.1 s por grupo.
- **Distancia y respuesta.** `routes` contiene las entregas originales y `total_distance` suma las rutas completas. `internal_routes` queda vacío. `metrics.total_cost` ya incluye los tramos internos, por lo que no deben sumarse otra vez. El flujo nativo devuelve las entregas en las rutas, sin necesitar zonas ficticias para representarlas.
- **Validación.** Se comprueban cobertura exacta de clientes, capacidad, pertenencia del vehículo al depósito y coherencia de los grupos. Un candidato incorrecto se descarta. Si no queda ninguno válido, se utiliza el fallback clásico. El flujo cuántico sigue admitiendo un solo viaje por vehículo.
- **Qiskit.** Se transpila el circuito a puertas básicas antes de simularlo, evitando la exponencial matricial costosa observada. El proceso aislado comunica el tamaño real del QUBO para ampliar el presupuesto automático, hasta 300 s por candidato. Un timeout explícito se respeta. Esto no establece un límite global para toda la petición.

## Archivos creados

Rutas relativas a `Solver-FMS/engines/quantum-solver-ms/`.

| Archivo | Qué se ha creado |
|---|---|
| `services/route_expansion_service.py` | Expande los grupos, valida las rutas y recalcula las distancias sobre los clientes originales. |
| `solvers/pyvrp_path_solver.py` | Resuelve el recorrido interno con extremos distintos y evita ejecutar PyVRP para grupos de cero o un cliente. |
| `tests/test_route_expansion.py` | Comprueba recorridos abiertos, grupos consecutivos, cobertura, capacidad, flotas y selección por coste expandido. |
| `tests/test_quantum_qiskit.py` | Comprueba decodificación, transpilación, rechazo de muestras incorrectas y control del proceso y del timeout. |
| `tests/run_qiskit_validation.py` | Permite resolver un caso manual con Qiskit y comprobar la expansión, sin fallback global. |
| `tests/qiskit_results.json` | Conserva los resultados medidos antes y después del ajuste del simulador. |
| `tests/data/quantum_two_clients.txt` | Añade una instancia Cordeau de dos clientes resuelta directamente con Qiskit usando semilla 7. |

## Archivos modificados

| Archivo | Qué se ha modificado |
|---|---|
| `services/evaluation_service.py` | Expande cada candidato antes de compararlo y evita sumar dos veces el coste interno. |
| `solvers/pyvrp_solver.py` | Elimina el antiguo `solve_internal_tsp`, sustituido por el recorrido abierto. |
| `solvers/quantum_qiskit.py` | Añade transpilación, opciones de QAOA, presupuesto adaptativo y validación del resultado y de los clientes visitados. |
| `solvers/quantum_qiskit_proc_isolate.py` | Comunica los qubits del modelo y el resultado mediante mensajes JSON al proceso principal. |
| `solvers/quantum_dwave.py` | Actualiza el comentario sobre la expansión posterior, sin cambiar su algoritmo. |
| `domain/models.py` | Aclara el significado de los clientes internos, la distancia completa y el campo legado `internal_routes`. |
| `domain/spatial_partitioner.py` | Actualiza el comentario que explica para qué se conservan los miembros de cada grupo. |
| `api/main.py` | Hace opcional `X-Quantum-Timeout-Seconds` para activar el presupuesto automático cuando se omite. |
| `tests/run_local_evaluation.py` | Utiliza el presupuesto automático de Qiskit cuando no se fija un límite. |
| `README.md` | Documenta la expansión, el cálculo del coste y la configuración actual del servicio. |
| `INFORME_TECNICO_REDUCCION_QUBO_Y_QUBITS.md` | Distingue variables y bits, corrige los recuentos del QUBO y recoge las pruebas del simulador. |

## Comprobaciones y puntos para continuar

Han pasado **84 tests locales**. También se ha repetido una ejecución real del simulador con dos clientes directos, 10 qubits y semilla 7, obteniendo una ruta válida de distancia 22. No es una ejecución en QPU ni demuestra ventaja cuántica. No se ha probado una llamada remota a D-Wave.

Para la integración, la salida debe conservar los IDs y el orden de las rutas expandidas al convertirla al contrato de Delivera. La resolución cuántica sigue usando el DTO nativo y cabeceras. Queda pendiente completar su adaptación al contrato común, incluidos `parameters`, la matriz de distancias y `startDepotId`. El clásico y el fallback de este flujo siguen usando `pyvrp_solver.py`, no el PyVRP heterogéneo de `pyvrp_v2_solver.py`.
