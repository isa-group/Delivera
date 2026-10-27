import json
import subprocess
import sys
import time
from typing import List

from domain.models import RoutingRequest, RoutingResponse, RouteDto


class QuantumExecutionTimeoutError(TimeoutError):
    """Indica que Qiskit no terminó dentro del límite de tiempo configurado."""


def solve_with_qiskit_with_timeout(
    sub_requests: List[RoutingRequest],
    max_qubits: int = 24,
    include_mtz: bool = False,
    timeout_sec: float = 30.0,
) -> RoutingResponse:
    """Ejecuta Qiskit con límite de tiempo real y termina el proceso si vence.

    QAOA y SciPy ejecutan código síncrono que no se puede cancelar de forma
    segura desde un hilo. Un proceso aislado permite detener una ejecución
    lenta y devolver el control a evaluation_service para activar PyVRP.
    """
    if timeout_sec <= 0:
        raise ValueError("El límite temporal de Qiskit debe ser mayor que cero.")

    # subprocess inicia un intérprete independiente. A diferencia de un hilo,
    # se puede terminar desde Windows aunque Qiskit o SciPy sigan calculando.
    # La entrada y la salida usan JSON por stdin/stdout, sin crear ficheros
    # temporales que puedan estar bloqueados por políticas de Windows.
    request_payload = json.dumps(
        {
            "sub_requests": [request.model_dump(mode="json") for request in sub_requests],
            "max_qubits": max_qubits,
            "include_mtz": include_mtz,
        }
    )
    process = subprocess.Popen(
        [sys.executable, "-m", "solvers.quantum_qiskit_proc_isolate"], # -m significa que se ejecuta como módulo, no como script,
                                                                 # para que los imports relativos funcionen
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE, # stederr se usa para capturar errores y mensajes de depuración del proceso hijo
        text=True,
    )
    try:
        stdout, stderr = process.communicate(
            input=request_payload,
            timeout=timeout_sec,
        )
    except subprocess.TimeoutExpired:
        process.kill()
        process.communicate() # sirve para limpiar los buffers de stdout y stderr del proceso hijo
        raise QuantumExecutionTimeoutError(
            f"Qiskit superó el límite de {timeout_sec:g} segundos."
        )

    if not stdout:
        detail = stderr.strip() or "sin detalle adicional"
        raise RuntimeError(
            "El proceso de Qiskit terminó sin devolver un resultado: "
            f"{detail}"
        )
    payload = json.loads(stdout) # cargamos el JSON devuelto por el proceso hijo a un diccionario de Python
    status = payload["status"]

    if status == "error":
        raise RuntimeError(payload["detail"])

    # Aseguramos que el resultado cumple con el modelo de datos esperado
    return RoutingResponse.model_validate(payload["result"])


