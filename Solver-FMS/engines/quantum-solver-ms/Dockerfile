# Imagen del engine cuántico (solvers QISKIT y DWAVE de la pasarela). Se construye
# con el código de esta misma carpeta; docker-compose.yml la levanta con el perfil
# "quantum".
FROM python:3.14-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PYTHONPATH=/app \
    QUANTUM_MAX_QUBITS=24

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY api/ api/
COPY domain/ domain/
COPY services/ services/
COPY solvers/ solvers/
COPY utils/ utils/

RUN useradd --system --no-create-home quantum
USER quantum
EXPOSE 8095
# La imagen slim no trae wget ni curl, así que la sonda usa el propio Python.
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8095/health', timeout=4)" || exit 1
CMD ["uvicorn", "api.main:app", "--host", "0.0.0.0", "--port", "8095"]
