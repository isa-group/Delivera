"""Proceso auxiliar para aislar una ejecución local de Qiskit."""

import json
import sys

from domain.models import RoutingRequest
from solvers.quantum_qiskit import solve_with_qiskit


def main() -> int:
    """Lee JSON por stdin, ejecuta Qiskit y escribe JSON por stdout."""
    request_payload = json.load(sys.stdin)
    sub_requests = [
        RoutingRequest.model_validate(item)
        for item in request_payload["sub_requests"]
    ]

    try:
        result = solve_with_qiskit(
            sub_requests,
            max_qubits=request_payload["max_qubits"],
            include_mtz=request_payload["include_mtz"],
        )
        payload = {"status": "ok", "result": result.model_dump(mode="json")}
    except Exception as error:
        # El padre recibirá una descripción segura incluso si la excepción de
        # Qiskit no puede serializarse a JSON.
        payload = {"status": "error", "detail": f"{type(error).__name__}: {error}"}

    json.dump(payload, sys.stdout) # escribimos el resultado en stdout para que el proceso padre lo lea
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
