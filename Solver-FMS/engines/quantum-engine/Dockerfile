# syntax=docker/dockerfile:1
#
# Imagen del engine cuántico. El código no vive en Delivera: se toma del
# repositorio de la línea cuántica (cqvrp, carpeta quantum-solver-ms) a través
# del contexto de construcción con nombre "cqvrp", que define docker-compose.yml.
FROM python:3.14-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PYTHONPATH=/app \
    QUANTUM_MAX_QUBITS=24

WORKDIR /app
COPY --from=cqvrp requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY --from=cqvrp api/ api/
COPY --from=cqvrp domain/ domain/
COPY --from=cqvrp services/ services/
COPY --from=cqvrp solvers/ solvers/
COPY --from=cqvrp utils/ utils/

RUN useradd --system --no-create-home quantum
USER quantum
EXPOSE 8095
# La imagen slim no trae wget ni curl, así que la sonda usa el propio Python.
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8095/health', timeout=4)" || exit 1
CMD ["uvicorn", "api.main:app", "--host", "0.0.0.0", "--port", "8095"]
