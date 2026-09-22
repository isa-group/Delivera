"""
Lo unico que este banco sabe de la pasarela: dos peticiones HTTP.

Se mide a traves de la API y no llamando a cada motor porque es el unico camino que
ejerce el mapeo real y trata a todos los solvers exactamente igual, que es la condicion
para que la comparacion signifique algo.
"""

import json
import time
import urllib.request


def catalog(base_url, timeout=10):
    """El descriptor dice que solvers hay, con que parametros corren y cuales son deterministas."""
    with urllib.request.urlopen(f"{base_url}/api/v1/fms/solvers", timeout=timeout) as response:
        return {s["type"]: s for s in json.load(response)["solvers"]}


def declared_defaults(info):
    """
    Los valores por defecto del descriptor, que son los que aplica la pasarela cuando el
    cliente no manda nada. Se registran en el CSV aunque no se envien: describen la
    ejecucion igual de bien, y sin ellos un resultado no se puede repetir mas adelante.
    """
    return {p["name"]: p["defaultValue"]
            for p in info.get("parameters") or []
            if p.get("defaultValue") is not None}


def solve(base_url, instance_name, solver, timeout, parameters):
    """Resuelve una instancia del banco y devuelve la respuesta y el tiempo de pared."""
    url = f"{base_url}/api/v1/fms/instances/send?fileName={instance_name}&solverType={solver}"
    request = urllib.request.Request(
        url, data=json.dumps(parameters).encode("utf-8"), method="POST",
        headers={"Content-Type": "application/json"})

    started = time.perf_counter()
    with urllib.request.urlopen(request, timeout=timeout) as response:
        body = json.load(response)
    return body, (time.perf_counter() - started) * 1000
