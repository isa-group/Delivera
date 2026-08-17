"""
  instance.py   Que es una instancia Cordeau, cuanto cuesta una solucion y si es valida
  gateway.py    Hablar con la pasarela: catalogo y resolucion
  dataset.py    El esquema del CSV y la agregacion de repeticiones
  report.py     El informe en Markdown
  runner.py     Lanzar, medir y convertir cada ejecucion en una fila

Sin dependencias externas: solo la libreria estandar.
"""

from pathlib import Path

# La raiz de Solver-FMS, de la que cuelgan las instancias, los BKS y los resultados.
BASE_DIR = Path(__file__).resolve().parent.parent