def solve_with_qiskit(
    sub_requests: List[RoutingRequest],
    max_qubits: int = 24,
    include_mtz: bool = False,
) -> RoutingResponse:
    """Resuelve subproblemas CVRP mediante QAOA.

    MTZ es opcional porque puede aumentar mucho el QUBO. Si se desactiva,
    la decodificacion valida que no queden clientes en subtours desconectados.
    """
    # Estos imports solo se ejecutan en quantum_qiskit_proc_isolate. Así SciPy y
    # Qiskit no pueden bloquear el proceso principal antes del timeout.
    from qiskit.primitives import StatevectorSampler
    from qiskit_algorithms import QAOA
    from qiskit_algorithms.optimizers import COBYLA
    from qiskit_optimization import QuadraticProgram
    from qiskit_optimization.algorithms import MinimumEigenOptimizer
    from qiskit_optimization.converters import QuadraticProgramToQubo

    # El solver devuelve solo rutas globales entre el deposito y los
    # superclientes. Las rutas internas de cada supercliente se calculan
    # aparte con PyVRP y se agregan despues en evaluation_service.
    start_time = time.perf_counter()
    all_routes = []
    total_distance = 0.0
    largest_qubo = 0

    # StatevectorSampler es el simulador local compatible con QAOA en la
    # version actual de Qiskit; BasicSimulator no acepta la operacion QAOA sin
    # una transpilation adicional.
    sampler = StatevectorSampler()
    # COBYLA ajusta los parametros de QAOA. Reps=1 usa una sola capa para
    # mantener bajo el coste del experimento local.
    qaoa = QAOA(sampler=sampler, optimizer=COBYLA(maxiter=20), reps=1)
    optimizer = MinimumEigenOptimizer(qaoa)

    for request in sub_requests:
        if not request.customers or not request.depots or not request.vehicles:
            continue

        # Cada subrequest llega con un unico deposito porque el GAP ya separo
        # la instancia global en regiones independientes.
        depot = request.depots[0]
        customers = request.customers
        nodes = [depot] + customers
        vehicles = request.vehicles
        vehicle_ids = [vehicle.id for vehicle in vehicles]
        capacities = {vehicle.id: vehicle.capacity for vehicle in vehicles}
        demands = {customer.id: customer.demand for customer in customers}
        node_by_id = {node.id: node for node in nodes}

        # Variables del modelo:
        #   x_origen_destino_vehiculo = 1 si el vehiculo usa ese arco.
        #   y_cliente_vehiculo = 1 si el cliente se asigna a ese vehiculo.
        #   u_cliente_vehiculo = carga acumulada, solo con MTZ.
        # Qiskit convierte las variables enteras 'u' a varios bits binarios.
        problem = QuadraticProgram(name=f"QUBO_Cluster_{request.instance_name}")

        for vehicle_id in vehicle_ids:
            for origin in nodes:
                for destination in nodes:
                    if origin.id != destination.id:
                        problem.binary_var(
                            name=f"x_{origin.id}_{destination.id}_{vehicle_id}"
                        )

        for customer in customers:
            for vehicle_id in vehicle_ids:
                problem.binary_var(name=f"y_{customer.id}_{vehicle_id}")
                if include_mtz:
                    problem.integer_var(
                        name=f"u_{customer.id}_{vehicle_id}",
                        lowerbound=0,
                        upperbound=capacities[vehicle_id],
                    )

        # El objetivo minimiza la suma de distancias de todos los arcos usados.
        # Las distancias son euclideas redondeadas, igual que en el parser y
        # en PyVRP.
        problem.minimize(
            linear={
                f"x_{origin.id}_{destination.id}_{vehicle_id}": origin.euclidean_distance(destination)
                for vehicle_id in vehicle_ids
                for origin in nodes
                for destination in nodes
                if origin.id != destination.id
            }
        )

        # Cada cliente debe pertenecer exactamente a un vehiculo. Despues se
        # impone conservacion de flujo: si un vehiculo visita un cliente,
        # debe entrar y salir de el exactamente una vez.
        for customer in customers:
            problem.linear_constraint(
                linear={f"y_{customer.id}_{vehicle_id}": 1 for vehicle_id in vehicle_ids},
                sense="==",
                rhs=1,
                name=f"visit_{customer.id}",
            )
            for vehicle_id in vehicle_ids:
                incoming = {
                    f"x_{origin.id}_{customer.id}_{vehicle_id}": 1
                    for origin in nodes
                    if origin.id != customer.id
                }
                outgoing = {
                    f"x_{customer.id}_{destination.id}_{vehicle_id}": 1
                    for destination in nodes
                    if destination.id != customer.id
                }
                assignment = f"y_{customer.id}_{vehicle_id}"
                problem.linear_constraint(
                    linear={**incoming, assignment: -1},
                    sense="==",
                    rhs=0,
                    name=f"in_{customer.id}_{vehicle_id}",
                )
                problem.linear_constraint(
                    linear={**outgoing, assignment: -1},
                    sense="==",
                    rhs=0,
                    name=f"out_{customer.id}_{vehicle_id}",
                )
                if include_mtz:
                    # Estas dos restricciones atan la carga acumulada a la
                    # asignacion del cliente. Evitan valores de carga absurdos.
                    load = f"u_{customer.id}_{vehicle_id}"
                    problem.linear_constraint(
                        linear={load: 1, assignment: -demands[customer.id]},
                        sense=">=",
                        rhs=0,
                        name=f"cap_min_{customer.id}_{vehicle_id}",
                    )
                    problem.linear_constraint(
                        linear={load: 1, assignment: -capacities[vehicle_id]},
                        sense="<=",
                        rhs=0,
                        name=f"cap_max_{customer.id}_{vehicle_id}",
                    )

        # Sin MTZ, esta restriccion conserva la capacidad de cada vehiculo,
        # pero no elimina subtours; la decodificacion los rechaza despues.
        if not include_mtz:
            for vehicle_id in vehicle_ids:
                problem.linear_constraint(
                    linear={
                        f"y_{customer.id}_{vehicle_id}": demands[customer.id]
                        for customer in customers
                    },
                    sense="<=",
                    rhs=capacities[vehicle_id],
                    name=f"capacity_aggregate_{vehicle_id}",
                )

        # Cada vehiculo puede iniciar como maximo una ruta desde el deposito.
        # El balance obliga a regresar al mismo deposito. Sin MTZ aun pueden
        # existir ciclos de clientes desconectados del deposito.
        # Las restricciones de visita y flujo anteriores garantizan la visita
        # unica, mientras que MTZ, si se activa, elimina ciclos desconectados.
        for vehicle_id in vehicle_ids:
            depot_out = {
                f"x_{depot.id}_{customer.id}_{vehicle_id}": 1
                for customer in customers
            }
            depot_in = {
                f"x_{customer.id}_{depot.id}_{vehicle_id}": 1
                for customer in customers
            }
            problem.linear_constraint(
                linear={**depot_out, **{name: -value for name, value in depot_in.items()}},
                sense="==",
                rhs=0,
                name=f"depot_balance_{vehicle_id}",
            )
            problem.linear_constraint(
                linear=depot_out,
                sense="<=",
                rhs=1,
                name=f"depot_out_{vehicle_id}",
            )

            if include_mtz:
                # MTZ fuerza que el orden/carga avance al recorrer un arco de
                # cliente a cliente; por eso elimina subtours desconectados.
                for origin in customers:
                    for destination in customers:
                        if origin.id == destination.id:
                            continue
                        problem.linear_constraint(
                            linear={
                                f"u_{destination.id}_{vehicle_id}": 1,
                                f"u_{origin.id}_{vehicle_id}": -1,
                                f"x_{origin.id}_{destination.id}_{vehicle_id}": -capacities[vehicle_id],
                            },
                            sense=">=",
                            rhs=demands[destination.id] - capacities[vehicle_id],
                            name=f"mtz_{origin.id}_{destination.id}_{vehicle_id}",
                        )

        # QAOA trabaja sin restricciones: el convertidor incorpora las
        # penalizaciones y variables auxiliares en el QUBO final.
        # El numero de variables del QUBO es el numero real de qubits logicos
        # que necesitara este subproblema, incluidas variables auxiliares.
        qubo = QuadraticProgramToQubo().convert(problem)
        qubit_count = qubo.get_num_vars()
        largest_qubo = max(largest_qubo, qubit_count)
        if qubit_count > max_qubits:
            raise ValueError(
                f"El subproblema {request.instance_name} necesita {qubit_count} qubits; "
                f"el limite configurado es {max_qubits}."
            )

        # MinimumEigenOptimizer convierte el problema, ejecuta QAOA y devuelve
        # valores 0/1 para las variables originales del QuadraticProgram.
        result = optimizer.solve(problem)
        request_distance = 0.0

        # Reconstruir la ruta desde los arcos binarios permite validar que la
        # solucion cubre exactamente los clientes asignados a cada vehiculo.
        for vehicle_id in vehicle_ids:
            active_arcs = {
                (origin.id, destination.id): result.get_variable_value(
                    f"x_{origin.id}_{destination.id}_{vehicle_id}"
                )
                for origin in nodes
                for destination in nodes
                if origin.id != destination.id
            }
            if not any(
                active_arcs.get((depot.id, customer.id), 0) > 0.5
                for customer in customers
            ):
                continue

            # Seguir los arcos activos desde el deposito reconstruye una ruta
            # ordenada. Se detiene al volver al deposito.
            route = [depot.id]
            current = depot.id
            visited = {depot.id}
            while True:
                next_node = next(
                    (
                        node.id
                        for node in nodes
                        if node.id != current
                        and active_arcs.get((current, node.id), 0) > 0.5
                    ),
                    None,
                )
                if next_node is None or next_node == depot.id: # Si no hay siguiente nodo o volvemos al deposito, terminamos la ruta
                    route.append(depot.id)
                    break
                if next_node in visited:
                    raise ValueError(f"QAOA produjo un subtour para el vehiculo {vehicle_id}.")
                route.append(next_node)
                visited.add(next_node)
                current = next_node

            # La reconstruccion empieza en el deposito y solo sigue los arcos
            # activos que son alcanzables desde el. Por eso route[1:-1]
            # contiene los clientes de la ruta conectada, sin los depositos.
            # Si las variables de arcos 'x' y de asignacion 'y' permiten un ciclo aislado
            # entre clientes, ese ciclo no aparece en route aunque sus clientes tengan y=1. 
            # La comparacion detecta esa incoherencia y rechaza la solucion, no sustituye a
            # MTZ como prueba formal de eliminacion de subtours.
            assigned_customers = {
                customer.id
                for customer in customers
                if result.get_variable_value(f"y_{customer.id}_{vehicle_id}") > 0.5
            }
            if set(route[1:-1]) != assigned_customers: # el slicing route[1:-1] excluye los depositos inicial y final, 
                                                       # dejando solo los clientes visitados
                raise ValueError(
                    f"QAOA produjo un subtour desconectado para el vehiculo {vehicle_id}."
                )

            # La distancia final se recalcula sobre la ruta decodificada para
            # que coincida con lo que se devuelve en RouteDto.
            route_distance = sum(
                node_by_id[origin].euclidean_distance(node_by_id[destination])
                for origin, destination in zip(route, route[1:])
            )
            route_load = sum(
                demands[customer.id]
                for customer in customers
                if result.get_variable_value(f"y_{customer.id}_{vehicle_id}") > 0.5 # y[cliente.id, k] nos dice si el cliente fue asignado al vehículo k
            )

            # Acumulamos la distancia total de la subrequest y guardamos la ruta reconstruida.
            request_distance += route_distance
            all_routes.append(
                RouteDto(
                    vehicle_id=vehicle_id,
                    stops=route,
                    distance=float(route_distance),
                    load=route_load,
                )
            )

        # Acumulamos la distancia total de todas las subrequests para el informe final.
        total_distance += request_distance

    return RoutingResponse(
        instance_name="Delivera_Quantum_Qiskit_Global",
        solver_used="IBM Quantum / BSC Simulator",
        execution_time_ms=round((time.perf_counter() - start_time) * 1000, 3),
        total_distance=total_distance,
        is_feasible=True,
        routes=all_routes,
        qubits=largest_qubo,
    )
