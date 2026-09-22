"""
Medir: lanzar los solvers sobre las instancias y dejar constancia. Lo usa
compare_solvers.py.

  instance.py   Que es una instancia Cordeau, cuanto cuesta una solucion y si es valida
  gateway.py    Hablar con la pasarela: catalogo y resolucion
  runner.py     Lanzar, medir y convertir cada ejecucion en una fila
  dataset.py    Las dos tablas, su esquema y la agregacion de repeticiones
  report.py     El informe en Markdown

Sin dependencias externas: solo la libreria estandar. Es deliberado -medir no deberia
exigir un entorno de ciencia de datos- y por eso el analisis vive en el paquete de al
lado, que si las necesita.

El destinatario de la separacion es concreto: **un analisis posterior necesita
`instance.py` y nada mas**. Leer las caracteristicas de las 33 instancias no deberia
exigir que haya un gateway levantado ni arrastrar el generador de informes.

    from experimentation.comparison.instance import Instance, all_names

    filas = [Instance.load(nombre).features() for nombre in all_names()]
"""
