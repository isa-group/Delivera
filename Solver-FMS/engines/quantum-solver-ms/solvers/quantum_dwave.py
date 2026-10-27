# solvers/quantum_dwave.py

import time
from typing import List
from dimod import ConstrainedQuadraticModel, Binary, Integer
from dwave.system import LeapHybridCQMSampler

from domain.models import RoutingRequest, RoutingResponse, RouteDto

def solve_with_dwave(sub_requests: List[RoutingRequest]) -> RoutingResponse:
    """
    Traductor CQM para D-Wave. Modela el MD-CVRP reducido en variables continuas/enteras e inecuaciones.
    Itera sobre los sub_requests generados por el particionador espacial.
    """
    # D-Wave resuelve cada subproblema como un CQM, conservando restricciones
    # enteras y desigualdades sin convertirlas manualmente a un QUBO.
    t0 = time.perf_counter()
    all_routes = []
    total_global_distance = 0.0

    # Conexión a la nube cuántica de D-Wave
    try:
        sampler = LeapHybridCQMSampler()
    except Exception as e:
        raise Exception(f"Fallo en la comunicación con Leap Hybrid Solver: {str(e)}")

    for request in sub_requests:
        if not request.customers:
            continue

        # 1. Extraer el deposito, clientes, vehiculos y matriz de distancias.
        depot = request.depots[0]
        clientes = request.customers
        nodos = [depot] + clientes
        
        vehiculos = [vehiculo.id for vehiculo in request.vehicles]
        Q = {vehiculo.id: vehiculo.capacity for vehiculo in request.vehicles}

        q = {c.id: c.demand for c in clientes}
        c = {u.id: {v.id: u.euclidean_distance(v) for v in nodos} for u in nodos}

        # 2. Variables binarias de arcos/asignaciones y cargas enteras.
        cqm = ConstrainedQuadraticModel()

        # Variables binarias de ruta (x_u_v_k)
        x = {(u.id, v.id, k): Binary(f'x_{u.id}_{v.id}_{k}') for u in nodos for v in nodos for k in vehiculos if u.id != v.id}
        
        # Variables binarias de asignación (y_i_k)
        y = {(i.id, k): Binary(f'y_{i.id}_{k}') for i in clientes for k in vehiculos}
        
        # Variables enteras de carga acumulada
        u_load = {(i.id, k): Integer(f'u_{i.id}_{k}', lower_bound=0, upper_bound=Q[k]) for i in clientes for k in vehiculos}

        # 3. Objetivo: minimizar la distancia de las rutas globales.
        cqm.set_objective(sum(c[u][v] * x[u, v, k] for u, v, k in x))

        # 4. Restricciones de visita, flujo, capacidad y eliminacion de
        # subtours mediante MTZ.
        # A. Visita única por cliente
        for i in clientes:
            cqm.add_constraint(sum(y[i.id, k] for k in vehiculos) == 1, label=f'visita_unica_{i.id}')

        # B. Conservación de flujo
        for i in clientes:
            for k in vehiculos:
                cqm.add_constraint(sum(x[u.id, i.id, k] for u in nodos if u.id != i.id) == y[i.id, k], label=f'entra_{i.id}_{k}')
                cqm.add_constraint(sum(x[i.id, v.id, k] for v in nodos if v.id != i.id) == y[i.id, k], label=f'sale_{i.id}_{k}')

        # C. Equilibrio y salida obligatoria desde el depósito
        for k in vehiculos:
            cqm.add_constraint(
                sum(x[depot.id, j.id, k] for j in clientes) == sum(x[j.id, depot.id, k] for j in clientes),
                label=f'equilibrio_depot_{k}'
            )
            # Un vehículo puede no utilizarse si los demás cubren todos los clientes.
            cqm.add_constraint(sum(x[depot.id, j.id, k] for j in clientes) <= 1, label=f'sale_depot_{k}')

        # D. Límites lógicos de capacidad
        for i in clientes:
            for k in vehiculos:
                cqm.add_constraint(u_load[i.id, k] >= q[i.id] * y[i.id, k], label=f'cap_min_{i.id}_{k}')
                cqm.add_constraint(u_load[i.id, k] <= Q[k] * y[i.id, k], label=f'cap_max_{i.id}_{k}')

        # E. Eliminación de Subtours (MTZ)
        for i in clientes:
            for j in clientes:
                if i.id != j.id:
                    for k in vehiculos:
                        if (i.id, j.id, k) in x:
                            cqm.add_constraint(
                                u_load[j.id, k] >= u_load[i.id, k] + q[j.id] - Q[k] * (1 - x[i.id, j.id, k]),
                                label=f'mtz_{i.id}_{j.id}_{k}'
                            )

        # 5. El sampler hibrido combina recursos clasicos y cuanticos.
        sampleset = sampler.sample_cqm(cqm, label=f"Delivera_Cluster_{request.instance_name}")
        
        # 6. Decodificar la mejor muestra factible en rutas legibles.
        feasible_sampleset = sampleset.filter(lambda row: row.is_feasible)
        
        if len(feasible_sampleset) == 0:
            raise Exception("El procesador cuántico no encontró ninguna ruta válida (infeasible) para esta capacidad.")
            
        # Las soluciones factibles se ordenan por menor energía; .first selecciona la mejor encontrada.
        best_sample = feasible_sampleset.first.sample

        # Se reconstruye una ruta independiente para cada vehículo utilizado.
        request_distance = 0.0
        for k in vehiculos:
            rutas_activas = {
                (u.id, v.id): best_sample[x[u.id, v.id, k].variables[0]] # .variables[0] accede al nombre de la variable en el sample
                for u in nodos for v in nodos if u.id != v.id
            }
            # > 0.5 se usa porque el solver cuantico puede devolver valores con ruido, no exactamente 0 o 1.
            if not any(rutas_activas.get((depot.id, cliente.id), 0) > 0.5 for cliente in clientes):
                continue

            ordered_route = [depot.id]
            nodo_actual = depot.id
            visitados = {depot.id}

            # Reconstrucción de la ruta siguiendo los arcos activos desde el depósito hasta volver al depósito.
            while True:
                siguiente_nodo = next(
                    (v.id for v in nodos
                     if v.id != nodo_actual and rutas_activas.get((nodo_actual, v.id), 0) > 0.5),
                    None
                )
                if not siguiente_nodo or siguiente_nodo == depot.id:
                    ordered_route.append(depot.id)
                    break
                if siguiente_nodo in visitados:
                    raise Exception(f"La solución de D-Wave contiene un ciclo inválido para el vehículo {k}.")

                ordered_route.append(siguiente_nodo)
                visitados.add(siguiente_nodo)
                nodo_actual = siguiente_nodo

            # Calcular la distancia y carga de la ruta reconstruida
            route_distance = sum(
                c[u][v] for u, v in zip(ordered_route, ordered_route[1:])
            )
            route_load = sum(
                q[cliente.id]
                for cliente in clientes
                if best_sample[y[cliente.id, k].variables[0]] > 0.5 # y[cliente.id, k] nos dice si el cliente fue asignado al vehículo k
            )
            request_distance += route_distance
            all_routes.append(RouteDto(
                vehicle_id=k,
                stops=ordered_route,
                distance=float(route_distance),
                load=route_load
            ))

        total_global_distance += request_distance

    execution_time_ms = round((time.perf_counter() - t0) * 1000, 3) # Tiempo total de ejecución en milisegundos

    # Las rutas internas de los superclientes se agregan en evaluation_service.
    return RoutingResponse(
        instance_name="Delivera_Quantum_DWave_Global",
        solver_used="D-Wave Leap Hybrid Solver",
        execution_time_ms=execution_time_ms,
        total_distance=total_global_distance,
        is_feasible=True,
        routes=all_routes
    )
