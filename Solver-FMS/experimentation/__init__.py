"""
Lo que sostiene los dos scripts de experimentation, separado por para que sirve.

  comparison/  Medir: lanzar los solvers sobre las instancias y dejar el experimento
                por escrito. Es lo que usa compare_solvers.py.
  tree/        Analizar: aprender de esas mediciones que solver conviene segun la
                instancia. Es lo que usa decision_tree.py.

La division no es cosmetica. `comparison` habla con la pasarela por HTTP y funciona
solo con la libreria estandar; `tree` no habla con nadie -parte del CSV ya escrito- y
depende de scikit-learn. Mezclarlos obligaria a instalar scikit-learn para poder medir,
o a renunciar a el para poder analizar.

Aqui arriba solo queda lo que comparten: donde esta la raiz.
"""

from pathlib import Path

# La raiz de Solver-FMS, de la que cuelgan las instancias, los BKS y los resultados.
BASE_DIR = Path(__file__).resolve().parent.parent
